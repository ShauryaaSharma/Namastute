package com.namastute.chat;

import com.namastute.groups.GroupChanged;
import com.namastute.groups.GroupService;
import com.namastute.shared.ApiException;
import com.namastute.users.UserService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

// Group chat: save a message first, then broadcast it to everyone subscribed to the group's topic (Learning Notes 46.3)
@Service
class ChatService {

    static final int PAGE = 50;
    static final int MAX_LENGTH = 1000;   // same limit as the messages table's check constraint

    // firstName is null for system messages. clientId is only set on the live copy of your own message (deduplication).
    // you is only known for history; on live messages the sender's browser recognises its own clientId instead.
    record MessageView(long id, String kind, String firstName, String body, OffsetDateTime createdAt, String clientId,
                       boolean you) {}

    record Row(long id, UUID userId, String kind, String body, OffsetDateTime createdAt) {}

    record Updated(UUID groupId) {}

    private final JdbcClient db;
    private final GroupService groups;
    private final UserService users;
    private final SimpMessagingTemplate broker;

    ChatService(JdbcClient db, GroupService groups, UserService users, SimpMessagingTemplate broker) {
        this.db = db;
        this.groups = groups;
        this.users = users;
        this.broker = broker;
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
    }

    // Runs only after the groups module's transaction COMMITS, so nobody is told about a change that then rolled back.
    // fallbackExecution: also run when the change had no transaction (e.g. a decline).
    // shortcut: if the app crashes between the commit and this listener, the system message is lost;
    // Spring Modulith's event publication registry (an "outbox") would retry it.
    // REQUIRES_NEW: after a commit, the old transaction is finished; without a fresh one this insert would never be saved.
    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onGroupChanged(GroupChanged event) {
        if (event.systemMessage() != null) {
            Row row = db.sql("insert into messages (group_id, kind, body) values (?, 'system', ?) returning id, user_id, kind, body, created_at")
                    .params(event.groupId(), event.systemMessage()).query(Row.class).single();
            broker.convertAndSend(chatTopic(event.groupId()), view(row, Map.of(), null, false));
        }
        // "Something changed, refetch": no details, so nothing private can leak through this topic
        broker.convertAndSend("/topic/groups." + event.groupId() + ".updated", new Updated(event.groupId()));
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
