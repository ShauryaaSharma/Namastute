package com.namastute.chat;

import com.namastute.groups.GroupService;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

// The checkpoint every incoming STOMP frame passes (Learning Notes 46.1–46.2). Throwing here refuses the frame;
// Spring then sends the client an ERROR frame and closes the connection.
@Component
class StompAuth implements ChannelInterceptor {

    private static final Pattern GROUP_TOPIC = Pattern.compile("/topic/groups\\.([0-9a-f-]{36})\\.(chat|updated)");

    private final JwtDecoder jwts;
    private final GroupService groups;

    StompAuth(JwtDecoder jwts, GroupService groups) {
        this.jwts = jwts;
        this.groups = groups;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (stomp == null || stomp.getCommand() == null) {
            return message;
        }
        switch (stomp.getCommand()) {
            case CONNECT -> {
                // Browsers can't put headers on the WebSocket handshake, so the access token comes in the CONNECT frame
                String auth = stomp.getFirstNativeHeader("Authorization");
                if (auth == null || !auth.startsWith("Bearer ")) {
                    throw new AccessDeniedException("Log in first");
                }
                // Same checks as HTTP (signature, expiry, issuer); throws if the token is bad.
                // The user set here is remembered for every later frame on this connection.
                stomp.setUser(new JwtAuthenticationToken(jwts.decode(auth.substring(7))));
            }
            case SUBSCRIBE -> {
                // Never trust a client-chosen destination: only your own group's topics
                Matcher topic = GROUP_TOPIC.matcher(String.valueOf(stomp.getDestination()));
                if (stomp.getUser() == null || !topic.matches()
                        || !groups.isActiveMember(UUID.fromString(topic.group(1)), UUID.fromString(stomp.getUser().getName()))) {
                    throw new AccessDeniedException("Not your group");
                }
            }
            case SEND -> {
                // Clients may only send to our controller (/app/...), never straight onto a broker topic,
                // or they could post fake messages that skip saving and membership checks
                if (stomp.getUser() == null || !String.valueOf(stomp.getDestination()).startsWith("/app/")) {
                    throw new AccessDeniedException("Not allowed");
                }
            }
            default -> { }
        }
        return message;
    }
}
