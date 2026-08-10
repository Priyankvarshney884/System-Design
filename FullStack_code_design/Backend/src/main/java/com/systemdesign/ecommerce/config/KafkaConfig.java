package com.systemdesign.ecommerce.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           Kafka Configuration — Topics & Producer            ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * SYSTEM DESIGN: Why Kafka for e-commerce?
 *
 *   Scenario: User places an order. What needs to happen?
 *     1. Reserve inventory
 *     2. Process payment
 *     3. Send confirmation email
 *     4. Update analytics dashboard
 *     5. Notify warehouse for shipping
 *
 *   WITHOUT Kafka (synchronous):
 *     Order API calls all 5 services in sequence.
 *     If email service is down → entire order fails.
 *     Response time = sum of all service times.
 *
 *   WITH Kafka (async event-driven):
 *     Order API publishes ONE event to Kafka → returns 202 Accepted immediately.
 *     Each downstream service consumes the event independently.
 *     If email service is down → it catches up when it recovers (Kafka retains messages).
 *     Response time = just the order creation time.
 *
 * DESIGN PATTERN: Observer / Publish-Subscribe
 *   OrderPlacedEvent is published ONCE.
 *   Multiple consumers (inventory, payment, notification) subscribe independently.
 *   Adding a new consumer never touches the Order Service.
 *
 * Topic naming convention: <domain>.<entity>.<event>
 *   e.g., order.placed, inventory.updated, notification.email.send
 */
@Configuration
public class KafkaConfig {

    // Topic name constants — used by producers and consumers
    public static final String TOPIC_ORDER_PLACED       = "order.placed";
    public static final String TOPIC_ORDER_CANCELLED    = "order.cancelled";
    public static final String TOPIC_INVENTORY_UPDATED  = "inventory.updated";
    public static final String TOPIC_PAYMENT_PROCESSED  = "payment.processed";
    public static final String TOPIC_NOTIFICATION_EMAIL = "notification.email";
    public static final String TOPIC_NOTIFICATION_SMS   = "notification.sms";

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // ── Topic Declarations ───────────────────────────────────────

    /**
     * Spring auto-creates topics with these configs on startup.
     *
     * partitions(3):
     *   3 partitions = 3 consumers can process in parallel.
     *   Tune based on expected throughput. More partitions = more parallelism.
     *
     * replicas(1):
     *   1 = dev/local. In production use 3 for fault tolerance
     *   (survives loss of 2 out of 3 brokers).
     */
    @Bean
    public NewTopic orderPlacedTopic() {
        return TopicBuilder.name(TOPIC_ORDER_PLACED)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderCancelledTopic() {
        return TopicBuilder.name(TOPIC_ORDER_CANCELLED)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic inventoryUpdatedTopic() {
        return TopicBuilder.name(TOPIC_INVENTORY_UPDATED)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentProcessedTopic() {
        return TopicBuilder.name(TOPIC_PAYMENT_PROCESSED)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic notificationEmailTopic() {
        return TopicBuilder.name(TOPIC_NOTIFICATION_EMAIL)
                .partitions(2).replicas(1).build();
    }

    @Bean
    public NewTopic notificationSmsTopic() {
        return TopicBuilder.name(TOPIC_NOTIFICATION_SMS)
                .partitions(2).replicas(1).build();
    }

    // ── Producer Factory ─────────────────────────────────────────

    /**
     * KafkaTemplate<String, Object>:
     *   - Key: String (e.g. orderId — same key = same partition = ordered processing)
     *   - Value: Any Java object serialized as JSON
     *
     * SYSTEM DESIGN: Why use orderId as the message key?
     *   Kafka guarantees ordering WITHIN a partition.
     *   Same orderId → same partition → events for one order are always processed in order.
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Idempotent producer: prevents duplicate messages on retry
        // SYSTEM DESIGN: critical for payment events — never charge twice
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.ACKS_CONFIG, "all");  // wait for all replicas
        config.put(ProducerConfig.RETRIES_CONFIG, 3);

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
