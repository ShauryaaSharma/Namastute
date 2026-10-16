package com.namastute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.namastute.users.UserService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// Per-person limits (Learning Notes 68): matches 30/min, ask 10/min, plan a trip 5/min. Fresh users every test,
// so the per-person counters in Redis always start at zero.
@SpringBootTest
@AutoConfigureMockMvc
class RateLimitsTest {

    static final Instant EXIT = Instant.now().plus(Duration.ofHours(7));

    @Autowired MockMvc mvc;
    @Autowired UserService users;

    @Test
    void matchesAreLimitedPerPersonWithRetryAfter() throws Exception {
        UUID asha = user(), ravi = user();
        String ashaTrip = JsonPath.read(planTrip(asha, "DEL-T2").andReturn().getResponse().getContentAsString(), "$.id");
        String raviTrip = JsonPath.read(planTrip(ravi, "DEL-T2").andReturn().getResponse().getContentAsString(), "$.id");

        for (int i = 0; i < 30; i++) {
            mvc.perform(get("/api/v1/trips/" + ashaTrip + "/matches").with(as(asha))).andExpect(status().isOk());
        }
        MvcResult refused = mvc.perform(get("/api/v1/trips/" + ashaTrip + "/matches").with(as(asha)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.error.message").value("You're going a bit fast. Try again in a minute."))
                .andReturn();
        assertThat(Integer.parseInt(refused.getResponse().getHeader("Retry-After"))).isBetween(1, 60);

        mvc.perform(get("/api/v1/trips/" + raviTrip + "/matches").with(as(ravi)))
                .andExpect(status().isOk());   // per person: Ravi isn't slowed by Asha
    }

    @Test
    void askingIsLimitedToTenAMinute() throws Exception {
        UUID asha = user();
        planTrip(asha, "DEL-T2").andExpect(status().isCreated());
        for (int i = 0; i < 10; i++) {   // nobody there: the usual 404, but each attempt counts
            ask(asha, UUID.randomUUID()).andExpect(status().isNotFound());
        }
        ask(asha, UUID.randomUUID()).andExpect(status().isTooManyRequests());
    }

    @Test
    void planningIsLimitedToFiveAMinute() throws Exception {
        UUID asha = user();
        for (int i = 0; i < 5; i++) {
            planTrip(asha, "DEL-T9").andExpect(status().isBadRequest());   // unknown terminal: refused, but counted
        }
        planTrip(asha, "DEL-T2").andExpect(status().isTooManyRequests());   // even a good one, until the minute is up
    }

    private ResultActions planTrip(UUID user, String terminal) throws Exception {
        String json = """
                {"terminalId":"%s","exitAt":"%s","destLat":28.4936,"destLng":77.0930,"destArea":"DLF Phase 3, Gurugram",\
                "seats":1,"bags":1,"womenOnly":false}""".formatted(terminal, EXIT);
        return mvc.perform(post("/api/v1/trips").with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions ask(UUID user, UUID toTripId) throws Exception {
        return mvc.perform(post("/api/v1/requests").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"toTripId\":\"" + toTripId + "\"}"));
    }

    private UUID user() {
        return users.findOrCreateByPhone("+9193" + String.format("%08d", (int) (Math.random() * 100_000_000)));
    }

    private static RequestPostProcessor as(UUID user) {
        return jwt().jwt(j -> j.subject(user.toString()));
    }
}
