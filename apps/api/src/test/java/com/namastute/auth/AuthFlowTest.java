package com.namastute.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

// End-to-end through HTTP and Spring Security: OTP login → tokens → protected endpoint → refresh → logout
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingSms.Config.class)
class AuthFlowTest {

    static final String PHONE = "+919000000002";

    @Autowired MockMvc mvc;
    @Autowired StringRedisTemplate redis;
    @Autowired CapturingSms sms;

    record Login(String accessToken, Cookie refreshCookie) {}

    @BeforeEach
    void cleanRedis() {
        // MockMvc requests come from 127.0.0.1; reset that IP's limit and this phone's OTP keys
        redis.delete(List.of("otp:code:" + PHONE, "otp:tries:" + PHONE, "otp:cooldown:" + PHONE,
                "rate:otp:phone:" + PHONE, "rate:otp:ip:127.0.0.1"));
    }

    @Test
    void accessTokenOpensProtectedEndpoint() throws Exception {
        Login login = login();

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(PHONE));
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sameUserOnEveryLogin() throws Exception {
        String first = userId(login());
        redis.delete("otp:cooldown:" + PHONE);   // skip the 30 s wait between logins

        assertThat(userId(login())).isEqualTo(first);
    }

    @Test
    void refreshRotatesAndReuseLogsOutEverywhere() throws Exception {
        Cookie original = login().refreshCookie();

        MockHttpServletResponse refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(original))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse();
        Cookie rotated = refreshed.getCookie(AuthController.REFRESH_COOKIE);
        assertThat(rotated.getValue()).isNotEqualTo(original.getValue());

        // Replaying the old cookie = theft signal: rejected, and the new one is revoked too
        mvc.perform(post("/api/v1/auth/refresh").cookie(original)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(rotated)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        Cookie cookie = login().refreshCookie();

        MockHttpServletResponse out = mvc.perform(post("/api/v1/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();
        assertThat(out.getCookie(AuthController.REFRESH_COOKIE).getMaxAge()).isZero();

        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshCookieIsLockedDown() throws Exception {
        Cookie cookie = login().refreshCookie();

        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
    }

    private Login login() throws Exception {
        mvc.perform(post("/api/v1/auth/otp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + PHONE + "\"}"))
                .andExpect(status().isNoContent());

        MockHttpServletResponse res = mvc.perform(post("/api/v1/auth/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + PHONE + "\",\"code\":\"" + sms.lastCode() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse();
        return new Login(JsonPath.read(res.getContentAsString(), "$.accessToken"),
                res.getCookie(AuthController.REFRESH_COOKIE));
    }

    private String userId(Login login) throws Exception {
        String body = mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + login.accessToken()))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
