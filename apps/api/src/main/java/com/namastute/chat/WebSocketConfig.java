package com.namastute.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

// STOMP over WebSocket (Learning Notes 45): browsers connect to /ws, send to /app/..., subscribe to /topic/...
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuth auth;
    private final String webOrigin;

    WebSocketConfig(StompAuth auth, @Value("${namastute.web-origin}") String webOrigin) {
        this.auth = auth;
        this.webOrigin = webOrigin;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(webOrigin);   // only our web app may open a socket
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry broker) {
        // shortcut: the in-memory simple broker only works with ONE API instance; with two, switch to a broker
        // relay (RabbitMQ STOMP plugin) or Redis pub/sub (design 3.6)
        broker.enableSimpleBroker("/topic");
        broker.setApplicationDestinationPrefixes("/app");   // /app/... goes to @MessageMapping methods
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(auth);
    }
}
