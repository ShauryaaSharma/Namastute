package com.namastute.chat;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// One controller, two doors: HTTP for history, STOMP for sending
@RestController
class ChatController {

    record Outgoing(String body, String clientId) {}

    // lat/lng null = "I stopped sharing"; accuracy = the browser's "within N metres"
    record Position(Double lat, Double lng, Double accuracy) {}

    private final ChatService chat;

    ChatController(ChatService chat) {
        this.chat = chat;
    }

    // GET /api/v1/groups/{id}/messages?before=<id>  (older page)  or  ?after=<id>  (catch up after a reconnect)
    @GetMapping("/api/v1/groups/{id}/messages")
    List<ChatService.MessageView> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                          @RequestParam(required = false) Long before,
                                          @RequestParam(required = false) Long after) {
        return chat.history(UUID.fromString(jwt.getSubject()), id, before, after);
    }

    // STOMP: SEND /app/groups/{groupId}/chat  {"body": "...", "clientId": "..."}. The user was set at CONNECT (StompAuth).
    // shortcut: a refused message (not a member, empty text) is only logged; the browser checks text length itself
    @MessageMapping("/groups/{groupId}/chat")
    void send(@DestinationVariable UUID groupId, Outgoing message, Principal user) {
        chat.post(UUID.fromString(user.getName()), groupId, message.body(), message.clientId());
    }

    // STOMP: SEND /app/groups/{groupId}/location  {"lat": 28.55, "lng": 77.08, "accuracy": 35}  (Learning Notes 60)
    @MessageMapping("/groups/{groupId}/location")
    void location(@DestinationVariable UUID groupId, Position position, Principal user) {
        chat.shareLocation(UUID.fromString(user.getName()), groupId, position);
    }
}
