# System Design & Distributed Systems — Part 6

## Caching: The Complete Guide

Caching is one of the most important performance patterns in system design. It stores frequently used data in a faster place so future requests can be served with lower latency and less work.

For a focused backend cache design checklist, see [`cache-design.md`](./cache-design.md).

At a high level:

```text
User Request
     ↓
Cache
     ↓
Database / Origin
     ↓
Response
```

The key idea is simple:

> Use faster memory for data that is expensive or slow to fetch repeatedly.

Caching improves user experience, reduces database and server load, saves cost, and helps systems survive traffic spikes. But it also introduces trade-offs around freshness, consistency, invalidation, capacity, and failure handling.

---

### ✍️ Handwritten Notes

- [View the handwritten study sheet](./handWrittenPdfs/caching_handwritten.png)

---

## 📚 Table of Contents

1. [Why Do We Need Caching?](#why-do-we-need-caching)
2. [Everyday Analogy](#everyday-analogy)
3. [Memory Hierarchy](#memory-hierarchy)
4. [Browser-Side Caching](#browser-side-caching)
5. [CDN Caching](#cdn-caching)
6. [Backend Caching](#backend-caching)
7. [Cache Reads and Writes](#cache-reads-and-writes)
8. [Cache Invalidation](#cache-invalidation)
9. [Time To Live](#time-to-live)
10. [Write Strategies](#write-strategies)
11. [Cache Eviction Policies](#cache-eviction-policies)
12. [Local vs Global Cache](#local-vs-global-cache)
13. [Single Node vs Distributed Cache](#single-node-vs-distributed-cache)
14. [What Should We Cache?](#what-should-we-cache)
15. [What Should We Avoid Caching?](#what-should-we-avoid-caching)
16. [Cache Consistency](#cache-consistency)
17. [Common Cache Failure Patterns](#common-cache-failure-patterns)
18. [Advanced Caching Patterns](#advanced-caching-patterns)
19. [Caching in System Design Interviews](#caching-in-system-design-interviews)
20. [Key Takeaways](#key-takeaways)

---

## Why Do We Need Caching?

Real systems often have a gap between two kinds of storage:

```text
Fast but small  ←→  Slow but large
```

Examples:

| Layer | Speed | Size | Cost | Example |
|---|---:|---:|---:|---|
| CPU cache / RAM | Very fast | Smaller | Costly | Registers, L1/L2/L3, memory |
| Disk / database | Slower | Larger | Cheaper | SSD, object store, database |

Without caching, every request may repeatedly hit slower systems:

```text
User → App → Database
User → App → Database
User → App → Database
```

With caching:

```text
User → App → Cache → Fast response
              ↓
        Database only when needed
```

Caching helps with:

- Lower response latency
- Fewer database reads
- Reduced backend CPU usage
- Lower network calls
- Better throughput
- Protection from repeated expensive computations
- Improved user experience
- Lower infrastructure cost

### Simple Example

Suppose a product page is viewed thousands of times per minute.

Without caching:

```text
Every request → Query product DB → Query inventory DB → Query reviews DB
```

With caching:

```text
First request  → DB queries → Store result in cache
Next requests  → Cache hit → Return quickly
```

---

## Everyday Analogy

### Making Tea

Imagine making tea.

Without preparation:

```text
Boil water → Add tea → Add milk → Serve
```

If many people ask for tea, repeating every step from scratch is slow.

With caching:

```text
Boil water once → Keep it in flask → Use when needed
```

The flask is the cache. It stores something prepared earlier so future work becomes faster.

### Human Brain

The human brain also behaves like a cache. It remembers frequently used information:

- Your home address
- Common passwords or PIN patterns
- Frequently used phone numbers
- Routes you travel often
- People you meet regularly

Frequently used information is quick to recall. Rarely used information may require searching notes, email, or memory.

---

## Memory Hierarchy

Caching exists because storage is hierarchical.

```text
CPU Registers
     ↓
CPU Cache: L1 / L2 / L3
     ↓
RAM
     ↓
Browser Cache
     ↓
CDN Edge
     ↓
Backend Cache
     ↓
Database / Disk / Origin
```

Usually:

```text
Closer to user/computation = faster, smaller, costlier
Farther from user/computation = slower, larger, cheaper
```

### Web Memory Hierarchy

```text
Browser Cache
     ↓
CDN
     ↓
Backend Cache
     ↓
Database
```

| Layer | Location | Speed | Common Data |
|---|---|---:|---|
| Browser cache | User device | Very fast | CSS, JS, images, HTTP responses |
| CDN cache | Edge location | Fast | Static assets, media, public content |
| Backend cache | Server side | Fast | Query results, sessions, computed data |
| Database | Origin storage | Slower | Source of truth |

The database is usually treated as the source of truth. Caches are derived copies.

---

## Browser-Side Caching

Browser caching stores data on the client device.

It reduces repeated network calls and speeds up repeat visits.

### Not Directly Accessible to Frontend JavaScript

Some browser caches are controlled by the browser and HTTP rules:

- Memory cache
- HTTP cache
- Disk cache
- Service worker cache, depending on implementation

### Accessible to Frontend JavaScript

Some browser storage is directly available to frontend code:

- Local Storage
- Session Storage
- Cookies
- IndexedDB
- Cache Storage API through service workers

### Browser Cache Types

| Type | Best For | Notes |
|---|---|---|
| Memory cache | Very short-lived assets | Cleared when tab/browser process ends |
| Disk HTTP cache | Static assets | Controlled using HTTP cache headers |
| Local Storage | Small non-sensitive values | Synchronous API; avoid large data |
| Session Storage | Per-tab temporary state | Cleared when tab closes |
| Cookies | Small values sent with requests | Useful for auth/session metadata, but size-limited |
| IndexedDB | Larger structured client data | Async and better for offline-capable apps |
| Service Worker Cache | Offline assets and controlled responses | Useful for PWAs |

### Common HTTP Cache Headers

```http
Cache-Control: public, max-age=31536000, immutable
ETag: "asset-version-123"
Last-Modified: Tue, 28 Jul 2026 10:00:00 GMT
```

Important headers:

| Header | Purpose |
|---|---|
| `Cache-Control` | Defines caching policy |
| `max-age` | Says how long response is fresh |
| `no-store` | Do not store response |
| `no-cache` | Store allowed, but revalidate before reuse |
| `private` | Store only in private user cache |
| `public` | Shared caches may store it |
| `ETag` | Version identifier for validation |
| `Last-Modified` | Timestamp-based validation |

### Browser Caching Example

For versioned static assets:

```text
app.9f3a1c.js
styles.18ab22.css
logo.772caa.png
```

You can cache aggressively because filename changes when content changes.

```http
Cache-Control: public, max-age=31536000, immutable
```

For HTML:

```http
Cache-Control: no-cache
```

The browser can store it, but should revalidate before using it.

---

## CDN Caching

A CDN, or Content Delivery Network, caches content near users at edge locations.

```text
User
 ↓
Nearest CDN Edge
 ↓
Origin Server
```

### What Do CDNs Cache?

CDNs commonly cache static content:

- Images
- CSS
- JavaScript
- Fonts
- Videos
- PDFs
- Public documents
- Static API responses, if configured carefully

CDNs usually do not act like primary databases. They cache files or responses, not normalized structured application data.

### What Problem Do CDNs Solve?

CDNs reduce:

- Latency
- Origin server load
- Bandwidth pressure on the origin
- Global delivery cost
- Impact of traffic spikes

### How Does a User Reach the Nearest CDN?

CDNs commonly use DNS-based routing and network-level routing techniques such as GeoDNS and Anycast.

```text
User → DNS → Nearest CDN IP → CDN Edge
```

The CDN provider chooses an edge location based on geography, network path, capacity, and availability.

### CDN Cache Miss

When the CDN does not have the requested file:

```text
User → CDN Edge → Origin Server
                  ↓
              CDN stores copy
                  ↓
User ← CDN Edge ← Response
```

### CDN Cache Hit

When the CDN already has a fresh copy:

```text
User → CDN Edge → Response
```

The origin server is not contacted.

### Can a CDN Become a Bottleneck?

Yes. CDN performance can degrade if:

- Cache hit ratio is low
- Origin server is slow
- Origin bandwidth is limited
- Cache keys are too fragmented
- Assets are not cacheable
- Edge location is overloaded
- Large dynamic responses bypass the cache

---

## Backend Caching

Backend caches sit near application servers.

Common tools:

- Redis
- Memcached
- In-memory maps
- Application-level caches
- Database query caches
- Distributed cache clusters

### Common Backend Cache Use Cases

- User sessions
- Authentication tokens
- Feature flags
- Product catalog data
- Expensive database query results
- Rate-limiting counters
- API responses
- Computed recommendations
- Leaderboards
- Frequently accessed configuration

### Basic Backend Flow

```text
Request
  ↓
Application
  ↓
Check Cache
  ├── Hit  → Return cached value
  └── Miss → Read DB → Store in cache → Return value
```

This common pattern is called cache-aside or lazy loading.

---

## Cache Reads and Writes

Caching introduces two important flows:

1. How reads happen
2. How writes keep cache and database aligned

### Read Flow

```text
User → App → Cache
              ├── Hit  → Return data
              └── Miss → DB → Store in cache → Return data
```

### Write Flow

```text
User → App → Database
              ↓
        Invalidate or update cache
```

The hard part is not reading from cache. The hard part is keeping cached data correct enough for the product's requirements.

---

## Cache Invalidation

Cache invalidation means removing or updating cached data when the source data changes.

```text
Database changes → Cached value may become stale
```

There are two broad approaches:

### Eviction

Eviction removes data when the cache is full or when data is no longer useful.

```text
Cache full → Remove old/less useful entry
```

Eviction is mostly about capacity management.

### Invalidation

Invalidation removes or refreshes data because the source data changed.

```text
Product updated in DB → Delete product cache key
```

Invalidation is mostly about correctness.

### Why Is Invalidation Hard?

Invalidation is difficult because:

- The same data may appear in multiple cache keys
- Updates can happen concurrently
- Distributed systems can fail between DB write and cache update
- Messages can be delayed or delivered twice
- Some cached values are derived from multiple data sources
- Global caches may replicate changes asynchronously

### Common Invalidation Strategies

| Strategy | How It Works | Best For |
|---|---|---|
| Delete on write | Remove cache key after DB update | Simple and common |
| Update on write | Write new value into cache | Data that is frequently read immediately |
| TTL only | Let entry expire naturally | Data that tolerates staleness |
| Event-based invalidation | Publish update event to clear caches | Distributed systems |
| Versioned keys | Change key version when data changes | Static or derived content |

---

## Time To Live

TTL, or Time To Live, is an expiry time for a cache entry.

```text
Set key = "A", TTL = 60 seconds
```

Timeline:

```text
0s                 30s                 60s
|------------------|------------------|
       Valid                 Expired
```

After expiry, the cache should no longer serve the value as fresh.

### Lazy Deletion

Many caches do not physically delete an expired entry exactly at the expiry time.

Instead:

```text
Entry expires → Deleted later in background or on access
```

This is called lazy deletion or passive expiration.

### What Consistency Does TTL Provide?

TTL usually provides eventual consistency.

```text
DB updated → Cache may remain stale → TTL expires → Fresh value loaded
```

Short TTLs reduce stale data but increase database load.

Long TTLs reduce database load but increase stale-data risk.

### TTL Selection

| Data Type | Suggested TTL |
|---|---|
| Static assets with versioned names | Very long |
| Product catalog | Minutes to hours |
| User profile | Seconds to minutes, depending on freshness needs |
| Stock price / live score | Very short or no cache |
| Authentication/session data | Based on security policy |
| Feature flags | Short enough for rollout safety |

There is no universal TTL. It depends on correctness requirements, traffic, update frequency, and database capacity.

---

## Write Strategies

Caching write strategy decides what happens when data changes.

### Cache-Aside

The application controls cache reads and writes.

Read:

```text
App → Cache
      ├── Hit  → Return
      └── Miss → DB → Cache → Return
```

Write:

```text
App → DB → Delete/Update Cache
```

**Advantages**

- Simple
- Works with many databases
- Cache stores only requested data
- Common in real systems

**Trade-Offs**

- First request after miss is slower
- App must handle cache logic
- Stale data possible if invalidation fails

### Write Around

Write around writes directly to the database and does not immediately update the cache.

```text
Write → Database
Read later → Cache miss → Load from DB
```

**Best for:** Data that is written often but not always read soon.

**Advantages**

- Avoids filling cache with rarely read data
- Keeps cache focused on read-hot entries

**Trade-Offs**

- First read after write may be slow
- Cache can be stale unless old key is invalidated

### Write Through

Write through writes to cache and database together.

```text
Write → Cache → Database
```

The write succeeds only when both are updated.

**Advantages**

- Cache is usually fresh
- Reads after writes are fast
- Simpler read path

**Trade-Offs**

- Higher write latency
- Cache may store data that is never read
- Cache/database failure handling becomes important

### Write Back / Write Behind

Write back writes to cache first and updates the database asynchronously.

```text
Write → Cache → Async queue → Database
```

**Advantages**

- Very fast writes
- Can batch database writes
- Good for write-heavy workloads

**Trade-Offs**

- Risk of data loss if cache fails before DB write
- More complex recovery
- Harder consistency guarantees
- Requires durable queues or replication for safety

### Strategy Comparison

| Strategy | Write Latency | Read Freshness | Complexity | Risk |
|---|---:|---:|---:|---|
| Cache-aside | Low/medium | Medium | Low | Stale cache if invalidation fails |
| Write around | Low | Medium | Low | Slow first read after write |
| Write through | Higher | High | Medium | Write path depends on cache |
| Write back | Very low | High from cache | High | Data loss if not durable |

---

## Cache Eviction Policies

Eviction decides what to remove when cache capacity is limited.

```text
Cache full → Choose entry to remove
```

### FIFO: First In, First Out

Removes the oldest inserted item.

```text
[1] [2] [3] [4] → remove 1
```

**Good:** Simple.

**Weakness:** Old does not always mean unused.

### LIFO: Last In, First Out

Removes the most recently inserted item.

```text
[1] [2] [3] [4] → remove 4
```

**Good:** Rarely used for general caching.

**Weakness:** New useful data can be removed too quickly.

### LRU: Least Recently Used

Removes the item that has not been accessed for the longest time.

```text
Recently used: 4, 2, 3, 1
Evict: 1
```

**Good:** Works well when recently used data is likely to be used again.

**Common in:** General-purpose caches.

### LFU: Least Frequently Used

Removes the item used the fewest number of times.

```text
Frequency:
A = 100
B = 2
C = 15
Evict B
```

**Good:** Useful when long-term popularity matters.

**Weakness:** Old popular data can stay even after it stops being useful unless aging is applied.

### Random Eviction

Removes a random entry.

**Good:** Very simple and sometimes surprisingly effective.

**Weakness:** May evict important hot data.

---

## Local vs Global Cache

### Local Cache

A local cache lives inside one application process.

```text
App Instance
 └── In-memory cache
```

**Advantages**

- Extremely fast
- No network call
- Simple to use

**Trade-Offs**

- Not shared across app instances
- Data can differ between instances
- Lost when process restarts
- Harder to invalidate globally

### Global / Distributed Cache

A global cache is shared across multiple app instances.

```text
App 1 ─┐
App 2 ─┼── Redis / Memcached Cluster
App 3 ─┘
```

**Advantages**

- Shared across applications
- More consistent than per-process cache
- Can scale independently
- Supports centralized invalidation

**Trade-Offs**

- Network overhead
- New failure dependency
- Requires capacity planning
- Needs replication and monitoring

### Common Pattern: Two-Level Cache

High-scale systems often combine both:

```text
App local cache → Distributed cache → Database
```

Local cache handles ultra-hot values. Distributed cache handles shared values. Database remains the source of truth.

---

## Single Node vs Distributed Cache

### Single Cache Node

```text
Apps → One Redis/Memcached node
```

**Advantages**

- Simple setup
- Easy debugging
- Good for small systems

**Trade-Offs**

- Single point of failure unless replicated
- Limited memory and throughput
- Vertical scaling limit

### Distributed Cache Cluster

```text
Apps → Cache Cluster
        ├── Node A
        ├── Node B
        └── Node C
```

Distributed caches improve:

- Availability
- Capacity
- Throughput
- Fault tolerance

But they introduce:

- Sharding
- Replication
- Rebalancing
- Network partitions
- More operational complexity

### Routing in Distributed Caches

Consistent hashing is commonly used to distribute cache keys.

```text
Key → Hash Function → Cache Node
```

Why consistent hashing helps:

- Balances keys across nodes
- Reduces data movement when nodes are added or removed
- Avoids remapping almost every key during cluster changes

---

## What Should We Cache?

Good cache candidates are:

- Read-heavy data
- Expensive database query results
- Expensive computed results
- Static or rarely changing content
- Public content shared by many users
- Data with acceptable staleness
- Data that is repeatedly requested
- Small enough to store efficiently

Examples:

| Data | Cache Suitability |
|---|---|
| Product details | High |
| Category pages | High |
| Static images | Very high |
| User session | High, with security care |
| Search suggestions | High |
| Analytics dashboard summary | Medium/high |
| Payment transaction state | Low unless carefully designed |

---

## What Should We Avoid Caching?

Avoid caching data when:

- It changes constantly
- Strong consistency is required
- It is extremely large
- It is rarely read
- It contains sensitive data without proper controls
- It is user-specific and hard to key correctly
- It would create more invalidation cost than read savings

### If Data Is Too Large

Options:

- Do not cache it
- Cache only summary metadata
- Cache smaller pages or chunks
- Cache compressed representation
- Cache IDs and fetch details separately
- Use CDN/object storage for large media

---

## Cache Consistency

Cache consistency describes how closely cache matches the source of truth.

### Strong Consistency

Every read sees the latest write.

This is difficult with caches because caches are separate copies.

### Eventual Consistency

Cache may briefly serve stale data but eventually becomes correct.

```text
DB updated → Cache stale → Invalidate or TTL expires → Cache fresh
```

Most caching systems are designed around eventual consistency.

### Read-Your-Writes Consistency

After a user updates data, that same user should see the new value.

Useful for:

- Profile updates
- Settings changes
- Cart updates
- Dashboard edits

Ways to support it:

- Update cache after write
- Bypass cache briefly after write
- Read from primary database after write
- Use user-specific cache keys carefully

---

## Common Cache Failure Patterns

### Cache Miss

A cache miss happens when the requested data is not in cache.

```text
Cache miss → Read DB → Store cache → Return
```

Misses are normal. Too many misses reduce cache value.

### Cache Stampede

Many requests miss the cache at the same time and all hit the database.

```text
Popular key expires
1000 requests miss
1000 database queries happen
```

Solutions:

- Request coalescing
- Distributed locks
- Soft TTL with background refresh
- Jittered TTLs
- Prewarming hot keys

### Cache Avalanche

Many keys expire at the same time, causing a sudden database load spike.

Solutions:

- Add random TTL jitter
- Stagger expiration times
- Refresh keys gradually
- Use circuit breakers and rate limits

### Cache Penetration

Requests repeatedly ask for data that does not exist, bypassing cache and hitting the database.

Example:

```text
GET /product/nonexistent-id
```

Solutions:

- Cache negative results briefly
- Validate IDs before database calls
- Bloom filters
- Rate limiting

### Hot Key Problem

One cache key receives extremely high traffic.

Solutions:

- Replicate hot key
- Split key by subcategory or region
- Use local cache in app instances
- Add request coalescing
- Precompute and distribute data

### Cache Server Failure

If cache is unavailable, the system may overload the database.

Solutions:

- Graceful degradation
- Timeouts
- Circuit breakers
- Fallback responses
- Replication
- Avoid treating cache as the only source of truth unless designed for it

---

## Advanced Caching Patterns

### Read-Through Cache

The cache itself loads data from the database on miss.

```text
App → Cache
      └── Miss → Cache loads DB
```

This hides loading logic from the application, but requires cache integration with the data source.

### Refresh-Ahead Cache

The cache refreshes popular entries before they expire.

```text
Key near expiry → Background refresh → New value ready
```

Useful for hot keys where misses are expensive.

### Soft TTL and Hard TTL

Soft TTL means the data is stale enough to refresh but still usable temporarily.

Hard TTL means the data must not be served after that point.

```text
0s             soft TTL             hard TTL
|---------------|--------------------|
Fresh           Serve stale + refresh Expired
```

This helps avoid stampedes.

### Cache Prewarming

Prewarming loads important keys before traffic arrives.

Examples:

- Load homepage data after deploy
- Load top products before sale starts
- Load feature flags during service startup

### Negative Caching

Negative caching stores "not found" results briefly.

```text
product:999 → NOT_FOUND, TTL = 30s
```

This protects the database from repeated invalid requests.

### Versioned Cache Keys

Instead of deleting old keys, create new keys when data changes.

```text
product:123:v1
product:123:v2
```

Useful for:

- Static assets
- Templates
- Derived data
- CDN content

Old keys naturally expire later.

### Cache Key Design

A cache key should uniquely represent the data and all important inputs.

Bad:

```text
products
```

Better:

```text
products:category=books:page=2:sort=price_asc:currency=INR
```

Include important dimensions:

- Entity ID
- Tenant ID
- User ID, if user-specific
- Locale
- Currency
- Pagination
- Filters
- Sort order
- Version
- Authorization scope, when relevant

Bad cache keys can cause data leaks or incorrect responses.

---

## Caching in System Design Interviews

When discussing caching in an interview, explain:

1. What data you will cache
2. Where the cache will live
3. Cache key format
4. TTL and invalidation strategy
5. Eviction policy
6. Consistency expectations
7. Failure handling
8. Metrics and monitoring

### Example: Product Page Cache

```text
User → CDN → App → Redis → Product DB
```

Cache layers:

- CDN caches images, CSS, JS, and public static files
- Redis caches product details and category summaries
- Browser caches static assets

Possible key:

```text
product:{productId}:locale:{locale}:currency:{currency}:v{version}
```

TTL:

```text
Product details: 5-30 minutes
Static assets: long TTL with versioned filenames
Inventory: short TTL or no cache depending on correctness needs
```

Invalidation:

```text
Product update event → Delete product cache key → Next read reloads from DB
```

### Questions to Ask

- Is the data read-heavy or write-heavy?
- How stale can the data be?
- Is the data public or user-specific?
- What happens if cache is down?
- Can a cache miss overload the database?
- What is the expected hit ratio?
- How large is each cached value?
- Does the cache need replication?

---

## Key Takeaways

- Caching stores frequently used data in faster memory.
- Cache close to the user or computation when possible.
- Browser, CDN, backend cache, and database form a hierarchy.
- Cache hits improve latency; cache misses still need a safe fallback path.
- TTL gives eventual consistency, not instant correctness.
- Eviction manages capacity; invalidation manages freshness.
- Write strategy determines latency, freshness, and failure risk.
- Cache key design is critical for correctness and security.
- Distributed caches need sharding, replication, routing, and monitoring.
- Caching is not just adding Redis. It is choosing the right memory at the right place.

---

## Quick Revision Sheet

```text
Fast → Slow

CPU Registers
→ CPU Cache
→ RAM
→ Browser Cache
→ CDN Edge
→ Backend Cache
→ Database
```

```text
Read Path:
Request → Cache hit → Return
Request → Cache miss → DB → Store cache → Return
```

```text
Write Path:
Write DB → Invalidate cache
Write DB → Update cache
Write cache → Async DB write
```

```text
Common Policies:
FIFO → Remove oldest inserted
LIFO → Remove newest inserted
LRU  → Remove least recently used
LFU  → Remove least frequently used
```

```text
Common Problems:
Stampede    → Many misses for same key
Avalanche   → Many keys expire together
Penetration → Missing data repeatedly hits DB
Hot key     → One key gets too much traffic
```
