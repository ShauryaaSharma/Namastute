package com.namastute.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.namastute.users.UserService;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

    // ---- helpers ----

    private ResultActions submit(UUID user, byte[] idPhoto, byte[] selfie) throws Exception {
        return mvc.perform(multipart("/api/v1/verification").file(part("idPhoto", idPhoto)).file(part("selfie", selfie))
                .param("docType", "passport").param("consent", "true").with(as(user)));
    }

    private String gesture(UUID user) throws Exception {
        String body = mvc.perform(get("/api/v1/verification").with(as(user))).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.gesture");
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
