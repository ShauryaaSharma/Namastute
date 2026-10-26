package com.namastute.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Logging in by email (Learning Notes 88), through HTTP like the web app. A new address every test, so no two runs
// share an account or an OTP limit.
@SpringBootTest
@AutoConfigureMockMvc
@Import({CapturingSms.Config.class, CapturingEmail.Config.class})   // the same set in every login test: they share one Spring context
class EmailLoginTest {

    @Autowired MockMvc mvc;
    @Autowired StringRedisTemplate redis;
    @Autowired CapturingEmail inbox;

    final String email = "asha." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

    @BeforeEach
    void resetIpLimit() {
        redis.delete(List.of("rate:otp:ip:127.0.0.1"));   // MockMvc requests come from 127.0.0.1
    }

    @Test
    void emailLoginMakesAnAccountWithNoPhone() throws Exception {
        String token = login(email);

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    @Test
    void capitalsReachTheSameAccount() throws Exception {
        String first = userId(login(email));
        redis.delete("otp:cooldown:" + email);   // skip the 30 s wait between logins

        assertThat(userId(login(email.toUpperCase()))).isEqualTo(first);
        assertThat(inbox.lastTo()).as("the code goes to the lower-case address").isEqualTo(email);
    }

    @Test
    void exactlyOneOfPhoneOrEmail() throws Exception {
        otp("{}").andExpect(status().isBadRequest());
        otp("{\"phone\":\"+919000000099\",\"email\":\"" + email + "\"}").andExpect(status().isBadRequest());
        otp("{\"email\":\"not-an-address\"}").andExpect(status().isBadRequest());
    }

    @Test
    void aFailedSendCanBeRetriedAtOnce() throws Exception {
        inbox.failNext = true;
        otp("{\"email\":\"" + email + "\"}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("CODE_NOT_SENT"));

        otp("{\"email\":\"" + email + "\"}").andExpect(status().isNoContent());   // no 30 s cooldown: nothing was sent
    }

    private ResultActions otp(String json) throws Exception {
        return mvc.perform(post("/api/v1/auth/otp").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // The whole login: ask for a code, read it from the inbox, send it back; returns the access token
    private String login(String address) throws Exception {
        otp("{\"email\":\"" + address + "\"}").andExpect(status().isNoContent());
        String body = mvc.perform(post("/api/v1/auth/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + address + "\",\"code\":\"" + inbox.lastCode() + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String userId(String token) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(), "$.id");
    }
}
