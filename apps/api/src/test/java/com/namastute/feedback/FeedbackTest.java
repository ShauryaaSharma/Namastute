package com.namastute.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.namastute.users.UserService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// Report and block (Learning Notes 56). Terminal 2, six hours ahead, fresh users every test; assertions look for
// specific trips, so leftovers from earlier runs can't interfere.
@SpringBootTest
@AutoConfigureMockMvc
class FeedbackTest {

    static final Instant EXIT = Instant.now().plus(Duration.ofHours(6));

    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;

    @Test
    void blockingHidesBothWaysSilentlyAndCanBeUndone() throws Exception {
        UUID asha = user("Asha Rao"), ravi = user("Ravi Kumar");
        String ashaTrip = trip(asha), raviTrip = trip(ravi);
        assertThat(matchTrips(asha, ashaTrip)).contains(raviTrip);

        block(asha, raviTrip).andExpect(status().isNoContent());

        assertThat(matchTrips(asha, ashaTrip)).doesNotContain(raviTrip);
        assertThat(matchTrips(ravi, raviTrip)).as("two-way invisible").doesNotContain(ashaTrip);
        ask(ravi, ashaTrip).andExpect(status().isNotFound())   // the same answer as any ended trip: nothing leaks
                .andExpect(jsonPath("$.error.message").value("That traveller isn't available any more"));
        mvc.perform(get("/api/v1/blocks").with(as(ravi))).andExpect(jsonPath("$.length()").value(0));   // Ravi isn't told

        String blockId = JsonPath.read(mvc.perform(get("/api/v1/blocks").with(as(asha)))
                .andExpect(jsonPath("$[0].firstName").value("Ravi"))
                .andExpect(jsonPath("$[0].length()").value(2))   // a handle and a first name, never a user id
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        mvc.perform(delete("/api/v1/blocks/" + blockId).with(as(asha))).andExpect(status().isNoContent());
        assertThat(matchTrips(asha, ashaTrip)).as("unblocked: visible again").contains(raviTrip);
        mvc.perform(delete("/api/v1/blocks/" + blockId).with(as(asha))).andExpect(status().isNotFound());
    }

    @Test
    void blockingWithdrawsPendingRequests() throws Exception {
        UUID asha = user("Asha Rao"), ravi = user("Ravi Kumar");
        String ashaTrip = trip(asha), raviTrip = trip(ravi);
        ask(ravi, ashaTrip).andExpect(status().isCreated());

        block(asha, raviTrip).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/requests").with(as(asha))).andExpect(jsonPath("$.received.length()").value(0));
        mvc.perform(get("/api/v1/requests").with(as(ravi))).andExpect(jsonPath("$.sent.length()").value(0));
    }

    @Test
    void reportsNeedASharedGroupAndHappenOncePerRide() throws Exception {
        UUID asha = user("Asha Rao"), keshav = user("Keshav Dixit"), stranger = user("Neel Shah");
        trip(asha);
        String keshavTrip = trip(keshav);
        trip(stranger);
        ask(asha, keshavTrip).andExpect(status().isCreated());
        String request = JsonPath.read(mvc.perform(get("/api/v1/requests").with(as(keshav))).andReturn().getResponse()
                .getContentAsString(), "$.received[0].id");
        mvc.perform(post("/api/v1/requests/" + request + "/accept").with(as(keshav))).andExpect(status().isNoContent());

        // Asha finds Keshav's handle on her group card
        List<String> handles = JsonPath.read(mvc.perform(get("/api/v1/groups/current").with(as(asha))).andReturn()
                .getResponse().getContentAsString(), "$.members[?(@.you == false)].tripId");
        assertThat(handles).containsExactly(keshavTrip);

        report(asha, keshavTrip, "harassment", "Rude in the chat").andExpect(status().isNoContent());
        report(asha, keshavTrip, "unsafe", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_REPORTED"));
        report(stranger, keshavTrip, "other", null).andExpect(status().isNotFound())   // never shared a group
                .andExpect(jsonPath("$.error.code").value("NOT_IN_A_GROUP_TOGETHER"));
        report(asha, keshavTrip, "rude", null).andExpect(status().isBadRequest());           // not a category
        report(asha, keshavTrip, "other", "x".repeat(501)).andExpect(status().isBadRequest()); // over 500

        assertThat(jdbc.queryForObject("select category from reports where reporter_id = ? and reported_id = ?",
                String.class, asha, keshav)).isEqualTo("harassment");
    }

    // ---- helpers ----

    private UUID user(String name) {
        UUID id = users.findOrCreateByPhone("+9197" + String.format("%08d", (int) (Math.random() * 100_000_000)));
        jdbc.update("update users set name = ? where id = ?", name, id);
        return id;
    }

    private String trip(UUID user) throws Exception {
        String json = """
                {"terminalId":"DEL-T2","exitAt":"%s","destLat":28.4936,"destLng":77.0930,"destArea":"DLF Phase 3, Gurugram",\
                "seats":1,"bags":1,"womenOnly":false}""".formatted(EXIT);
        return JsonPath.read(mvc.perform(post("/api/v1/trips").with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private List<String> matchTrips(UUID user, String tripId) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/v1/trips/" + tripId + "/matches").with(as(user)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$[*].riders[*].tripId");
    }

    private ResultActions ask(UUID from, String toTripId) throws Exception {
        return mvc.perform(post("/api/v1/requests").with(as(from)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"toTripId\":\"" + toTripId + "\"}"));
    }

    private ResultActions block(UUID user, String tripId) throws Exception {
        return mvc.perform(post("/api/v1/blocks").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripId\":\"" + tripId + "\"}"));
    }

    private ResultActions report(UUID user, String tripId, String category, String details) throws Exception {
        String body = "{\"tripId\":\"" + tripId + "\",\"category\":\"" + category + "\""
                + (details == null ? "" : ",\"details\":\"" + details + "\"") + "}";
        return mvc.perform(post("/api/v1/reports").with(as(user)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static RequestPostProcessor as(UUID user) {
        return jwt().jwt(j -> j.subject(user.toString()));
    }
}
