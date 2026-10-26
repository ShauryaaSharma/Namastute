package com.namastute.groups;

import com.namastute.trips.OpenGroups;
import com.namastute.users.UserService;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

// The groups module's plug ("adapter") for the trips module's OpenGroups port: matching gets open groups
// without trips ever depending on groups (Learning Notes 43.3)
@Component
class OpenGroupsQuery implements OpenGroups {

    record Row(UUID groupId, boolean womenOnly, UUID tripId) {}

    private final JdbcClient db;

    OpenGroupsQuery(JdbcClient db) {
        this.db = db;
    }

    @Override
    public List<OpenGroup> near(UUID userId, String terminalId, Instant exitAt, boolean womenOnly) {
        List<Row> rows = db.sql("""
                        select g.id as group_id, g.women_only, m.trip_id from groups g
                        join group_members m on m.group_id = g.id and m.status = 'accepted'
                        where g.status = 'open' and g.terminal_id = :terminal
                          and g.meet_at between :exitAt - interval '30 minutes' and :exitAt + interval '30 minutes'
                          and not exists (select 1 from group_members x join blocks b
                                            on (b.blocker_id = x.user_id and b.blocked_id = :user)
                                            or (b.blocker_id = :user and b.blocked_id = x.user_id)
                                          where x.group_id = g.id and x.status = 'accepted')
                          and (not g.women_only or :user in %s)
                          and (not :womenOnly or g.women_only)
                        order by g.id, m.created_at, m.user_id""".formatted(UserService.WOMEN_ONLY_USERS))
                .param("terminal", terminalId)
                .param("exitAt", exitAt.atOffset(ZoneOffset.UTC))
                .param("user", userId)
                .param("womenOnly", womenOnly)
                .query(Row.class).list();
        // One entry per group, members in row order (LinkedHashMap keeps insertion order)
        return rows.stream().collect(Collectors.groupingBy(Row::groupId, LinkedHashMap::new, Collectors.toList())).values().stream()
                .map(g -> new OpenGroup(g.get(0).womenOnly(), g.stream().map(Row::tripId).toList())).toList();
    }
}
