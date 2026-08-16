# Cache Patterns, Write/Read Strategies, and Consistency Models

> **Golden rule:** the database is the source of truth; a cache is a performance optimization. Choose the pattern that gives the required freshness at an acceptable cost and complexity.

## 1. Why cache?

A cache is a fast, in-memory store placed in front of a slower durable store such as a database. It reduces read latency, offloads the database, absorbs traffic spikes, improves throughput, and can lower infrastructure cost.

Without a cache, every request reaches the database. The database becomes a bottleneck as traffic grows, resulting in slower responses, lower scalability, and a worse user experience.

### A simple mental model

Think of the database as the **official filing cabinet**: durable and authoritative, but not the fastest place to look up the same document repeatedly. The cache is the **copy on a desk**: very fast to read, but it can become old and may disappear. Caching is therefore never only a speed decision; it is a decision about how much temporary staleness the product can safely tolerate.

For every cache design, answer these three questions first:

1. **Who owns the truth?** Usually the database or another durable service.
2. **How old may a read be?** For example, 5 minutes for a product description, but 0 seconds for a payment balance.
3. **What happens on cache failure?** A safe default is to fall back to the database, possibly with rate limits or degraded functionality.

## 2. Write strategies

### Write-through

On each write, synchronously write to both the cache and database. The cache is immediately current after a successful request.

```text
client -> cache -> database
```

- **Use when:** reads are frequent and strong consistency is important.
- **Pros:** cache is up to date; straightforward read path; strong consistency when the operation is atomic or correctly coordinated.
- **Cons:** slower writes because both systems are on the critical path; may populate unused keys.
- **Examples:** account balances, inventory counts, critical profile settings.

**Example:** when an item is purchased, decrease inventory in the database and update/invalidate the `inventory:item-42` cache entry before confirming the purchase. A later customer does not see an old “in stock” value.

### Write-back (write-behind)

Write to the cache first, then persist the change to the database asynchronously in a batch or later.

```text
client -> cache -> database (async)
```

- **Use when:** write throughput matters and a small durability/freshness risk is acceptable.
- **Pros:** very fast writes; batching can reduce database load.
- **Cons:** data can be lost if the cache fails before persistence; recovery, retries, ordering, and backpressure are complex.
- **Examples:** telemetry, counters, logs, chat activity, non-critical events.

**Example:** a page-view counter may first increment in Redis. A background worker periodically writes the accumulated count to the database. The screen remains fast, but a Redis crash could lose increments not yet flushed.

### Write-around

Bypass the cache and write directly to the database. Load the key into cache only when a later read needs it.

```text
client -> database
later read -> cache miss -> database -> cache
```

- **Use when:** written data is rarely read soon afterward, or write volume is high.
- **Pros:** avoids filling cache with cold data; no stale cache entry caused by the write itself.
- **Cons:** first read is slower and increases database load.
- **Examples:** product catalog updates, archival records, bulk imports.

**Example:** an administrator imports 100,000 products overnight. Caching every product immediately would evict useful hot data, so the import writes only to the database. Products enter the cache only if customers later view them.

### Write-through plus write-back

Update cache and database synchronously, while also using asynchronous write-back/batching where appropriate. This is a high-performance but operationally complex approach.

- **Use when:** you need very high write performance *and* strong controls around persistence.
- **Watch for:** duplicate writes, idempotency, ordering, retries, and durable queues.

## 3. Read strategies

### Cache-aside (lazy loading)

The application owns cache misses. It reads cache first; on a miss, it reads the database, stores the result in cache, and returns it.

```text
client -> application -> cache
                         └─ miss -> database -> cache
```

- **Use when:** general-purpose caching; this is the most common pattern.
- **Pros:** only requested data is cached; cache failures do not make the database inaccessible.
- **Cons:** first request for a key is slow; concurrent misses can cause a cache stampede.
- **Mitigations:** request coalescing/single-flight, TTL jitter, stale-while-revalidate, locking, and prewarming hot keys.

**Example flow:**

1. Request asks for `product:42`.
2. The application looks in cache and misses.
3. It reads product 42 from the database.
4. It stores the result with a TTL, then returns it.
5. The next request is served from cache.

The danger is a **cache stampede**: if a popular key expires and 10,000 requests all miss together, they can all query the database. Use a single-flight lock so one request refills the key while the others wait briefly or receive a still-valid stale value.

### Read-through

The cache itself retrieves data from the database on a miss, then returns it to the application.

- **Use when:** your cache platform/library supports a stable loader and you want a simpler client interface.
- **Pros:** application always reads one place; cache-loading behavior is centralized.
- **Cons:** loader configuration is more coupled to the cache; still needs stampede protection.

### Refresh-ahead

Refresh popular or predictable keys before their TTL expires.

- **Use when:** access patterns are predictable and cache-miss latency is unacceptable.
- **Pros:** fewer cache misses; stable latency for hot keys.
- **Cons:** refreshes keys that may not be used; inaccurate predictions add database load.
- **Examples:** leaderboards, dashboards, trending content, scheduled reports.

**Example:** if a dashboard is opened every minute, refresh its cached aggregate at 55 seconds instead of waiting for the next visitor to experience a miss.

### Read-around

Bypass the cache and read directly from the database.

- **Use when:** almost never; typically only where caching provides no useful benefit.
- **Pros:** always reads the current database value.
- **Cons:** no cache benefit and higher database load.

## 4. Consistency models

| Model | Guarantee | Typical use |
| --- | --- | --- |
| Strong consistency | Every read returns the most recently committed write. | balances, inventory |
| Eventual consistency | Replicas/cache converge after a delay. | social feeds, likes, analytics |
| Read-your-writes (RYW) | A client sees its own successful writes on later reads. | profile edits, shopping carts |
| Monotonic reads | Once a client has seen a version, it will not later see an older one. | scrolling feeds |
| Causal consistency | If event B depends on event A, observers see A before B. | collaborative workflows, microservices |

### Achieving read-your-writes

After a user writes, route that user’s following reads so they cannot land on a stale replica/cache. Common techniques:

- sticky sessions or session affinity;
- read from the primary/database for a short post-write window;
- version numbers, session tokens, or a `min-version` request constraint;
- write-through cache updates or targeted invalidation before responding.

Example: after a user updates their profile, return the new version and ensure subsequent profile reads for that session are served from a source at least as new as that version.

### Freshness is a product requirement

Do not say “the whole system needs strong consistency.” Identify what each screen or action needs:

| Feature | Usually acceptable freshness | Why |
| --- | --- | --- |
| Bank transfer confirmation | immediate | incorrect values can cause financial harm |
| Shopping-cart update for the same user | read-your-writes | the user expects to see their own change |
| Likes and view counts | seconds or minutes | small delays rarely change the user decision |
| News-feed ranking | eventual | availability and scale are usually more valuable than instant convergence |
| Product catalog text | minutes or hours | stale descriptions are normally low risk |

## 5. Cache invalidation

Invalidation keeps cache entries acceptably correct. The hard part is handling races: an old read can refill a key after an update unless versioning or ordering safeguards are used.

### TTL (time to live)

Expire data after a fixed period and reload on a later request.

- Simple and broadly useful, but data can remain stale until expiration.
- Add random **jitter** to prevent many keys expiring simultaneously.

### Manual invalidation

Delete or update relevant cache keys whenever the source data changes.

- Good for critical data.
- Requires a correct mapping from database entities to all affected keys.

### Versioned keys / values

Include a version in the cached value or key, for example `user:123:v42`. Ignore values older than the known latest version.

- Helps prevent stale refills and supports distributed writers.
- Requires version generation and cleanup of old entries.

### Event-based invalidation

Publish change events through a broker (for example, a pub/sub topic). Consumers invalidate or update relevant cache entries.

- Scales well across services and cache nodes.
- Make consumers idempotent, durable where needed, and able to recover missed events.

**Important reliability pattern:** save the database change and a matching event record in one transaction (often called an *outbox*). A worker then publishes the event reliably. Without this, the database write might succeed while the “invalidate cache” event is lost.

## 6. Real-time data: keeping screens live

Real-time data means clients receive updates shortly after something changes, rather than discovering the change only when they poll or reload. It is useful for chat, live dashboards, delivery tracking, collaborative documents, notifications, auctions, and game state.

### The end-to-end flow

```text
writer -> API/service -> database -> durable change event -> stream gateway -> connected clients
                                \-> cache invalidation/update
```

The database change is still the authoritative action. The event informs caches and connected clients that a newer version exists. A live message is **not** a substitute for durable storage: clients disconnect, messages can be duplicated, and messages may arrive out of order.

### Delivery options

| Option | Best for | Trade-off |
| --- | --- | --- |
| Polling | simple, low-frequency updates | easy but wasteful and not instant |
| Long polling | notifications where WebSockets are unavailable | server holds requests open; more overhead |
| Server-Sent Events (SSE) | one-way server-to-browser streams | simple browser support; client cannot send on the same stream |
| WebSockets | bidirectional chat, collaboration, games | persistent connections need connection management and scaling |
| Pub/sub or event stream | service-to-service distribution | clients need a separate gateway or consumer |

### A safe real-time recipe

1. **Write and persist first.** Confirm an update only after the source of truth accepts it.
2. **Assign a version or sequence number** per entity, such as `document-7 version 105`.
3. **Publish a durable event** after the write, preferably via an outbox.
4. **Update or invalidate the cache** using the event. Version checks prevent an older event from overwriting newer data.
5. **Push the event to subscribed clients.** Send either the changed data or an “entity changed” notice that prompts a fetch.
6. **Recover after reconnect.** The client supplies the last event/version it saw; the server replays missed events or returns a fresh snapshot.

### Ordering, duplication, and missed messages

Distributed systems cannot assume “one message, once, in order.” Design clients and consumers so that they can process an event more than once and reject old versions.

```text
Received: order-9 version 12  -> apply
Received: order-9 version 11  -> ignore (older than current version)
Received: order-9 version 12  -> safe to process again (duplicate)
```

Keep ordering only where it matters, usually per user, document, order, or chat room. Global ordering is expensive and usually unnecessary.

### Cache and real-time updates

- For **critical values**, update/invalidate cache synchronously or serve reads from the primary during the short post-write window.
- For **fast-moving feeds/counters**, publish updates asynchronously and accept eventual consistency.
- For **many subscribers**, cache the current snapshot, stream small incremental events, and let a reconnecting client fetch the snapshot again.
- Use TTL as a safety net even with event invalidation, because an event can be delayed or missed.

**Chat example:** persist message `m101` to the chat store, publish `MessageCreated(room=7, sequence=101)`, update the room’s recent-message cache, then push it over WebSocket. A reconnecting client requests all messages after sequence 97, or fetches the latest room snapshot.

## 7. Choosing a pattern

| Situation | Recommended approach |
| --- | --- |
| High write rate; small loss risk is acceptable | Write-back |
| Critical writes; correctness must be immediate | Write-through |
| Writes are rarely read soon after | Write-around + cache-aside |
| Predictable hot reads | Refresh-ahead |
| User must immediately see an update | RYW with a session/version mechanism |
| Default application caching | Cache-aside with TTL and invalidation |

## 8. Practical design checklist

1. Define the data owner and keep the database as the durable source of truth.
2. State the freshness requirement per use case, not globally.
3. Select a read pattern and write pattern independently; they often differ.
4. Define cache keys, TTLs, invalidation triggers, and versioning.
5. Plan for cache misses, stampedes, cache outages, and stale reads.
6. For async writes/events, define retry, deduplication, ordering, dead-lettering, and monitoring behavior.
7. Measure hit rate, eviction rate, cache latency, database offload, stale-read rate, and queue lag.
8. For real-time features, monitor connected clients, reconnect rate, event-delivery lag, replay failures, and out-of-order events.

## 9. Interview-ready comparisons

- **Write-through vs write-back:** write-through favors correctness and immediate persistence; write-back favors write latency and database efficiency but risks data loss before flush.
- **Cache-aside vs read-through:** cache-aside puts miss handling in the application; read-through delegates it to the cache layer.
- **TTL vs explicit invalidation:** TTL is simple but permits bounded staleness; explicit invalidation is fresher but harder to implement correctly.
- **Strong vs eventual consistency:** strong consistency prevents stale reads at higher latency/availability cost; eventual consistency improves scale and availability while accepting temporary divergence.
- **Polling vs WebSockets:** polling is easiest for infrequent changes; WebSockets are better for low-latency bidirectional interaction, but require persistent-connection infrastructure.
- **Cache invalidation vs client push:** invalidation keeps the server-side fast read correct; client push updates the user interface. Real-time systems commonly need both.

## 10. Core takeaway

Think in trade-offs: **writes** choose between durability/consistency and speed; **reads** choose between latency and freshness; **invalidation** determines how long stale data can survive. Design the cache policy around business harm: serve an old social-feed item if necessary, but do not serve an old bank balance.
