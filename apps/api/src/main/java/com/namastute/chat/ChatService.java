package com.namastute.chat;

import com.namastute.groups.GroupChanged;
import com.namastute.groups.GroupService;
import com.namastute.notifications.PushService;
import com.namastute.notifications.PushService.Push;
import com.namastute.shared.ApiException;
import com.namastute.users.UserService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Group chat: save a message first, then broadcast it to everyone subscribed to the group's topic (Learning Notes 46.3)
@Service
class ChatService {

    static final int PAGE = 50;
    static final int MAX_LENGTH = 1000;   // same limit as the messages table's check constraint

    // firstName is null for system messages. clientId is only set on the live copy of your own message (deduplication).
    // you is only known for history; on live messages the sender's browser recognises its own clientId instead.
    record MessageView(long id, @Schema(allowableValues = {"text", "system"}) String kind, @Schema(nullable = true) String firstName,
                       String body, OffsetDateTime createdAt, @Schema(nullable = true) String clientId, boolean you) {}

    record Row(long id, UUID userId, String kind, String body, OffsetDateTime createdAt) {}

    record Updated(UUID groupId) {}

    // tripId: the member's opaque handle (the group card knows whose it is); sharing false = their dot disappears
    record Located(UUID tripId, boolean sharing, Double lat, Double lng, Double accuracy) {}

    private final JdbcClient db;
    private final GroupService groups;
    private final UserService users;
    private final SimpMessagingTemplate broker;
    private final SimpUserRegistry connected;
    private final PushService push;

    ChatService(JdbcClient db, GroupService groups, UserService users, SimpMessagingTemplate broker,
                SimpUserRegistry connected, PushService push) {
        this.db = db;
        this.groups = groups;
        this.users = users;
        this.broker = broker;
        this.connected = connected;
        this.push = push;
    }

    // History, oldest first. Cursor pagination (Learning Notes 46.4): before = the page older than that id;
    // after = everything newer than that id (catching up after a reconnect).
    List<MessageView> history(UUID userId, UUID groupId, Long before, Long after) {
        requireMember(userId, groupId);
        List<Row> rows;
        if (after != null) {
            // shortcut: at most 200 missed messages are caught up; plenty for a cab of four
            rows = db.sql("select id, user_id, kind, body, created_at from messages where group_id = ? and id > ? order by id limit 200")
                    .params(groupId, after).query(Row.class).list();
        } else {
            rows = new ArrayList<>(db.sql("select id, user_id, kind, body, created_at from messages where group_id = ? and id < ? order by id desc limit ?")
                    .params(groupId, before == null ? Long.MAX_VALUE : before, PAGE).query(Row.class).list());
            Collections.reverse(rows);   // newest-first from the index, shown oldest-first
        }
        Map<UUID, String> names = users.firstNames(rows.stream().map(Row::userId).filter(Objects::nonNull).toList());
        return rows.stream().map(r -> view(r, names, null, userId.equals(r.userId()))).toList();
    }

    void post(UUID userId, UUID groupId, String body, String clientId) {
        requireMember(userId, groupId);
        String text = body == null ? "" : body.strip();
        if (text.isEmpty() || text.length() > MAX_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_MESSAGE", "A message must have 1 to 1000 characters");
        }
        Row row = db.sql("insert into messages (group_id, user_id, body) values (?, ?, ?) returning id, user_id, kind, body, created_at")
                .params(groupId, userId, text).query(Row.class).single();
        String echo = clientId != null && clientId.length() <= 64 ? clientId : null;
        broker.convertAndSend(chatTopic(groupId), view(row, users.firstNames(List.of(userId)), echo, false));
        // Only members without a live connection get a push; never the message text (Learning Notes 58.5, 58.6)
        // shortcut: "connected" = any open WebSocket, even a backgrounded tab the phone hasn't closed yet; track visibility if pushes go missing
        push.send(groups.memberIds(groupId).stream()
                        .filter(m -> !m.equals(userId) && connected.getUser(m.toString()) == null).toList(),
                new Push("New message in your cab group", "Open Namastute to read it", Duration.ofMinutes(10)));
    }

    // Live location at the pickup (Learning Notes 60): relayed to the group and forgotten; never saved or logged.
    // shortcut: no server-side rate limit (the browser throttles itself); add one if a member ever floods their group
    void shareLocation(UUID userId, UUID groupId, ChatController.Position p) {
        UUID tripId = groups.sharingTrip(groupId, userId).orElseThrow(() ->
                new ApiException(HttpStatus.CONFLICT, "NOT_PICKUP_TIME", "Location sharing is only open around pickup time"));
        boolean sharing = p.lat() != null;
        if (sharing && !isPosition(p)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_LOCATION", "That isn't a valid position");
        }
        broker.convertAndSend("/topic/groups." + groupId + ".location", sharing
                ? new Located(tripId, true, p.lat(), p.lng(), p.accuracy())
                : new Located(tripId, false, null, null, null));
    }

    // NaN and infinities fail these comparisons too
    static boolean isPosition(ChatController.Position p) {
        return p.lng() != null && Math.abs(p.lat()) <= 90 && Math.abs(p.lng()) <= 180
                && (p.accuracy() == null || (p.accuracy() >= 0 && p.accuracy() <= 100_000));
    }

    // Runs INSIDE the groups module's transaction (when there is one), on the same database connection: the system
    // message is saved together with the change, or not at all. Only the broadcast waits for the commit, so nobody is
    // told about a change that then rolled back. (An earlier version saved the message after the commit in a new
    // transaction: that needed a second connection per request and starved the pool under load. Learning Notes 51.)
    @EventListener
    void onGroupChanged(GroupChanged event) {
        MessageView message = event.systemMessage() == null ? null : view(
                db.sql("insert into messages (group_id, kind, body) values (?, 'system', ?) returning id, user_id, kind, body, created_at")
                        .params(event.groupId(), event.systemMessage()).query(Row.class).single(),
                Map.of(), null, false);
        Runnable broadcast = () -> {
            if (message != null) {
                broker.convertAndSend(chatTopic(event.groupId()), message);
            }
            // "Something changed, refetch": no details, so nothing private can leak through this topic
            broker.convertAndSend("/topic/groups." + event.groupId() + ".updated", new Updated(event.groupId()));
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcast.run();
                }
            });
        } else {
            broadcast.run();   // no transaction (e.g. a decline): the insert above is already committed
        }
    }

    private void requireMember(UUID userId, UUID groupId) {
        if (!groups.isActiveMember(groupId, userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "You're not in that group");
        }
    }

    static String chatTopic(UUID groupId) {
        return "/topic/groups." + groupId + ".chat";
    }

    private static MessageView view(Row r, Map<UUID, String> names, String clientId, boolean you) {
        return new MessageView(r.id(), r.kind(), r.userId() == null ? null : names.get(r.userId()), r.body(), r.createdAt(),
                clientId, you);
    }
}
