package com.namastute.trips;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class MatchesTest {

    static final Instant EXIT = Instant.now().plus(Duration.ofHours(2));

    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearTerminal1() {
        // These tests use T1 only; trips left by earlier runs would show up as extra matches
        jdbc.update("update trip_requests set status = 'cancelled' where terminal_id = 'DEL-T1' and status = 'searching'");
    }

    @Test
    void findsTheGoodMatchAndShowsOnlyWhatOthersMaySee() throws Exception {
        UUID me = user("Keshav Dixit");
        String myTrip = createTrip(me, "DEL-T1", 0, 28.4936, 77.0930, "DLF Phase 3, Gurugram");

        createTrip(user("Asha Rao"), "DEL-T1", 10, 28.4683, 77.0628, "Sector 29, Gurugram");   // the match
        createTrip(user("Ravi Kumar"), "DEL-T1", 0, 28.5523, 77.0583, "Dwarka, Delhi");        // stage 2: wrong direction
        createTrip(user("Neel Shah"), "DEL-T1", 0, 28.5708, 77.3260, "Noida");                 // stage 1: over 15 km away
        createTrip(user("Tara Sen"), "DEL-T1", 90, 28.4936, 77.0930, "DLF Phase 3, Gurugram");  // stage 1: 90 min later
        createTrip(user("Meera Iyer"), "DEL-T3", 0, 28.4936, 77.0930, "DLF Phase 3, Gurugram"); // stage 1: other terminal
        UUID blocked = user("Kabir Das");
        createTrip(blocked, "DEL-T1", 0, 28.4936, 77.0930, "DLF Phase 3, Gurugram");           // stage 1: blocked me
        jdbc.update("insert into blocks (blocker_id, blocked_id) values (?, ?)", blocked, me);

        mvc.perform(get("/api/v1/trips/" + myTrip + "/matches").with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].firstName").value("Asha"))
                .andExpect(jsonPath("$[0].destArea").value("Sector 29, Gurugram"))
                .andExpect(jsonPath("$[0].minutesAfterYou").value(10))
                .andExpect(jsonPath("$[0].detourKm").value(0.5))   // 0.498 km from T1, shown to the nearest 0.5
                .andExpect(jsonPath("$[0].seats").value(1))
                .andExpect(jsonPath("$[0].bags").value(1))
                .andExpect(jsonPath("$[0].tripId").isString())   // opaque handle for "ask to share"
                .andExpect(jsonPath("$[0].length()").value(7))   // nothing else: no phone, exact destination, UPI ID or user id
                .andExpect(jsonPath("$[0].phone").doesNotExist())
                .andExpect(jsonPath("$[0].destLat").doesNotExist());
    }

    @Test
    void onlyForYourOwnTrip() throws Exception {
        String someoneElsesTrip = createTrip(user("Asha Rao"), "DEL-T1", 0, 28.4683, 77.0628, "Sector 29, Gurugram");

        mvc.perform(get("/api/v1/trips/" + someoneElsesTrip + "/matches").with(as(user("Keshav Dixit"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));
    }

    private UUID user(String name) {
        UUID id = users.findOrCreateByPhone("+9193" + String.format("%08d", (int) (Math.random() * 100_000_000)));
        jdbc.update("update users set name = ? where id = ?", name, id);
        return id;
    }

    private String createTrip(UUID user, String terminal, int minutesAfter, double lat, double lng, String area) throws Exception {
        String json = """
                {"terminalId":"%s","exitAt":"%s","destLat":%s,"destLng":%s,"destArea":"%s","seats":1,"bags":1,"womenOnly":false}"""
                .formatted(terminal, EXIT.plus(Duration.ofMinutes(minutesAfter)), lat, lng, area);
        String body = mvc.perform(post("/api/v1/trips").with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static RequestPostProcessor as(UUID user) {
        return jwt().jwt(j -> j.subject(user.toString()));
    }
}
