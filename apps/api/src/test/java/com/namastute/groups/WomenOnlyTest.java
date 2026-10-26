package com.namastute.groups;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// Women-only trips and groups (Learning Notes 84): one test per place the rule is enforced. Fresh users every test, at
// Terminal 3 twelve hours ahead (no other test uses that slot). Each test then cancels its own users' trips and groups,
// even when it fails: otherwise they'd crowd the next test's top 5 matches and a check could pass by luck.
@SpringBootTest
@AutoConfigureMockMvc
class WomenOnlyTest {

    static final Instant EXIT = Instant.now().plus(Duration.ofHours(12));

    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;

    final List<UUID> created = new ArrayList<>();

    // Only this test's own rows (never other data in the development database)
    @AfterEach
    void cancelOwnTripsAndGroups() {
        for (UUID user : created) {
            jdbc.update("""
                    update groups set status = 'cancelled' where status = 'open'
                      and id in (select group_id from group_members where user_id = ?)""", user);
            jdbc.update("update trip_requests set status = 'cancelled' where user_id = ? and status in ('searching', 'grouped')", user);
        }
    }

    @Test
    void onlyWomenWithAVerifiedIdMayPlanAWomenOnlyTrip() throws Exception {
        UUID neha = user("Neha", null), arjun = user("Arjun", "male"), other = user("Kiran", "other"), asha = user("Asha", "female");
        for (UUID notAllowed : List.of(neha, arjun, other)) {
            plan(notAllowed, true).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("WOMEN_ONLY_NEEDS_VERIFICATION"));
            mvc.perform(get("/api/v1/me").with(as(notAllowed))).andExpect(jsonPath("$.womenOnlyAllowed").value(false));
        }
        mvc.perform(get("/api/v1/me").with(as(asha))).andExpect(jsonPath("$.womenOnlyAllowed").value(true));
        plan(asha, true).andExpect(status().isCreated()).andExpect(jsonPath("$.womenOnly").value(true));
    }

    @Test
    void aWomenOnlyTripIsSeenOnlyByVerifiedWomenAndSeesOnlyThem() throws Exception {
        UUID asha = user("Asha", "female"), ritika = user("Ritika", "female"), neha = user("Neha", null), arjun = user("Arjun", "male");
        String ashaTrip = trip(asha, true), ritikaTrip = trip(ritika, false), nehaTrip = trip(neha, false), arjunTrip = trip(arjun, false);

        assertThat(shownAsWomenOnly(ritika, ritikaTrip, ashaTrip)).isTrue();
        assertThat(riderTripIds(neha, nehaTrip)).doesNotContain(ashaTrip);
        assertThat(riderTripIds(arjun, arjunTrip)).doesNotContain(ashaTrip);
        assertThat(riderTripIds(asha, ashaTrip)).contains(ritikaTrip).doesNotContain(nehaTrip, arjunTrip);
    }

    @Test
    void aCraftedAskGetsTheSameAnswerAsATripThatDoesntExist() throws Exception {
        UUID asha = user("Asha", "female"), arjun = user("Arjun", "male"), neha = user("Neha", null);
        String ashaTrip = trip(asha, true);
        trip(arjun, false);
        String nehaTrip = trip(neha, false);

        ask(arjun, ashaTrip).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));
        ask(asha, nehaTrip).andExpect(status().isNotFound());   // she asked for women only
    }

    @Test
    void theGroupIsBornWomenOnlyAndOnlyEligibleWomenSeeOrJoinIt() throws Exception {
        UUID asha = user("Asha", "female"), ritika = user("Ritika", "female"), priya = user("Priya", "female"), arjun = user("Arjun", "male");
        String ashaTrip = trip(asha, true), ritikaTrip = trip(ritika, false);
        ask(ritika, ashaTrip).andExpect(status().isCreated());
        accept(asha, receivedBy(asha).get(0)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/groups/current").with(as(ritika))).andExpect(jsonPath("$.womenOnly").value(true));

        String priyaTrip = trip(priya, false), arjunTrip = trip(arjun, false);
        assertThat(shownAsWomenOnly(priya, priyaTrip, ashaTrip)).isTrue();
        assertThat(riderTripIds(arjun, arjunTrip)).doesNotContain(ashaTrip, ritikaTrip);
        ask(arjun, ashaTrip).andExpect(status().isNotFound());
        ask(priya, ashaTrip).andExpect(status().isCreated());
    }

    @Test
    void aWomenOnlyTripDoesntSeeOrJoinAnOrdinaryGroup() throws Exception {
        UUID ritika = user("Ritika", "female"), priya = user("Priya", "female"), asha = user("Asha", "female");
        String priyaTrip = trip(priya, false), ritikaTrip = trip(ritika, false);
        ask(ritika, priyaTrip);
        accept(priya, receivedBy(priya).get(0)).andExpect(status().isNoContent());   // an ordinary group of two women

        String ashaTrip = trip(asha, true);
        assertThat(riderTripIds(asha, ashaTrip)).doesNotContain(priyaTrip, ritikaTrip);
        ask(asha, priyaTrip).andExpect(status().isNotFound());
    }

    // A request sent while someone was alone stays pending after they join a group, so accept must check again
    @Test
    void acceptChecksAgainBecauseTheGroupMayHaveBecomeWomenOnly() throws Exception {
        UUID asha = user("Asha", "female"), ritika = user("Ritika", "female"), neha = user("Neha", null);
        String ashaTrip = trip(asha, true), ritikaTrip = trip(ritika, false);
        trip(neha, false);
        ask(neha, ritikaTrip).andExpect(status().isCreated());   // fine: Ritika is alone and didn't ask for women only
        ask(ritika, ashaTrip);
        accept(asha, receivedBy(asha).get(0)).andExpect(status().isNoContent());   // Ritika is now in Asha's women-only group

        accept(asha, receivedBy(asha).get(0))   // Neha's old request, now answerable by the group
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("WOMEN_ONLY"));
        mvc.perform(get("/api/v1/groups/current").with(as(asha))).andExpect(jsonPath("$.members.length()").value(2));
    }

    // gender null = no approved ID check
    private UUID user(String name, String gender) {
        UUID id = users.findOrCreateByPhone("+9195" + String.format("%08d", (int) (Math.random() * 100_000_000)));
        jdbc.update("update users set name = ? where id = ?", name, id);
        created.add(id);
        if (gender != null) {
            jdbc.update("update users set verification = 'verified', gender = ?::gender, verified_at = now() where id = ?", gender, id);
        }
        return id;
    }

    // Terminal 3 to Dwarka Sector 21; returns the trip id
    private String trip(UUID user, boolean womenOnly) throws Exception {
        String body = plan(user, womenOnly).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions plan(UUID user, boolean womenOnly) throws Exception {
        String json = """
                {"terminalId":"DEL-T3","exitAt":"%s","destLat":28.5523,"destLng":77.0583,"destArea":"Dwarka Sector 21",\
                "seats":1,"bags":1,"womenOnly":%s}""".formatted(EXIT, womenOnly);
        return mvc.perform(post("/api/v1/trips").with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // Every rider of every match: a group's match is labelled with one member's trip id, and which one isn't fixed
    // (two members who joined in the same instant), so tests look for any rider, never for the label
    private List<String> riderTripIds(UUID user, String tripId) throws Exception {
        return JsonPath.read(matches(user, tripId), "$[*].riders[*].tripId");
    }

    // Is the match that includes this rider marked women-only? Fails if the rider isn't among the matches at all.
    private boolean shownAsWomenOnly(UUID user, String tripId, String riderTrip) throws Exception {
        List<Map<String, Object>> all = JsonPath.read(matches(user, tripId), "$[*]");
        return all.stream()
                .filter(m -> JsonPath.<List<String>>read(m, "$.riders[*].tripId").contains(riderTrip))
                .map(m -> (Boolean) m.get("womenOnly"))
                .findFirst().orElseThrow(() -> new AssertionError(riderTrip + " isn't among the matches"));
    }

    private String matches(UUID user, String tripId) throws Exception {
        return mvc.perform(get("/api/v1/trips/" + tripId + "/matches").with(as(user)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private List<String> receivedBy(UUID user) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/v1/requests").with(as(user))).andReturn().getResponse().getContentAsString(), "$.received[*].id");
    }

    private ResultActions ask(UUID from, String toTripId) throws Exception {
        return mvc.perform(post("/api/v1/requests").with(as(from)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"toTripId\":\"" + toTripId + "\"}"));
    }

    private ResultActions accept(UUID user, String requestId) throws Exception {
        return mvc.perform(post("/api/v1/requests/" + requestId + "/accept").with(as(user)));
    }

    private static RequestPostProcessor as(UUID user) {
        return jwt().jwt(j -> j.subject(user.toString()));
    }
}
