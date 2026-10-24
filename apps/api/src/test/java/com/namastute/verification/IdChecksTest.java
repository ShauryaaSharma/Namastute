package com.namastute.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.namastute.users.UserService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

// The automatic first pass (Learning Notes 82), end to end: a submitted check → saved suggestions, or an automatic
// rejection when there's no face. The face model is a stand-in with fixed answers (no real person's face in the repo);
// Tesseract is real. Each test runs the checks on its own check only.
@SpringBootTest
@AutoConfigureMockMvc
class IdChecksTest {

    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PhotoStore photos;
    @Autowired IdChecks checks;
    @MockitoBean FaceMatch faces;

    private UUID admin;

    @AfterEach
    void demoteTestAdmin() {
        if (admin != null) jdbc.update("update users set role = 'user' where id = ?", admin);
    }

    @Test
    void savesTheScoreAndWhatTheIdSaysForTheReviewer() throws Exception {
        when(faces.compare(any(), any())).thenReturn(new FaceMatch.Result(true, true, 0.71));
        UUID asha = user();
        String check = submit(asha, MachineChecksTest.card("GOVERNMENT OF INDIA", "Gender: FEMALE", "XXXX XXXX 4821"));

        checks.check(UUID.fromString(check));

        Map<String, Object> row = jdbc.queryForMap(
                "select status::text, face_similarity, suggested_gender, suggested_last4, checked_at from verifications where id = ?::uuid", check);
        assertThat(row).containsEntry("status", "pending")   // a person still decides
                .containsEntry("suggested_gender", "female").containsEntry("suggested_last4", "4821");
        assertThat(((Number) row.get("face_similarity")).doubleValue()).isEqualTo(0.71, org.assertj.core.data.Offset.offset(0.001));
        assertThat(row.get("checked_at")).isNotNull();

        admin = user();
        jdbc.update("update users set role = 'admin' where id = ?", admin);
        mvc.perform(get("/api/v1/admin/verifications").with(as(admin)))
                .andExpect(jsonPath("$[?(@.id == '" + check + "')].checked").value(contains(true)))
                .andExpect(jsonPath("$[?(@.id == '" + check + "')].suggestedGender").value(contains("female")))
                .andExpect(jsonPath("$[?(@.id == '" + check + "')].suggestedLast4").value(contains("4821")));

        checks.check(UUID.fromString(check));   // already checked: nothing runs twice
        assertThat(jdbc.queryForObject("select checked_at from verifications where id = ?::uuid", Object.class, check))
                .isEqualTo(row.get("checked_at"));
    }

    @Test
    void noFaceOnTheIdIsRejectedAutomaticallyWithAClearReason() throws Exception {
        when(faces.compare(any(), any())).thenReturn(new FaceMatch.Result(false, true, null));
        UUID ravi = user();
        String check = submit(ravi, MachineChecksTest.card("GOVERNMENT OF INDIA"));
        Map<String, Object> keys = jdbc.queryForMap("select doc_key, selfie_key from verifications where id = ?::uuid", check);

        checks.check(UUID.fromString(check));

        mvc.perform(get("/api/v1/verification").with(as(ravi)))
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.rejectReason").value(IdChecks.NO_FACE_ON_ID))
                .andExpect(jsonPath("$.gesture").isNotEmpty());   // free to try again
        for (Object key : keys.values()) {
            assertThatThrownBy(() -> photos.get((String) key)).isInstanceOf(NoSuchKeyException.class);
        }
    }

    @Test
    void noFaceInTheSelfieSaysSo() throws Exception {
        when(faces.compare(any(), any())).thenReturn(new FaceMatch.Result(true, false, null));
        UUID meera = user();
        String check = submit(meera, MachineChecksTest.card("GOVERNMENT OF INDIA"));
        checks.check(UUID.fromString(check));
        mvc.perform(get("/api/v1/verification").with(as(meera))).andExpect(jsonPath("$.rejectReason").value(IdChecks.NO_FACE_IN_SELFIE));
    }

    private String submit(UUID user, byte[] idPhoto) throws Exception {
        mvc.perform(get("/api/v1/verification").with(as(user)));   // get a gesture
        mvc.perform(multipart("/api/v1/verification")
                        .file(new MockMultipartFile("idPhoto", "id.jpg", "image/jpeg", idPhoto))
                        .file(new MockMultipartFile("selfie", "selfie.jpg", "image/jpeg", MachineChecksTest.card("SELFIE", "", "", "")))
                        .param("docType", "masked_aadhaar").param("consent", "true").with(as(user)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("select id::text from verifications where user_id = ? and status = 'pending'", String.class, user);
    }

    private UUID user() {
        UUID id = users.findOrCreateByPhone("+9195" + String.format("%08d", (int) (Math.random() * 100_000_000)));
        jdbc.update("update users set name = 'Test' where id = ?", id);
        return id;
    }

    private static RequestPostProcessor as(UUID user) {
        return jwt().jwt(j -> j.subject(user.toString()));
    }
}
