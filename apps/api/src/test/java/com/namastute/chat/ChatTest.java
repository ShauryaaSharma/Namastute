package com.namastute.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.namastute.users.UserService;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

// Real WebSocket + STOMP against the running app (RANDOM_PORT), with real signed access tokens.
// Uses Terminal 2 and fresh users in every test.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ChatTest {

    static final Instant EXIT = Instant.now().plus(Duration.ofHours(4));

    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired SimpUserRegistry sockets;

    @Test
    void membersGetMessagesLiveAndHistoryOldestFirst() throws Exception {
        UUID asha = user("Asha Rao"), keshav = user("Keshav Dixit");
        String group = pair(keshav, asha);
        BlockingQueue<Map<String, Object>> keshavInbox = subscribe(connect(keshav), keshav, "/topic/groups." + group + ".chat");

        connect(asha).send("/app/groups/" + group + "/chat", Map.of("body", "  At pillar 12  ", "clientId", "c-1"));

        Map<String, Object> live = keshavInbox.poll(5, TimeUnit.SECONDS);
        assertThat(live).isNotNull();
        assertThat(live).containsEntry("body", "At pillar 12")   // trimmed
                .containsEntry("firstName", "Asha").containsEntry("kind", "text").containsEntry("clientId", "c-1");

        mvc.perform(get("/api/v1/groups/" + group + "/messages").with(as(keshav)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("system"))           // "Keshav joined", written when the group formed
                .andExpect(jsonPath("$[-1:].body").value("At pillar 12"))   // oldest first, newest last
                .andExpect(jsonPath("$[-1:].you").value(false));
        mvc.perform(get("/api/v1/groups/" + group + "/messages").with(as(asha)))
                .andExpect(jsonPath("$[-1:].you").value(true));
    }

    @Test
    void outsidersCanNeitherListenNorRead() throws Exception {
        UUID asha = user("Asha Rao"), keshav = user("Keshav Dixit"), stranger = user("Ravi Kumar");
        String group = pair(keshav, asha);

        StompSession snoop = connect(stranger);   // logging in works...
        BlockingQueue<Map<String, Object>> snoopInbox = new LinkedBlockingQueue<>();
        snoop.subscribe("/topic/groups." + group + ".chat", collector(snoopInbox));   // ...but this SUBSCRIBE is refused
        connect(asha).send("/app/groups/" + group + "/chat", Map.of("body", "secret plan", "clientId", "c-2"));

        assertThat(snoopInbox.poll(1, TimeUnit.SECONDS)).isNull();
        mvc.perform(get("/api/v1/groups/" + group + "/messages").with(as(stranger))).andExpect(status().isNotFound());
    }

    @Test
    void connectingWithoutATokenIsRefused() {
        WebSocketStompClient client = stompClient();
        assertThatThrownBy(() -> client.connectAsync("ws://localhost:" + port + "/ws", handshake(), new StompHeaders(),
                new StompSessionHandlerAdapter() {}).get(3, TimeUnit.SECONDS)).isInstanceOf(Exception.class);
    }

    @Test
    void sendingStraightOntoATopicIsRefused() throws Exception {
        UUID asha = user("Asha Rao"), keshav = user("Keshav Dixit");
        String group = pair(keshav, asha);
        BlockingQueue<Map<String, Object>> keshavInbox = subscribe(connect(keshav), keshav, "/topic/groups." + group + ".chat");

        // A fake message that would skip saving and the membership check
        connect(asha).send("/topic/groups." + group + ".chat", Map.of("body", "fake", "kind", "system"));

        assertThat(keshavInbox.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void groupChangesAreAnnouncedAndLeavingEndsAccess() throws Exception {
        UUID asha = user("Asha Rao"), keshav = user("Keshav Dixit"), neha = user("Neha Gupta");
        String group = pair(keshav, asha);
        BlockingQueue<Map<String, Object>> updates = subscribe(connect(keshav), keshav, "/topic/groups." + group + ".updated");

        trip(neha);
        mvc.perform(post("/api/v1/requests").with(as(neha)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"toTripId\":\"" + tripOf(asha) + "\"}")).andExpect(status().isCreated());

        assertThat(updates.poll(5, TimeUnit.SECONDS)).as("Neha's request reached the group").containsEntry("groupId", group);

        mvc.perform(post("/api/v1/groups/" + group + "/leave").with(as(asha))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/groups/" + group + "/messages").with(as(asha))).andExpect(status().isNotFound());
    }

    @Test
    void historyPagesWithACursor() throws Exception {
        UUID asha = user("Asha Rao"), keshav = user("Keshav Dixit");
        String group = pair(keshav, asha);
        for (int i = 1; i <= 55; i++) {
            jdbc.update("insert into messages (group_id, user_id, body) values (?::uuid, ?, ?)", group, asha, "m" + i);
        }

        String latest = mvc.perform(get("/api/v1/groups/" + group + "/messages").with(as(keshav)))
                .andExpect(jsonPath("$.length()").value(50))
                .andExpect(jsonPath("$[0].body").value("m6"))
                .andExpect(jsonPath("$[49].body").value("m55"))
                .andReturn().getResponse().getContentAsString();
        long oldestShown = ((Number) JsonPath.read(latest, "$[0].id")).longValue();

        mvc.perform(get("/api/v1/groups/" + group + "/messages?before=" + oldestShown).with(as(keshav)))
                .andExpect(jsonPath("$.length()").value(6))           // "Keshav joined" + m1..m5
                .andExpect(jsonPath("$[5].body").value("m5"));
        mvc.perform(get("/api/v1/groups/" + group + "/messages?after=" + oldestShown).with(as(keshav)))
                .andExpect(jsonPath("$.length()").value(49))
                .andExpect(jsonPath("$[0].body").value("m7"));
    }

    // Live location (Learning Notes 60): relayed to the group only around pickup time, never saved
    @Test
    void locationReachesTheGroupOnlyAroundPickupAndIsNeverStored() throws Exception {
        UUID asha = user("Asha Rao"), keshav = user("Keshav Dixit"), stranger = user("Ravi Kumar");
        String group = pair(keshav, asha);
        String topic = "/topic/groups." + group + ".location";
        BlockingQueue<Map<String, Object>> ashaSees = subscribe(connect(asha), asha, topic);
        StompSession keshavLive = connect(keshav);
        Map<String, Object> pillar12 = Map.of("lat", 28.5562, "lng", 77.0870, "accuracy", 35.0);

        keshavLive.send("/app/groups/" + group + "/location", pillar12);
        assertThat(ashaSees.poll(1, TimeUnit.SECONDS)).as("open group: the cab isn't booked yet").isNull();

        mvc.perform(post("/api/v1/groups/" + group + "/lock").with(as(keshav))).andExpect(status().isNoContent());
        jdbc.update("update groups set meet_at = now() + interval '10 minutes' where id = ?::uuid", group);
        mvc.perform(get("/api/v1/groups/current").with(as(asha)))   // the browser learns the window from here
                .andExpect(jsonPath("$.sharingFrom").exists()).andExpect(jsonPath("$.sharingUntil").exists());
        int messages = jdbc.queryForObject("select count(*) from messages where group_id = ?::uuid", Integer.class, group);

        keshavLive.send("/app/groups/" + group + "/location", pillar12);
        assertThat(ashaSees.poll(5, TimeUnit.SECONDS)).containsEntry("tripId", tripOf(keshav)).containsEntry("sharing", true)
                .containsEntry("lat", 28.5562).containsEntry("lng", 77.0870).containsEntry("accuracy", 35.0);

        keshavLive.send("/app/groups/" + group + "/location", Map.of("lat", 200.0, "lng", 77.0870));
        assertThat(ashaSees.poll(1, TimeUnit.SECONDS)).as("impossible position dropped").isNull();

        Map<String, Object> stopped = new java.util.HashMap<>();
        stopped.put("lat", null);   // "I stopped sharing", as the browser sends it
        keshavLive.send("/app/groups/" + group + "/location", stopped);
        assertThat(ashaSees.poll(5, TimeUnit.SECONDS)).containsEntry("sharing", false).containsEntry("lat", null);

        BlockingQueue<Map<String, Object>> snoop = new LinkedBlockingQueue<>();
        connect(stranger).subscribe(topic, collector(snoop));   // refused by StompAuth
        keshavLive.send("/app/groups/" + group + "/location", pillar12);
        assertThat(ashaSees.poll(5, TimeUnit.SECONDS)).isNotNull();
        assertThat(snoop.poll(1, TimeUnit.SECONDS)).isNull();

        jdbc.update("update groups set meet_at = now() + interval '1 hour' where id = ?::uuid", group);
        keshavLive.send("/app/groups/" + group + "/location", pillar12);
        assertThat(ashaSees.poll(1, TimeUnit.SECONDS)).as("before the window opens").isNull();

        assertThat(jdbc.queryForObject("select count(*) from messages where group_id = ?::uuid", Integer.class, group))
                .as("positions are relayed, not saved").isEqualTo(messages);
    }

    // ---- helpers ----

    // Keshav asks Asha, Asha accepts; returns the group id
    private String pair(UUID keshav, UUID asha) throws Exception {
        trip(keshav);
        String ashaTrip = trip(asha);
        mvc.perform(post("/api/v1/requests").with(as(keshav)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"toTripId\":\"" + ashaTrip + "\"}")).andExpect(status().isCreated());
        String request = JsonPath.read(mvc.perform(get("/api/v1/requests").with(as(asha))).andReturn().getResponse()
                .getContentAsString(), "$.received[0].id");
        mvc.perform(post("/api/v1/requests/" + request + "/accept").with(as(asha))).andExpect(status().isNoContent());
        return JsonPath.read(mvc.perform(get("/api/v1/groups/current").with(as(asha))).andReturn().getResponse()
                .getContentAsString(), "$.id");
    }

    private String trip(UUID user) throws Exception {
        String json = """
                {"terminalId":"DEL-T2","exitAt":"%s","destLat":28.4936,"destLng":77.0930,"destArea":"DLF Phase 3, Gurugram",\
                "seats":1,"bags":1,"womenOnly":false}""".formatted(EXIT);
        return JsonPath.read(mvc.perform(post("/api/v1/trips").with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String tripOf(UUID user) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/v1/trips/current").with(as(user))).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private UUID user(String name) {
        UUID id = users.findOrCreateByPhone("+9195" + String.format("%08d", (int) (Math.random() * 100_000_000)));
        jdbc.update("update users set name = ? where id = ?", name, id);
        return id;
    }

    // A real access token, signed like AccessTokens does (same issuer, 15 minutes)
    private String token(UUID user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("namastute-api").subject(user.toString())
                .issuedAt(now).expiresAt(now.plus(Duration.ofMinutes(15))).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private StompSession connect(UUID user) throws Exception {
        StompHeaders connect = new StompHeaders();
        connect.add("Authorization", "Bearer " + token(user));
        return stompClient().connectAsync("ws://localhost:" + port + "/ws", handshake(), connect, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    // Subscribes and waits until the server has registered it, so no message can slip past before we listen
    private BlockingQueue<Map<String, Object>> subscribe(StompSession session, UUID user, String destination) throws Exception {
        BlockingQueue<Map<String, Object>> inbox = new LinkedBlockingQueue<>();
        session.subscribe(destination, collector(inbox));
        for (int i = 0; i < 100 && sockets.findSubscriptions(s -> s.getDestination().equals(destination)
                && s.getSession().getUser().getName().equals(user.toString())).isEmpty(); i++) {
            Thread.sleep(20);
        }
        return inbox;
    }

    private static StompFrameHandler collector(BlockingQueue<Map<String, Object>> inbox) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                inbox.add((Map<String, Object>) payload);
            }
        };
    }

    private static WebSocketStompClient stompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        return client;
    }

    private static WebSocketHttpHeaders handshake() {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin("http://localhost:3000");   // the only origin WebSocketConfig allows
        return headers;
    }

    private static RequestPostProcessor as(UUID user) {
        return jwt().jwt(j -> j.subject(user.toString()));
    }
}
