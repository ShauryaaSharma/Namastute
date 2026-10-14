package com.namastute.trips;

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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class TripsTest {

    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired TripService trips;

    UUID userId;

    @BeforeEach
    void freshUser() {
        userId = users.findOrCreateByPhone("+9191" + String.format("%08d", (int) (Math.random() * 100_000_000)));
    }

    @Test
    void listsTheThreeIgiTerminals() throws Exception {
        mvc.perform(get("/api/v1/terminals").with(me()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.id == 'DEL-T3')].name").value("Terminal 3"));
    }

    @Test
    void createsTripAndStoresTheDestinationTheRightWayRound() throws Exception {
        String body = createTrip(trip()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("searching"))
                .andExpect(jsonPath("$.terminalName").value("Terminal 3"))
                .andExpect(jsonPath("$.flightNo").value("AI865"))
                .andExpect(jsonPath("$.destLat").value(28.4949))
                .andExpect(jsonPath("$.destLng").value(77.0886))
                .andReturn().getResponse().getContentAsString();

        // Ask PostGIS itself which number it thinks is the latitude (ST_Y) and longitude (ST_X)
        String id = JsonPath.read(body, "$.id");
        Map<String, Object> row = jdbc.queryForMap(
                "select ST_Y(dest::geometry) as lat, ST_X(dest::geometry) as lng, solo_km from trip_requests where id = ?::uuid", id);
        assertThat((Double) row.get("lat")).isEqualTo(28.4949);
        assertThat((Double) row.get("lng")).isEqualTo(77.0886);
        assertThat((Float) row.get("solo_km")).as("about 6.8 km straight × 1.4").isBetween(8f, 11f);
    }

    @Test
    void currentTripThenCancel() throws Exception {
        String id = JsonPath.read(createTrip(trip()).andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/v1/trips/current").with(me()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(delete("/api/v1/trips/" + id).with(me())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/trips/current").with(me())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/trips/" + id).with(me())).andExpect(status().isNotFound());
    }

    @Test
    void onlyOneActiveTripAtATime() throws Exception {
        createTrip(trip()).andExpect(status().isCreated());

        createTrip(trip()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACTIVE_TRIP_EXISTS"));
    }

    @Test
    void staleTripDoesNotBlockANewOne() throws Exception {
        String id = JsonPath.read(createTrip(trip()).andReturn().getResponse().getContentAsString(), "$.id");
        jdbc.update("update trip_requests set exit_at = now() - interval '2 hours' where id = ?::uuid", id);

        mvc.perform(get("/api/v1/trips/current").with(me())).andExpect(status().isNoContent());
        createTrip(trip()).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select status::text from trip_requests where id = ?::uuid", String.class, id))
                .isEqualTo("expired");
    }

    @Test
    void theSweepExpiresEveryonesStaleTrips() throws Exception {
        String stale = JsonPath.read(createTrip(trip()).andReturn().getResponse().getContentAsString(), "$.id");
        jdbc.update("update trip_requests set exit_at = now() - interval '2 hours' where id = ?::uuid", stale);
        UUID someoneElse = users.findOrCreateByPhone("+9192" + String.format("%08d", (int) (Math.random() * 100_000_000)));
        String fresh = JsonPath.read(mvc.perform(post("/api/v1/trips").with(jwt().jwt(j -> j.subject(someoneElse.toString())))
                .contentType(MediaType.APPLICATION_JSON).content(trip())).andReturn().getResponse().getContentAsString(), "$.id");

        trips.expireStaleTrips();   // the background job, called directly instead of waiting a minute

        assertThat(jdbc.queryForObject("select status::text from trip_requests where id = ?::uuid", String.class, stale))
                .isEqualTo("expired");
        assertThat(jdbc.queryForObject("select status::text from trip_requests where id = ?::uuid", String.class, fresh))
                .isEqualTo("searching");
    }

    @Test
    void cannotCancelSomeoneElsesTrip() throws Exception {
        String id = JsonPath.read(createTrip(trip()).andReturn().getResponse().getContentAsString(), "$.id");
        UUID stranger = users.findOrCreateByPhone("+9192" + String.format("%08d", (int) (Math.random() * 100_000_000)));

        mvc.perform(delete("/api/v1/trips/" + id).with(jwt().jwt(j -> j.subject(stranger.toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsBadRequests() throws Exception {
        expectError(trip().replace("\"DEL-T3\"", "\"DEL-T9\""), 400, "UNKNOWN_TERMINAL");
        expectError(trip(Instant.now().minus(Duration.ofHours(1))), 400, "EXIT_TIME_OUT_OF_RANGE");
        expectError(trip(Instant.now().plus(Duration.ofHours(30))), 400, "EXIT_TIME_OUT_OF_RANGE");
        expectError(trip().replace("\"destLat\":28.4949", "\"destLat\":19.0760").replace("\"destLng\":77.0886", "\"destLng\":72.8777"),
                400, "DESTINATION_TOO_FAR");   // Mumbai
        expectError(trip().replace("\"womenOnly\":false", "\"womenOnly\":true"), 403, "WOMEN_ONLY_NEEDS_VERIFICATION");
        expectError(trip().replace("\"seats\":1", "\"seats\":4"), 400, "VALIDATION_FAILED");
        expectError(trip().replace("\"flightNo\":\"ai 865\"", "\"flightNo\":\"hello\""), 400, "VALIDATION_FAILED");
    }

    @Test
    void needsLogin() throws Exception {
        mvc.perform(get("/api/v1/trips/current")).andExpect(status().isUnauthorized());
    }

    private JwtRequestPostProcessor me() {
        return jwt().jwt(j -> j.subject(userId.toString()));
    }

    private ResultActions createTrip(String json) throws Exception {
        return mvc.perform(post("/api/v1/trips").with(me()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void expectError(String json, int status, String code) throws Exception {
        createTrip(json).andExpect(status().is(status)).andExpect(jsonPath("$.error.code").value(code));
    }

    // Terminal 3 → DLF Phase 3, Gurugram, leaving in an hour
    private static String trip() {
        return trip(Instant.now().plus(Duration.ofHours(1)));
    }

    private static String trip(Instant exitAt) {
        return """
                {"terminalId":"DEL-T3","flightNo":"ai 865","exitAt":"%s","destLat":28.4949,"destLng":77.0886,\
                "destArea":"DLF Phase 3, Gurugram","seats":1,"bags":2,"womenOnly":false}""".formatted(exitAt);
    }
}
