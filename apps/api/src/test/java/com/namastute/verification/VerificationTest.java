package com.namastute.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.namastute.users.UserService;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

// ID verification uploads (Learning Notes 78), against real S3-compatible storage (SeaweedFS from docker-compose / CI).
// Fresh users every test.
@SpringBootTest
@AutoConfigureMockMvc
class VerificationTest {

    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PhotoStore photos;
    @Autowired VerificationService verification;

    private final List<UUID> admins = new ArrayList<>();

    // Test admins go back to being ordinary users: they mustn't pile up as real admins in the dev database
    @AfterEach
    void demoteTestAdmins() {
        admins.forEach(id -> jdbc.update("update users set role = 'user' where id = ?", id));
    }

    @Test
    void submitsStoresCleanJpegsAndShowsUnderReview() throws Exception {
        UUID neha = user();
        mvc.perform(get("/api/v1/verification").with(as(neha)))
                .andExpect(jsonPath("$.status").value("unverified"))
                .andExpect(jsonPath("$.gesture").isNotEmpty());
        String gesture = gesture(neha);

        submit(neha, jpegWithGps(), jpegWithGps())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.docType").value("passport"))
                .andExpect(jsonPath("$.gesture").doesNotExist());   // nothing to photograph while a check waits

        Map<String, Object> row = jdbc.queryForMap(
                "select doc_key, selfie_key, gesture, consented_at from verifications where user_id = ?", neha);
        assertThat(row.get("gesture")).isEqualTo(gesture);
        assertThat(row.get("consented_at")).isNotNull();
        for (String key : new String[] {(String) row.get("doc_key"), (String) row.get("selfie_key")}) {
            byte[] stored = photos.get(key);
            assertThat(stored[0] & 0xff).as("a JPEG").isEqualTo(0xFF);
            assertThat(stored[1] & 0xff).isEqualTo(0xD8);
            assertThat(new String(stored, StandardCharsets.ISO_8859_1)).as("the hidden GPS data is gone")
                    .doesNotContain("Exif").doesNotContain("GPS-28.5562,77.0870");
        }
    }

    @Test
    void refusesWhatIsntAUsablePhoto() throws Exception {
        UUID ravi = user();
        gesture(ravi);
        submit(ravi, "not a photo".getBytes(), jpeg(800, 600))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("NOT_AN_IMAGE"));
        submit(ravi, jpeg(320, 240), jpeg(800, 600))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("PHOTO_TOO_SMALL"));
        // A small file claiming a huge picture is refused from its header, before anything is decoded
        submit(ravi, png(7000, 500), jpeg(800, 600))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("PHOTO_TOO_LARGE"));
        mvc.perform(multipart("/api/v1/verification").file(part("idPhoto", jpeg(800, 600)))
                        .param("docType", "passport").param("consent", "true").with(as(ravi)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("PHOTO_MISSING"));
        assertThat(jdbc.queryForObject("select count(*) from verifications where user_id = ?", Integer.class, ravi))
                .as("nothing saved").isZero();
    }

    @Test
    void needsConsentAKnownDocumentAndAFreshGesture() throws Exception {
        UUID asha = user();
        gesture(asha);
        mvc.perform(multipart("/api/v1/verification").file(part("idPhoto", jpeg(800, 600))).file(part("selfie", jpeg(800, 600)))
                        .param("docType", "passport").with(as(asha)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
        mvc.perform(multipart("/api/v1/verification").file(part("idPhoto", jpeg(800, 600))).file(part("selfie", jpeg(800, 600)))
                        .param("docType", "aadhaar").param("consent", "true").with(as(asha)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("DOC_TYPE_INVALID"));

        UUID noGesture = user();   // never opened the page, so was never told a gesture
        submit(noGesture, jpeg(800, 600), jpeg(800, 600))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("GESTURE_EXPIRED"));
    }

    @Test
    void onlyOneCheckAtATime() throws Exception {
        UUID meera = user();
        gesture(meera);
        submit(meera, jpeg(800, 600), jpeg(800, 600)).andExpect(status().isCreated());
        submit(meera, jpeg(800, 600), jpeg(800, 600))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ALREADY_PENDING"));
    }

    @Test
    void photosAreDeletedAfterSevenDaysWhateverHappened() throws Exception {
        UUID kavya = user();
        gesture(kavya);
        submit(kavya, jpeg(800, 600), jpeg(800, 600)).andExpect(status().isCreated());
        Map<String, Object> row = jdbc.queryForMap("select doc_key, selfie_key from verifications where user_id = ?", kavya);
        jdbc.update("update verifications set created_at = now() - interval '8 days' where user_id = ?", kavya);

        assertThat(verification.purgeOld()).isGreaterThanOrEqualTo(1);

        for (Object key : row.values()) {
            assertThatThrownBy(() -> photos.get((String) key)).isInstanceOf(NoSuchKeyException.class);
        }
        mvc.perform(get("/api/v1/verification").with(as(kavya)))
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.rejectReason").value("Not reviewed within 7 days, so your photos were deleted. Please try again."))
                .andExpect(jsonPath("$.gesture").isNotEmpty());   // free to try again
        assertThat(jdbc.queryForMap("select doc_key, selfie_key from verifications where user_id = ?", kavya).values())
                .containsOnlyNulls();
    }

    // ---- The review (Learning Notes 80) ----

    @Test
    void onlyAdminsCanReviewAndNobodyReviewsTheirOwn() throws Exception {
        UUID priya = user(), admin = admin(), stranger = user();
        String check = submitted(priya);

        // A non-admin learns nothing, not even that the admin area exists
        mvc.perform(get("/api/v1/admin/verifications").with(as(stranger))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/verifications/" + check + "/photos/id").with(as(stranger))).andExpect(status().isNotFound());
        approve(stranger, check).andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/admin/verifications").with(as(admin)))
                .andExpect(jsonPath("$[?(@.id == '" + check + "')].firstName").value(contains("Test")));
        byte[] photo = mvc.perform(get("/api/v1/admin/verifications/" + check + "/photos/selfie").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))   // the browser keeps no copy
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(photo[0] & 0xff).isEqualTo(0xFF);
        mvc.perform(get("/api/v1/admin/verifications/" + check + "/photos/passport").with(as(admin))).andExpect(status().isNotFound());

        String own = submitted(admin);
        approve(admin, own).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ALREADY_DECIDED"));
    }

    @Test
    void approvingVerifiesThePersonDeletesThePhotosAndShowsTheBadge() throws Exception {
        UUID priya = user(), admin = admin(), ravi = user();
        String check = submitted(priya);
        Map<String, Object> keys = jdbc.queryForMap("select doc_key, selfie_key from verifications where id = ?::uuid", check);

        approve(admin, check).andExpect(status().isNoContent());
        approve(admin, check).andExpect(status().isConflict());   // a double click: still one decision

        assertThat(jdbc.queryForMap("select verification::text, gender::text from users where id = ?", priya))
                .containsEntry("verification", "verified").containsEntry("gender", "female");
        Map<String, Object> row = jdbc.queryForMap(
                "select status::text, doc_last4, reviewed_by, doc_key, selfie_key from verifications where id = ?::uuid", check);
        assertThat(row).containsEntry("status", "verified").containsEntry("doc_last4", "AB12").containsEntry("reviewed_by", admin);
        assertThat(row.get("doc_key")).isNull();
        for (Object key : keys.values()) {
            assertThatThrownBy(() -> photos.get((String) key)).isInstanceOf(NoSuchKeyException.class);
        }
        mvc.perform(get("/api/v1/verification").with(as(priya))).andExpect(jsonPath("$.status").value("verified"));

        // The badge: Ravi sees "ID verified" next to Priya in his matches, and Priya sees no badge on Ravi
        String priyaTrip = trip(priya), raviTrip = trip(ravi);
        mvc.perform(get("/api/v1/trips/" + raviTrip + "/matches").with(as(ravi)))
                .andExpect(jsonPath("$[?(@.riders[0].tripId == '" + priyaTrip + "')].riders[0].rating.idVerified").value(contains(true)));
        mvc.perform(get("/api/v1/trips/" + priyaTrip + "/matches").with(as(priya)))
                .andExpect(jsonPath("$[?(@.riders[0].tripId == '" + raviTrip + "')].riders[0].rating.idVerified").value(contains(false)));
        // Only our own two trips: leftovers would crowd this slot's top 5 on the next run
        mvc.perform(delete("/api/v1/trips/" + priyaTrip).with(as(priya))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/trips/" + raviTrip).with(as(ravi))).andExpect(status().isNoContent());
    }

    @Test
    void rejectingGivesTheReasonAndAllowsATryAgain() throws Exception {
        UUID kiran = user(), admin = admin();
        String check = submitted(kiran);
        mvc.perform(post("/api/v1/admin/verifications/" + check + "/reject").with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Photo blurry or unreadable\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/verification").with(as(kiran)))
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.rejectReason").value("Photo blurry or unreadable"))
                .andExpect(jsonPath("$.gesture").isNotEmpty());
        assertThat(jdbc.queryForObject("select verification::text from users where id = ?", String.class, kiran)).isEqualTo("unverified");
        assertThat(jdbc.queryForObject("select doc_key from verifications where id = ?::uuid", String.class, check)).isNull();
    }

    @Test
    void thePurgeFinishesADecidedCheckWhosePhotosWereLeftBehind() throws Exception {
        UUID meera = user(), admin = admin();
        String check = submitted(meera);
        approve(admin, check).andExpect(status().isNoContent());
        // As if S3 had been down at decision time: the photo is still stored and its key still on the row
        String left = "verification/left-" + check + ".jpg";
        photos.put(left, jpeg(800, 600));
        jdbc.update("update verifications set doc_key = ? where id = ?::uuid", left, check);

        verification.purgeOld();

        assertThatThrownBy(() -> photos.get(left)).isInstanceOf(NoSuchKeyException.class);
        assertThat(jdbc.queryForMap("select status::text, doc_key from verifications where id = ?::uuid", check))
                .containsEntry("status", "verified").containsEntry("doc_key", null);   // still approved: only the photo went
    }

    // ---- helpers ----

    private String submitted(UUID user) throws Exception {
        gesture(user);
        submit(user, jpeg(800, 600), jpeg(800, 600)).andExpect(status().isCreated());
        return jdbc.queryForObject("select id::text from verifications where user_id = ? and status = 'pending'", String.class, user);
    }

    private ResultActions approve(UUID admin, String check) throws Exception {
        return mvc.perform(post("/api/v1/admin/verifications/" + check + "/approve").with(as(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"gender\":\"female\",\"docLast4\":\"ab12\"}"));
    }

    private UUID admin() {
        UUID id = user();
        jdbc.update("update users set role = 'admin' where id = ?", id);
        admins.add(id);
        return id;
    }

    // Terminal 3, ten hours ahead: a slot no other test uses, so their trips can't crowd the top 5 matches
    private String trip(UUID user) throws Exception {
        String json = """
                {"terminalId":"DEL-T3","exitAt":"%s","destLat":28.4936,"destLng":77.0930,"destArea":"DLF Phase 3, Gurugram",\
                "seats":1,"bags":1,"womenOnly":false}""".formatted(Instant.now().plus(Duration.ofHours(10)));
        return JsonPath.read(mvc.perform(post("/api/v1/trips").with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }


    private ResultActions submit(UUID user, byte[] idPhoto, byte[] selfie) throws Exception {
        return mvc.perform(multipart("/api/v1/verification").file(part("idPhoto", idPhoto)).file(part("selfie", selfie))
                .param("docType", "passport").param("consent", "true").with(as(user)));
    }

    private String gesture(UUID user) throws Exception {
        String body = mvc.perform(get("/api/v1/verification").with(as(user))).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.gesture");
    }

    private static MockMultipartFile part(String name, byte[] bytes) {
        return new MockMultipartFile(name, name + ".jpg", "image/jpeg", bytes);
    }

    private static byte[] jpeg(int width, int height) throws Exception {
        return encode(width, height, "jpeg");
    }

    private static byte[] png(int width, int height) throws Exception {
        return encode(width, height, "png");
    }

    private static byte[] encode(int width, int height, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, width / 2, height / 2);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    // A JPEG with an EXIF block (APP1, "Exif\0\0") carrying a fake GPS location, like a phone photo
    private static byte[] jpegWithGps() throws Exception {
        byte[] plain = jpeg(800, 600);
        byte[] payload = "Exif\0\0GPS-28.5562,77.0870".getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(plain, 0, 2);                                     // FF D8: start of image
        out.write(new byte[] {(byte) 0xFF, (byte) 0xE1});           // APP1 marker
        int length = payload.length + 2;
        out.write(new byte[] {(byte) (length >> 8), (byte) length});
        out.write(payload);
        out.write(plain, 2, plain.length - 2);
        return out.toByteArray();
    }

    private UUID user() {
        UUID id = users.findOrCreateByPhone("+9196" + String.format("%08d", (int) (Math.random() * 100_000_000)));
        jdbc.update("update users set name = 'Test' where id = ?", id);
        return id;
    }

    private static RequestPostProcessor as(UUID user) {
        return jwt().jwt(j -> j.subject(user.toString()));
    }
}
