package com.systemdesign.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           E-Commerce Application Entry Point                 ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * SYSTEM DESIGN NOTE — Why these annotations matter at scale:
 *
 * @SpringBootApplication
 *   Combines @Configuration + @EnableAutoConfiguration + @ComponentScan.
 *   Auto-wires everything Spring finds in this package tree.
 *
 * @EnableCaching
 *   Activates the Spring Cache abstraction — allows @Cacheable, @CachePut,
 *   @CacheEvict on any Spring Bean. Backed by Redis in this project.
 *   PATTERN: Cache-Aside — load on miss, store on hit.
 *
 * @EnableJpaAuditing
 *   Automatically populates @CreatedDate / @LastModifiedDate on entities.
 *   At FAANG scale every record needs an audit trail.
 *
 * @EnableAsync
 *   Allows @Async methods — non-blocking execution on a thread pool.
 *   Java 21 Virtual Threads make this even cheaper (no OS thread per task).
 *
 * @EnableKafka
 *   Registers Kafka listener container factory for @KafkaListener methods.
 *   Powers the async event-driven pipeline (orders → inventory → notifications).
 */
@SpringBootApplication
@EnableCaching         // Redis Cache-Aside pattern
@EnableJpaAuditing     // Auto-fill createdAt / updatedAt
@EnableAsync           // Non-blocking @Async methods
@EnableKafka           // Kafka consumer listeners
public class EcommerceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcommerceApplication.class, args);
    }
}
