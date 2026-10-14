package com.namastute.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;

// Behind Caddy, the OTP limit must count the REAL client IP from X-Forwarded-For (Learning Notes 54.5).
// A real server on a random port: MockMvc skips Tomcat, which is where forwarded headers are handled.
// The test request comes from 127.0.0.1, a trusted private address, exactly like Caddy on Docker's network.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.forward-headers-strategy=native")   // as in application-prod.yaml
class ForwardedIpTest {

    @LocalServerPort int port;
    @Autowired StringRedisTemplate redis;

    @Test
    void theOtpLimitCountsTheForwardedClientIp() throws Exception {
        String clientIp = "203.0.113." + (int) (Math.random() * 250 + 1);   // a documentation-only address range
        String phone = "+9196" + String.format("%08d", (int) (Math.random() * 100_000_000));
        redis.delete("rate:otp:ip:" + clientIp);

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/otp"))
                        .header("Content-Type", "application/json")
                        .header("X-Forwarded-For", clientIp)
                        .POST(HttpRequest.BodyPublishers.ofString("{\"phone\":\"" + phone + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isBetween(200, 299);
        assertThat(redis.hasKey("rate:otp:ip:" + clientIp)).as("counted against the client, not the proxy").isTrue();
    }
}
