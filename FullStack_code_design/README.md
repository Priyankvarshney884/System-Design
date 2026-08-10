# 🏗️ System Design in Practice — Full-Stack E-Commerce Application

> **Goal:** Learn and demonstrate real-world System Design patterns the way FAANG engineers build them —  
> not just theory, but working code with clear comments you can revise and showcase in interviews.

---

## 📁 Repository Structure

```
FullStack_code_design/
├── README.md                  ← You are here
├── Backend/                   ← Java 21 + Spring Boot (REST API, Design Patterns, System Design)
└── FrontEnd/                  ← Angular (Component Architecture, State Management, Performance)
```

---

## 🎯 Why FAANG Companies Focus on System Design

| Concern | Why It Matters at Scale |
|---|---|
| **Scalability** | Handle millions of users without rewriting the app |
| **Availability** | 99.99% uptime — failure is always expected, design around it |
| **Performance** | Sub-100ms response times at scale (caching, CDN, indexing) |
| **Maintainability** | Large teams, microservices — clean design patterns prevent chaos |
| **Cost Efficiency** | Horizontal scaling, async processing, lazy loading reduce infra cost |

---

## 🛠️ Technology Stack

### Backend — Java 21 + Spring Ecosystem

| Layer | Technology | Purpose |
|---|---|---|
| Language | Java 21 (LTS) | Virtual threads (Project Loom), modern records, sealed classes |
| Framework | Spring Boot 3.x | Auto-configuration, production-ready microservices |
| API | Spring Web MVC / WebFlux | REST APIs + Reactive programming |
| Security | Spring Security + JWT | Authentication, Authorization, OAuth2 |
| ORM | Spring Data JPA + Hibernate | Database abstraction, query optimization |
| Messaging | Apache Kafka | Async event-driven architecture |
| Caching | Redis (Spring Cache + Lettuce) | Distributed cache, session store, rate limiting |
| Search | Elasticsearch | Full-text product search |
| API Gateway | Spring Cloud Gateway | Routing, rate limiting, load balancing |
| Service Discovery | Eureka (Spring Cloud Netflix) | Microservice registration and discovery |
| Circuit Breaker | Resilience4j | Fault tolerance, retry, fallback |
| Observability | Micrometer + Prometheus + Grafana | Metrics, tracing, alerting |
| Documentation | SpringDoc OpenAPI (Swagger) | API docs auto-generation |
| Testing | JUnit 5, Mockito, Testcontainers | Unit, Integration, Contract tests |
| Build | Maven / Gradle | Dependency management |

### Frontend — Angular

| Layer | Technology | Purpose |
|---|---|---|
| Framework | Angular 17+ (Standalone) | Component architecture, routing, DI |
| State Management | NgRx (Redux pattern) | Predictable state, time-travel debugging |
| HTTP | Angular HttpClient + Interceptors | API calls, auth headers, error handling |
| UI Components | Angular Material | Accessible, consistent design system |
| Lazy Loading | Angular Router | Code splitting, faster initial load |
| PWA | @angular/pwa | Offline capability, push notifications |
| Caching | HTTP Cache-Control + Service Worker | Browser-level caching strategy |
| Testing | Jest + Cypress | Unit tests + E2E tests |
| Build | Angular CLI + Webpack / esbuild | Optimized production bundles |

### Databases & Infrastructure

| Component | Technology | Use Case |
|---|---|---|
| **Primary DB** | PostgreSQL 16 | Transactional data — orders, users, inventory |
| **Document Store** | MongoDB | Product catalog, reviews (flexible schema) |
| **Cache Layer** | Redis 7 | Sessions, hot data, rate limiting, Pub/Sub |
| **Search Engine** | Elasticsearch 8 | Product search, autocomplete, faceted filters |
| **Message Broker** | Apache Kafka | Order events, inventory updates, notifications |
| **Object Storage** | MinIO (S3-compatible) | Product images, invoices, user uploads |
| **CDN** | Nginx / Cloudflare | Static assets, edge caching |
| **Container** | Docker + Docker Compose | Local dev, consistent environment |
| **Orchestration** | Kubernetes (K8s) | Production deployment, auto-scaling |

---

## 🛒 Application Domain — E-Commerce Platform

We build a **production-grade e-commerce application** end-to-end, covering every major system design challenge:

### Core Modules

```
E-Commerce Platform
├── 👤 User Service          — Registration, Login, JWT, OAuth2
├── 🛍️  Product Service       — Catalog, Search, Inventory
├── 🛒 Cart Service           — Session cart, persistent cart, Redis
├── 📦 Order Service          — Order lifecycle, saga pattern
├── 💳 Payment Service        — Payment processing, idempotency
├── 🔔 Notification Service   — Email/SMS/Push via Kafka events
├── 📊 Analytics Service      — Reporting, real-time dashboard
└── 🔍 Search Service         — Elasticsearch full-text + filters
```

---

## 📚 System Design Topics Covered

### 1. Scalability Patterns
- [x] Horizontal scaling with stateless services
- [ ] Database sharding & read replicas
- [ ] CQRS — Command Query Responsibility Segregation
- [ ] Event Sourcing with Kafka
- [ ] Consistent hashing for distributed caching

### 2. Caching Strategies
- [ ] **Cache-Aside** — Load on miss (product catalog)
- [ ] **Write-Through** — Sync write to DB + cache (inventory)
- [ ] **Write-Behind (Write-Back)** — Async DB write (analytics counters)
- [ ] **Read-Through** — Cache proxies DB reads
- [ ] Redis TTL, eviction policies, and cache invalidation
- [ ] HTTP caching (ETag, Cache-Control, CDN)

### 3. Design Patterns (GoF + Enterprise)

#### Creational
- [ ] **Builder** — Complex order/product construction
- [ ] **Factory Method** — Payment provider factory (Stripe, PayPal)
- [ ] **Abstract Factory** — Notification sender factory (Email, SMS, Push)
- [ ] **Singleton** — Application config, connection pools

#### Structural
- [ ] **Adapter** — Third-party payment gateway adapter
- [ ] **Decorator** — Layered caching decorator
- [ ] **Facade** — OrderFacade combining multiple services
- [ ] **Proxy** — Lazy loading, security proxy

#### Behavioral
- [ ] **Strategy** — Pricing strategy, discount rules, shipping calculator
- [ ] **Observer** — Inventory update → notify watchers
- [ ] **Chain of Responsibility** — Order validation pipeline
- [ ] **Command** — Undo/redo cart operations
- [ ] **Template Method** — Payment processing steps
- [ ] **State** — Order status state machine
- [ ] **Saga Pattern** — Distributed transaction across services

### 4. Database Design Patterns
- [ ] **Repository Pattern** — Abstraction over JPA
- [ ] **Unit of Work** — Transactional consistency
- [ ] **Optimistic Locking** — Concurrent inventory updates
- [ ] **Pagination** — Cursor-based vs offset pagination
- [ ] **Database Indexing** — Composite, partial, covering indexes
- [ ] **Connection Pooling** — HikariCP configuration

### 5. API Design Patterns
- [ ] **RESTful API** — Resource naming, HTTP verbs, status codes
- [ ] **Idempotency** — Safe retry for payments and orders
- [ ] **API Versioning** — URI vs Header versioning
- [ ] **Rate Limiting** — Token bucket via Redis
- [ ] **Pagination & Filtering** — Consistent API contract
- [ ] **HATEOAS** — Hypermedia-driven REST

### 6. Resilience & Fault Tolerance
- [ ] **Circuit Breaker** — Resilience4j with Spring Boot
- [ ] **Retry with Backoff** — Exponential backoff for transient failures
- [ ] **Bulkhead** — Isolate failures between services
- [ ] **Timeout** — Prevent cascading failures
- [ ] **Fallback** — Graceful degradation (cached data on DB failure)

### 7. Security Design
- [ ] **JWT Authentication** — Access + Refresh token rotation
- [ ] **OAuth2 / OIDC** — Google / GitHub SSO login
- [ ] **Role-Based Access Control (RBAC)** — User, Admin, Vendor roles
- [ ] **Input Validation & Sanitization** — Prevent injection attacks
- [ ] **Secrets Management** — Spring Vault / environment secrets

### 8. Async & Event-Driven Architecture
- [ ] **Kafka Producer/Consumer** — Order placed → inventory deducted
- [ ] **Dead Letter Queue (DLQ)** — Handle failed messages
- [ ] **Outbox Pattern** — Reliable event publishing with DB transactions
- [ ] **CQRS + Event Sourcing** — Separate read/write models

### 9. Frontend Architecture Patterns
- [ ] **NgRx Store** — Centralized state management (Redux pattern)
- [ ] **Smart & Dumb Components** — Container vs presentational
- [ ] **Lazy Loading Modules** — Route-level code splitting
- [ ] **Interceptors** — Auth token injection, error handling
- [ ] **Facade Pattern** — Angular service facades over NgRx
- [ ] **OnPush Change Detection** — Performance optimization
- [ ] **Virtual Scrolling** — Large product lists
- [ ] **Optimistic UI Updates** — Instant feedback before server response

### 10. Observability & Monitoring
- [ ] **Structured Logging** — JSON logs with correlation IDs
- [ ] **Distributed Tracing** — OpenTelemetry + Jaeger
- [ ] **Metrics** — Micrometer + Prometheus + Grafana dashboards
- [ ] **Health Checks** — Spring Actuator endpoints
- [ ] **Alerting** — Prometheus alerting rules

---

## 🗺️ High-Level Architecture Diagram

```
                        ┌─────────────────────────────────────┐
                        │           Client (Angular)           │
                        │  PWA · NgRx · Lazy Load · OnPush    │
                        └──────────────┬──────────────────────┘
                                       │ HTTPS
                        ┌──────────────▼──────────────────────┐
                        │          CDN / Nginx                 │
                        │    Static assets · Edge cache        │
                        └──────────────┬──────────────────────┘
                                       │
                        ┌──────────────▼──────────────────────┐
                        │       API Gateway (Spring Cloud)     │
                        │  Auth · Rate Limit · Routing         │
                        └──┬────────┬─────────┬───────────────┘
                           │        │         │
              ┌────────────▼─┐  ┌───▼──────┐ ┌▼─────────────┐
              │ User Service │  │ Product  │ │ Order Service │
              │  (JWT/OAuth) │  │ Service  │ │ (Saga Pattern)│
              └──────┬───────┘  └────┬─────┘ └──────┬────────┘
                     │               │               │
              ┌──────▼───────────────▼───────────────▼────────┐
              │                  Data Layer                     │
              │  PostgreSQL · MongoDB · Redis · Elasticsearch  │
              └─────────────────────┬──────────────────────────┘
                                    │
              ┌─────────────────────▼──────────────────────────┐
              │              Apache Kafka                        │
              │  Order Events · Inventory · Notifications       │
              └────────────────────────────────────────────────┘
```

---

## 🚀 Getting Started

### Prerequisites
```bash
# Required tools
java --version        # Java 21+
node --version        # Node 20+
docker --version      # Docker 24+
docker compose version # Docker Compose v2
```

### Run Infrastructure (Docker)
```bash
# Start all infrastructure services
docker compose up -d

# Services started:
# - PostgreSQL  → localhost:5432
# - MongoDB     → localhost:27017
# - Redis       → localhost:6379
# - Kafka       → localhost:9092
# - Elasticsearch → localhost:9200
# - Kibana      → localhost:5601
```

### Run Backend
```bash
cd Backend
./mvnw spring-boot:run
# API available at http://localhost:8080
# Swagger UI at  http://localhost:8080/swagger-ui.html
```

### Run Frontend
```bash
cd FrontEnd
npm install
ng serve
# App available at http://localhost:4200
```

---

## 📝 Interview Talking Points

Each implemented feature will have comments in the code explaining:
1. **What** the pattern is
2. **Why** we used it here
3. **Trade-offs** considered
4. **How it scales** — what happens at 10x, 100x traffic

---

## 📖 Learning Resources

| Topic | Resource |
|---|---|
| System Design Fundamentals | *Designing Data-Intensive Applications* — Martin Kleppmann |
| FAANG Interview Prep | *System Design Interview* — Alex Xu (Vol 1 & 2) |
| Design Patterns | *Design Patterns: Elements of Reusable OO Software* — GoF |
| Spring Boot | [spring.io/guides](https://spring.io/guides) |
| Angular Architecture | [angular.dev](https://angular.dev) |
| Redis Patterns | [redis.io/docs](https://redis.io/docs/) |
| Kafka | [kafka.apache.org/documentation](https://kafka.apache.org/documentation/) |

---

## 🗓️ Progress Tracker

| Module | Backend | Frontend | Design Pattern | Status |
|---|---|---|---|---|
| User Auth | ⬜ | ⬜ | Strategy + JWT | 🔲 Not Started |
| Product Catalog | ⬜ | ⬜ | Repository + Cache-Aside | 🔲 Not Started |
| Search | ⬜ | ⬜ | Facade + Elasticsearch | 🔲 Not Started |
| Shopping Cart | ⬜ | ⬜ | Command + Redis | 🔲 Not Started |
| Order Service | ⬜ | ⬜ | Saga + State Machine | 🔲 Not Started |
| Payment | ⬜ | ⬜ | Strategy + Idempotency | 🔲 Not Started |
| Notifications | ⬜ | ⬜ | Observer + Kafka | 🔲 Not Started |
| Rate Limiting | ⬜ | ⬜ | Token Bucket + Redis | 🔲 Not Started |
| Circuit Breaker | ⬜ | ⬜ | Resilience4j | 🔲 Not Started |
| Analytics | ⬜ | ⬜ | CQRS + Event Sourcing | 🔲 Not Started |

---

*Built for learning System Design through production-quality code — one pattern at a time.*
