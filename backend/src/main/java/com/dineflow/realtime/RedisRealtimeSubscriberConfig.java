package com.dineflow.realtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/**
 * Multi-replica Redis Pub/Sub subscriber active when dineflow.realtime.fanout=redis.
 * Receives durable outbox events published by any replica and delivers them to local WebSocket subscribers.
 */
@Configuration
@ConditionalOnProperty(name = "dineflow.realtime.fanout", havingValue = "redis")
public class RedisRealtimeSubscriberConfig {

    @Bean
    public RedisMessageListenerContainer realtimeRedisListenerContainer(
            RedisConnectionFactory connectionFactory,
            RealtimeEventDistributor distributor) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                (message, pattern) -> distributor.onRedisPubSubMessage(new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(RealtimeEventDistributor.REDIS_CHANNEL)
        );
        return container;
    }
}
