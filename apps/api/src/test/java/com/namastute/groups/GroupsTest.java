package com.namastute.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.namastute.shared.ApiException;
import com.namastute.users.UserService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// Uses Terminal 2 only, with fresh users in every test, so leftovers from other tests can't interfere
@SpringBootTest
@AutoConfigureMockMvc
class GroupsTest {

    static final Instant EXIT = Instant.now().plus(Duration.ofHours(3));

    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired GroupService groups;

    @Test
    void askThenAcceptFormsAGroupWithTheAskerAsMaker() throws Exception {
        UUID keshav = user("Keshav Dixit"), asha = user("Asha Rao"), neha = user("Neha Gupta");
        jdbc.update("update users set upi_id = 'keshav@okaxis' where id = ?", keshav);
        trip(keshav, 1, 1);
        String ashaTrip = trip(asha, 1, 1);
        String nehaTrip = trip(neha, 1, 1);
        ask(keshav, ashaTrip).andExpect(status().isCreated());
        ask(keshav, nehaTrip).andExpect(status().isCreated());   // several requests out at once is fine

        mvc.perform(get("/api/v1/requests").with(as(asha)))
                .andExpect(jsonPath("$.received.length()").value(1))
                .andExpect(jsonPath("$.received[0].firstName").value("Keshav"));
        accept(asha, receivedBy(asha).get(0)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/groups/current").with(as(keshav)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.youAreBooker").value(true))     // the asker made the group
                .andExpect(jsonPath("$.members.length()").value(2));
        mvc.perform(get("/api/v1/groups/current").with(as(asha)))
                .andExpect(jsonPath("$.youAreBooker").value(false))
                .andExpect(jsonPath("$.bookerUpiId").value("keshav@okaxis"));   // members may see it
        mvc.perform(get("/api/v1/trips/current").with(as(asha))).andExpect(jsonPath("$.status").value("grouped"));
        assertThat(receivedBy(neha)).as("Keshav's other request was withdrawn").isEmpty();
    }

    @Test
    void whileAloneOnlyThePersonAskedCanAnswer() throws Exception {
        UUID keshav = user("Keshav Dixit"), asha = user("Asha Rao"), stranger = user("Ravi Kumar");
        trip(keshav, 1, 1);
        ask(keshav, trip(asha, 1, 1));
        String request = receivedBy(asha).get(0);

        accept(stranger, request).andExpect(status().isNotFound());
        accept(keshav, request).andExpect(status().isNotFound());   // you can't accept your own request
        accept(asha, request).andExpect(status().isNoContent());
    }

    @Test
    void anyMemberCanAnswerForTheGroup() throws Exception {
        UUID keshav = user("Keshav Dixit"), asha = user("Asha Rao"), neha = user("Neha Gupta");
        String ashaTrip = pair(keshav, asha);
        trip(neha, 1, 1);
        ask(neha, ashaTrip).andExpect(status().isCreated());   // Neha asked Asha...

        accept(keshav, receivedBy(keshav).get(0)).andExpect(status().isNoContent());   // ...and Keshav said yes

        mvc.perform(get("/api/v1/groups/current").with(as(neha))).andExpect(jsonPath("$.members.length()").value(3));
    }

    @Test
    void tooBigForOneCarIsRefusedAndNothingChanges() throws Exception {
        UUID keshav = user("Keshav Dixit"), asha = user("Asha Rao");
        trip(keshav, 3, 1);
        ask(keshav, trip(asha, 3, 1));

        accept(asha, receivedBy(asha).get(0)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GROUP_FULL"));

        // The transaction rolled back: no group, both still searching
        mvc.perform(get("/api/v1/groups/current").with(as(asha))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/trips/current").with(as(asha))).andExpect(jsonPath("$.status").value("searching"));
        mvc.perform(get("/api/v1/trips/current").with(as(keshav))).andExpect(jsonPath("$.status").value("searching"));
    }

    @Test
    void onlyTheMakerLocksAndLockingDeclinesWaitingRequests() throws Exception {
        UUID keshav = user("Keshav Dixit"), asha = user("Asha Rao"), neha = user("Neha Gupta");
        String ashaTrip = pair(keshav, asha);
        trip(neha, 1, 1);
        ask(neha, ashaTrip);
        String group = groupOf(keshav);

        lock(asha, group).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CANNOT_LOCK"));
        lock(keshav, group).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/requests").with(as(neha))).andExpect(jsonPath("$.sent[0].status").value("declined"));
        ask(neha, tripOf(keshav)).andExpect(status().isNotFound());   // a locked group takes no new requests
    }

    @Test
    void leavingALockedGroupNeedsAReasonAndReportsTheBooker() throws Exception {
        UUID keshav = user("Keshav Dixit"), asha = user("Asha Rao"), neha = user("Neha Gupta");
        String group = trio(keshav, asha, neha);
        lock(keshav, group);

        leave(asha, group, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("REASON_REQUIRED"));
        leave(asha, group, "Booker not answering").andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/groups/current").with(as(keshav)))
                .andExpect(jsonPath("$.status").value("locked"))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.updates[0]").value("Asha left: Booker not answering"));
        mvc.perform(get("/api/v1/trips/current").with(as(asha))).andExpect(jsonPath("$.status").value("searching"));
        assertThat(jdbc.queryForObject("select count(*) from reports where reporter_id = ? and reported_id = ?",
                Integer.class, asha, keshav)).isEqualTo(1);
    }

    @Test
    void whenTheMakerLeavesTheLongestStandingMemberTakesOverAndTheGroupReopens() throws Exception {
        UUID keshav = user("Keshav Dixit"), asha = user("Asha Rao"), neha = user("Neha Gupta");
        String group = trio(keshav, asha, neha);
        lock(keshav, group);

        leave(keshav, group, "Flight delayed").andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/groups/current").with(as(asha)))
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.youAreBooker").value(true));   // Asha has been here longer than Neha
        assertThat(jdbc.queryForObject("select count(*) from reports where group_id = ?::uuid", Integer.class, group))
                .as("the booker leaving files no report").isZero();
    }

    @Test
    void aGroupOfTwoIsCancelledWhenOneLeaves() throws Exception {
        UUID keshav = user("Keshav Dixit"), asha = user("Asha Rao");
        pair(keshav, asha);

        leave(asha, groupOf(asha), null).andExpect(status().isNoContent());   // open group: no reason needed

        mvc.perform(get("/api/v1/groups/current").with(as(keshav))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/trips/current").with(as(keshav))).andExpect(jsonPath("$.status").value("searching"));
    }

    // Design 3.4: 10 accepts at the same moment into a group with ONE free seat. Exactly one may succeed.
    @Test
    void lastSeatRace() throws Exception {
        UUID keshav = user("Keshav Dixit"), asha = user("Asha Rao");
        trip(keshav, 2, 1);
        ask(keshav, trip(asha, 1, 1));
        accept(asha, receivedBy(asha).get(0));   // 3 of 4 seats taken
        String group = groupOf(keshav);
        List<UUID> requests = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID asker = user("Traveller");
            trip(asker, 1, 0);
            ask(asker, tripOf(asha));
        }
        requests.addAll(receivedBy(keshav).stream().map(UUID::fromString).toList());
        assertThat(requests).hasSize(10);

        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        List<String> failures = new CopyOnWriteArrayList<>();
        for (UUID request : requests) {
            pool.submit(() -> {
                start.await();                       // everyone waits at the gate...
                try {
                    groups.accept(keshav, request);
                    successes.incrementAndGet();
                } catch (ApiException e) {
                    failures.add(e.code());
                }
                return null;
            });
        }
        start.countDown();                           // ...and starts at the same instant
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures).hasSize(9).containsOnly("GROUP_FULL");
        assertThat(jdbc.queryForObject("select seats_taken from groups where id = ?::uuid", Integer.class, group)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from group_members where group_id = ?::uuid and status = 'accepted'",
                Integer.class, group)).isEqualTo(3);
    }

    // ---- helpers ----

    // Keshav asks Asha, Asha accepts; returns Asha's trip id
    private String pair(UUID keshav, UUID asha) throws Exception {
        trip(keshav, 1, 1);
        String ashaTrip = trip(asha, 1, 1);
        ask(keshav, ashaTrip);
        accept(asha, receivedBy(asha).get(0)).andExpect(status().isNoContent());
        return ashaTrip;
    }

    // Keshav + Asha, then Neha joins; returns the group id
    private String trio(UUID keshav, UUID asha, UUID neha) throws Exception {
        String ashaTrip = pair(keshav, asha);
        trip(neha, 1, 1);
        ask(neha, ashaTrip);
        accept(asha, receivedBy(asha).get(0)).andExpect(status().isNoContent());
        return groupOf(keshav);
    }

    private UUID user(String name) {
        UUID id = users.findOrCreateByPhone("+9194" + String.format("%08d", (int) (Math.random() * 100_000_000)));
        jdbc.update("update users set name = ? where id = ?", name, id);
        return id;
    }

    // A trip from Terminal 2 to DLF Phase 3; returns its id
    private String trip(UUID user, int seats, int bags) throws Exception {
        String json = """
                {"terminalId":"DEL-T2","exitAt":"%s","destLat":28.4936,"destLng":77.0930,"destArea":"DLF Phase 3, Gurugram",\
                "seats":%d,"bags":%d,"womenOnly":false}""".formatted(EXIT, seats, bags);
        String body = mvc.perform(post("/api/v1/trips").with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String tripOf(UUID user) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/v1/trips/current").with(as(user))).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String groupOf(UUID user) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/v1/groups/current").with(as(user))).andReturn().getResponse().getContentAsString(), "$.id");
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

    private ResultActions lock(UUID user, String groupId) throws Exception {
        return mvc.perform(post("/api/v1/groups/" + groupId + "/lock").with(as(user)));
    }

    private ResultActions leave(UUID user, String groupId, String reason) throws Exception {
        var request = post("/api/v1/groups/" + groupId + "/leave").with(as(user));
        if (reason != null) {
            request.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + reason + "\"}");
        }
        return mvc.perform(request);
    }

    private static RequestPostProcessor as(UUID user) {
        return jwt().jwt(j -> j.subject(user.toString()));
    }
}
