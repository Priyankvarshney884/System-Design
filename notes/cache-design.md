# System Design & Distributed Systems

## Backend Cache Design: Learning Notes

Caching is not just "add Redis." A good cache design answers five questions:

```text
What should be cached?
Where should it be cached?
How should cache keys be designed?
How fresh does the data need to be?
What happens when the cache is full or unavailable?
```

At a high level:

```text
Request
  ↓
Application
  ↓
Cache
  ├── Hit  -> Return fast
  └── Miss -> Fetch origin -> Store in cache -> Return
```

The goal is to reduce repeated expensive work while keeping correctness, freshness, and failure behavior under control.

---

## Handwritten Notes

- [High-resolution cache design PNG](../assets/handwritten/cache-design/cache_design_high_res.png)
- [High-resolution cache design PDF](../assets/handwritten/cache-design/cache_design_high_res.pdf)
- [Original cache design PDF](../assets/handwritten/cache-design/cache_design_notes.pdf)

---

## Table of Contents

1. [Do We Always Want to Cache?](#do-we-always-want-to-cache)
2. [Five-Step Cache Design Process](#five-step-cache-design-process)
3. [Cache-Aside Pattern](#cache-aside-pattern)
4. [Local vs Global Cache](#local-vs-global-cache)
5. [Single Node vs Distributed Cache](#single-node-vs-distributed-cache)
6. [TTL and Freshness](#ttl-and-freshness)
7. [Invalidation](#invalidation)
8. [Write Strategies](#write-strategies)
9. [Eviction](#eviction)
10. [Cache Failure Patterns](#cache-failure-patterns)
11. [Case Study: Code Judge Testcases](#case-study-code-judge-testcases)
12. [Case Study: Contest Leaderboard](#case-study-contest-leaderboard)
13. [Design Checklist](#design-checklist)
14. [Official Learning Sources](#official-learning-sources)

---

## Do We Always Want to Cache?

No.

Caching is useful when reuse is high and the cost of fetching or computing data repeatedly is significant. It is risky when correctness depends on always reading the latest data.

Good cache candidates:

- Read-heavy data
- Expensive query results
- Expensive computed results
- Static or rarely changing content
- Public data shared by many users
- Data that can tolerate bounded staleness

Poor cache candidates:

- Data that changes constantly
- Data that is rarely read
- Data requiring strict freshness
- Very large values with low reuse
- Sensitive user-specific data without careful keys and access control
- Data whose invalidation cost is higher than the read savings

Key learning point from official docs: cache-aside is most useful for repeated access to data, but cached data cannot automatically stay perfectly consistent with the source of truth.

---

## Five-Step Cache Design Process

### Step 1: Identify Read-Heavy Data

Ask:

- Is this data requested repeatedly?
- Is the origin fetch slow or expensive?
- Is the data shared across many users?
- What is the expected cache hit ratio?

Example:

```text
Product details -> good candidate
Payment transaction state -> usually risky
```

### Step 2: Choose Cache Location

Common locations:

- Browser cache
- CDN cache
- Local application memory
- Shared Redis or Memcached cache
- Database or storage-layer cache

Choose based on where latency is being paid.

```text
Closer to user/computation = faster
Shared cache = easier consistency across app instances
```

### Step 3: Design Cache Keys

A cache key must include every input that changes the response.

Bad:

```text
ranklist
```

Better:

```text
ranklist:{contestId}:page:{page}:filter:{filterHash}:v{version}
```

Include dimensions such as:

- Entity ID
- Tenant ID
- User ID when user-specific
- Locale
- Currency
- Pagination
- Filters
- Sort order
- Authorization scope
- Version

Bad cache keys can cause stale data, incorrect responses, or data leaks.

### Step 4: Pick TTL and Invalidation

TTL controls how long the cache may serve a value.

Invalidation handles data changes.

```text
TTL -> freshness window
Invalidation -> correctness after updates
```

Short TTL:

- Fresher data
- More cache misses
- More origin load

Long TTL:

- Better hit ratio
- Lower origin load
- Higher stale-data risk

### Step 5: Plan Eviction and Failure Behavior

Ask:

- What gets removed when cache memory is full?
- Does the app fail open or fail closed if cache is down?
- Can cache misses overload the database?
- Do we need replication?
- Do we need request coalescing or locks for hot keys?

---

## Cache-Aside Pattern

Cache-aside, also called lazy loading, is one of the most common backend caching patterns.

Read flow:

```text
App -> Cache
       ├── Hit  -> Return cached value
       └── Miss -> Read DB -> Store in cache -> Return
```

Write flow:

```text
App -> Database -> Delete or update cache key
```

Why teams use it:

- Simple to implement
- Only requested data enters the cache
- Works with many databases
- Fits read-heavy workloads

Trade-offs:

- First read after a miss is slower
- Cache can briefly be stale
- Application owns cache logic
- Popular-key expiration can cause a stampede

Important ordering:

```text
Update database first -> then invalidate cache
```

Microsoft's Cache-Aside guidance calls out this ordering because deleting the cache before updating the data store can let an old database value be read and cached again.

---

## Local vs Global Cache

### Local Cache

A local cache lives inside one application process.

```text
App Instance
  └── In-memory cache
```

Pros:

- Fastest option
- No network call
- Simple for ultra-hot values

Cons:

- Not shared across instances
- Can become inconsistent across servers
- Lost on restart
- Harder to invalidate globally

### Global Cache

A global cache is shared by multiple application instances.

```text
App 1 ─┐
App 2 ─┼── Redis / Memcached
App 3 ─┘
```

Pros:

- Shared working set
- Better consistency across app instances
- Centralized invalidation
- Independent scaling

Cons:

- Network hop
- New dependency
- Needs monitoring, capacity planning, and failover

Common production design:

```text
Local cache -> Distributed cache -> Database
```

---

## Single Node vs Distributed Cache

### Single Cache Node

```text
Apps -> One Redis/Memcached node
```

Good for:

- Small systems
- Simple prototypes
- Low traffic

Problems:

- Single point of failure without replication
- Limited memory
- Limited throughput

### Distributed Cache

```text
Apps -> Cache Cluster
        ├── Node 1
        ├── Node 2
        └── Node 3
```

Distributed caches need:

- Sharding
- Replication
- Health checks
- Client-side or proxy routing
- Rebalancing
- Observability

Consistent hashing is often used so keys do not all move when cache nodes are added or removed.

---

## TTL and Freshness

TTL means Time To Live.

```text
cache.set(key, value, ttl = 300 seconds)
```

After TTL expires, the key is treated as missing or stale.

TTL does not guarantee perfect freshness. It gives bounded staleness.

Use shorter TTLs for:

- Frequently changing data
- User-visible mutable state
- Live contest ranklists
- Feature flags during rollout

Use longer TTLs for:

- Static assets
- Versioned files
- Public catalog data
- Expensive computed summaries that change rarely

Useful patterns:

- Add TTL jitter to avoid many keys expiring together.
- Use soft TTL for background refresh.
- Use hard TTL for maximum allowed staleness.
- Prewarm important keys before expected traffic spikes.

---

## Invalidation

Invalidation removes or updates cached data after the source of truth changes.

```text
DB update -> invalidate cache key
```

Common strategies:

- Delete on write
- Update on write
- TTL-only expiration
- Event-based invalidation
- Versioned cache keys

Versioned keys are often safer for files and judge testcases:

```text
testcase:{problemId}:v{version}:bundle:{bundleId}
```

When testcases change, write a new version instead of depending only on deleting old cache entries.

---

## Write Strategies

### Write-Around

```text
Write -> Database
Read later -> Cache miss -> Load cache
```

Useful when written data may not be read soon.

### Write-Through

```text
Write -> Cache + Database
```

Useful when reads immediately after writes should be fresh, but every write becomes slower.

### Write-Back

```text
Write -> Cache -> Async database write
```

Useful for write-heavy workloads where latency matters, but it needs strong durability planning. Without a durable queue or replication, data loss is possible.

---

## Eviction

Eviction removes data when cache memory is full.

```text
Cache full -> choose key to remove
```

Common policies:

| Policy | Meaning | Good For |
|---|---|---|
| LRU | Least Recently Used | Recent access predicts future access |
| LFU | Least Frequently Used | Long-term popularity matters |
| FIFO | First In, First Out | Simple workloads |
| Random | Remove a random key | Roughly equal access patterns |
| TTL-based | Remove keys closest to expiry | Expiration-aware caches |

Redis official docs emphasize that eviction is controlled by memory limits and eviction policy. In Redis, LRU and LFU are approximated for efficiency rather than tracked with perfect precision.

---

## Cache Failure Patterns

### Cache Stampede

Many requests miss the same hot key at once.

```text
Hot key expires -> 1000 requests -> 1000 DB queries
```

Mitigations:

- Request coalescing
- Distributed lock
- Soft TTL
- Background refresh
- TTL jitter

### Cache Avalanche

Many keys expire at nearly the same time.

Mitigations:

- Randomized TTL
- Staggered prewarming
- Background refresh
- Rate limiting

### Cache Penetration

Requests repeatedly ask for data that does not exist.

Mitigations:

- Negative caching
- Bloom filters
- Input validation
- Rate limiting

### Hot Key

One key receives extreme traffic.

Mitigations:

- Local near-cache
- Replicate hot key
- Split key by region or segment
- Request coalescing

---

## Case Study: Code Judge Testcases

Problem:

```text
Do we want to transfer huge testcase files from object storage for every submission?
```

Usually, no.

A code judge app server may need:

- Problem metadata
- Input files
- Expected outputs
- Time and memory limits
- Language runtime config

Suggested flow:

```text
Submission
  ↓
Judge Worker
  ↓
Local testcase cache
  ↓ miss
Object storage / S3
  ↓
Store locally by version
  ↓
Run testcase evaluation
```

Cache key:

```text
testcase:{problemId}:version:{version}:bundle:{bundleId}
```

Design choices:

- Cache versioned testcase bundles.
- Use local disk cache for repeated evaluations.
- Prewarm popular contest problems.
- Use LRU eviction when disk space is limited.
- Avoid CDN for hidden testcases unless access control is designed carefully.

Questions to ask:

- How large are average and worst-case testcase files?
- How often do testcases change?
- How many submissions hit the same problem?
- Can testcases be split into chunks?
- What happens when local disk cache is full?

---

## Case Study: Contest Leaderboard

Problem:

```text
Should ranklist be recalculated for every page view?
```

Usually, no.

A leaderboard is often:

- Read very frequently
- Updated after submissions
- Filtered by institute, region, or problem
- Paginated

Redis sorted sets are a common fit:

```text
ZADD contest:{contestId}:rank score userId
ZREVRANGE contest:{contestId}:rank 0 99 WITHSCORES
ZRANK contest:{contestId}:rank userId
```

Suggested flow:

```text
Submission judged
  ↓
Scoring service
  ↓
Event queue
  ↓
Leaderboard worker
  ↓
Redis sorted set
  ↓
Ranklist API
```

Cache key for rendered pages:

```text
ranklist:{contestId}:page:{page}:filter:{filterHash}:v{version}
```

Questions to ask:

- How many users are in the contest?
- How frequently is the ranklist queried?
- How many submissions per second are expected?
- Is immediate consistency required?
- Can public pages be stale for a few seconds?
- Which filters need to be cached?

---

## Design Checklist

Use this checklist in system design interviews:

1. Define the exact data to cache.
2. Estimate item size and total working set size.
3. Estimate read rate, write rate, and expected hit ratio.
4. Choose browser, CDN, local cache, or distributed cache.
5. Design precise cache keys.
6. Pick TTL based on acceptable staleness.
7. Choose invalidation strategy.
8. Choose write strategy.
9. Choose eviction policy.
10. Plan cache-miss and cache-down behavior.
11. Add stampede and avalanche protection.
12. Track metrics.

Important metrics:

- Hit ratio
- Miss ratio
- P50/P95/P99 latency
- Cache memory usage
- Evicted keys
- Expired keys
- Hot keys
- Backend fallback rate
- Error and timeout rate

---

## Official Learning Sources

These are the official docs I used for the key learning points:

- [Microsoft Learn: Cache-Aside pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/cache-aside)
- [AWS ElastiCache: Caching strategies for Memcached](https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/Strategies.html)
- [Redis Docs: Cache-aside](https://redis.io/docs/latest/develop/use-cases/cache-aside/)
- [Redis Docs: Key eviction](https://redis.io/docs/latest/develop/reference/eviction/)
- [Cloudflare Docs: Cache-Control concepts](https://developers.cloudflare.com/cache/concepts/cache-control/)

---

## Continue the Learning Path

- **Related:** Part 6 broader guide in [`caching.md`](./caching.md)
- **Previous:** Part 5 — Hashing Fundamentals in [`hashing-fundamentals.md`](./hashing-fundamentals.md)
- **Next:** Consistent Hashing — minimizing cache and shard movement when servers join or leave
