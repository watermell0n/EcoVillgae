package com.b201.ecovillage.realtime.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.b201.ecovillage.identity.config.WebClientProperties;
import com.b201.ecovillage.realtime.infrastructure.RealtimeTicketHandshakeInterceptor;
import com.b201.ecovillage.realtime.infrastructure.RealtimeWebSocketHandler;
import com.b201.ecovillage.realtime.infrastructure.RedisRealtimeEventBus;
import com.b201.ecovillage.realtime.infrastructure.RedisRealtimeEventSubscriber;

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
@EnableScheduling
@EnableConfigurationProperties(RealtimeProperties.class)
public class RealtimeConfiguration implements WebSocketConfigurer {

    private final RealtimeWebSocketHandler handler;
    private final RealtimeTicketHandshakeInterceptor handshakeInterceptor;
    private final WebClientProperties webClientProperties;

    public RealtimeConfiguration(
        RealtimeWebSocketHandler handler,
        RealtimeTicketHandshakeInterceptor handshakeInterceptor,
        WebClientProperties webClientProperties
    ) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.webClientProperties = webClientProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
            .addHandler(handler, "/ws/v1")
            .addInterceptors(handshakeInterceptor)
            .setAllowedOrigins(webClientProperties.allowedOrigins().toArray(String[]::new));
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "ecovillage.realtime",
        name = "redis-enabled",
        havingValue = "true"
    )
    RedisMessageListenerContainer realtimeRedisListenerContainer(
        RedisConnectionFactory connectionFactory,
        RedisRealtimeEventSubscriber subscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
            subscriber,
            new ChannelTopic(RedisRealtimeEventBus.TOPIC)
        );
        return container;
    }
}
