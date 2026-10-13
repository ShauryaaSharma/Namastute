package com.namastute.users;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// PATCH /api/v1/me through HTTP and Spring Security. jwt() stands in for a real login: "this request carries a valid token for user X".
@SpringBootTest
@AutoConfigureMockMvc
class ProfileTest {

    @Autowired MockMvc mvc;
    @Autowired UserService users;

    UUID userId;

    @BeforeEach
    void freshUser() {
        // A new random number per test, so tests never see each other's changes
        userId = users.findOrCreateByPhone("+9190" + String.format("%08d", (int) (Math.random() * 100_000_000)));
    }

    @Test
    void newUserHasNoNameOrUpiId() throws Exception {
        mvc.perform(get("/api/v1/me").with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.upiId").doesNotExist());
    }

    @Test
    void setsNameAndUpiIdLowercased() throws Exception {
        patchMe("{\"name\":\"Keshav Dixit\",\"upiId\":\"Keshav.D@OkAxis\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Keshav Dixit"))
                .andExpect(jsonPath("$.upiId").value("keshav.d@okaxis"));
    }

    @Test
    void leftOutFieldIsUnchangedAndEmptyUpiIdRemovesIt() throws Exception {
        patchMe("{\"name\":\"Asha\",\"upiId\":\"asha@ybl\"}").andExpect(status().isOk());

        patchMe("{\"name\":\"Asha Rao\"}")
                .andExpect(jsonPath("$.name").value("Asha Rao"))
                .andExpect(jsonPath("$.upiId").value("asha@ybl"));
        patchMe("{\"upiId\":\"\"}")
                .andExpect(jsonPath("$.name").value("Asha Rao"))
                .andExpect(jsonPath("$.upiId").doesNotExist());
    }

    @Test
    void acceptsNamesInAnyScript() throws Exception {
        patchMe("{\"name\":\"केशव दीक्षित\"}").andExpect(status().isOk());   // Devanagari vowel signs are marks, not letters
        patchMe("{\"name\":\"கேசவ்\"}").andExpect(status().isOk());          // Tamil
        patchMe("{\"name\":\"অমিত\"}").andExpect(status().isOk());            // Bengali
        patchMe("{\"name\":\"D'Souza-Pinto Jr.\"}").andExpect(status().isOk());
    }

    @Test
    void rejectsBadInput() throws Exception {
        for (String body : new String[] {
                "{\"name\":\"K\"}",                    // too short
                "{\"name\":\" Keshav\"}",              // leading space
                "{\"name\":\"<script>\"}",             // not a name
                "{\"upiId\":\"keshav\"}",              // no @bank
                "{\"upiId\":\"keshav@123\"}"}) {       // bank handle must be letters
            patchMe(body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void needsLogin() throws Exception {
        mvc.perform(patch("/api/v1/me").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nobody\"}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions patchMe(String json) throws Exception {
        return mvc.perform(patch("/api/v1/me")
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
