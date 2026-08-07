# System Design & Networking Fundamentals — Part 4

## Data Sharding & Routing

As applications grow, one question becomes increasingly important:

> Can we keep all our data on a single server?

Initially, yes. But as data volume and traffic increase, one machine can run out of storage, memory, CPU, disk throughput, or connection capacity. **Partitioning**, **sharding**, and **intelligent routing** allow the system to divide that work across multiple machines.

The central challenge is no longer just storing the data:

```text
Find the right data → Find the right shard → Find a healthy server
```

---

### ✍️ Handwritten Notes

- [View the handwritten study sheet](../assets/handwritten/data-sharding-routing/Data_Sharding_Routing_Handwritten_Notes.png)
- [Download the printable PDF](../assets/handwritten/data-sharding-routing/Data_Sharding_Routing_Handwritten_Notes.pdf)

---

## 📚 Table of Contents

1. [Why One Database Is Not Always Enough](#why-one-database-is-not-always-enough)
2. [Partitioning and Sharding](#partitioning-and-sharding)
3. [Vertical Partitioning](#vertical-partitioning)
4. [Horizontal Partitioning](#horizontal-partitioning)
5. [What Is a Sharding Key?](#what-is-a-sharding-key)
6. [How Shard Routing Works](#how-shard-routing-works)
7. [Sharding Strategies](#sharding-strategies)
8. [Load Balancing vs. Sharding](#load-balancing-vs-sharding)
9. [Replication Inside a Shard](#replication-inside-a-shard)
10. [Queries Across Multiple Shards](#queries-across-multiple-shards)
11. [Rebalancing and Resharding](#rebalancing-and-resharding)
12. [Hot Shards and Skew](#hot-shards-and-skew)
13. [Operational Challenges](#operational-challenges)
14. [Choosing a Strategy](#choosing-a-strategy)
15. [Key Takeaways](#key-takeaways)

---

## Why One Database Is Not Always Enough

A single database is usually the simplest and best place to start. It provides straightforward queries, transactions, constraints, backups, and operations.

As the system grows, however, one server can become constrained by:

- Storage capacity
- Read and write throughput
- CPU and memory
- Network bandwidth
- Number of active connections
- Maintenance and backup windows
- Geographic latency

Vertical scaling—using a larger machine—can postpone these limits, but it becomes expensive and eventually reaches a ceiling.

```text
All Data → One Database
             ├── Larger failure impact
             ├── Limited scale-up capacity
             ├── Growing maintenance time
             └── Possible performance bottleneck
```

Partitioning divides a large dataset into smaller pieces that can be managed or stored separately.

---

## Partitioning and Sharding

The terms are related but emphasize different ideas.

### Partitioning

**Partitioning** divides data into logical pieces. Those pieces may remain on one database server or be placed on different servers.

### Sharding

**Sharding** is horizontal partitioning in which different row groups are distributed across independent database servers or clusters.

```text
                All Users
                    ↓
            Sharding Function
          ┌─────────┼─────────┐
          ↓         ↓         ↓
       Shard 1   Shard 2   Shard 3
       Users A–H Users I–Q Users R–Z
```

Each shard contains only part of the total dataset, typically using the same schema.

> Every shard is a partition, but not every partition is necessarily a separately deployed shard.

---

## Vertical Partitioning

Vertical partitioning divides data by **columns, features, or responsibilities**.

Suppose one large user table contains:

```text
UserID | Name | Email | Address | Preferences | PaymentDetails
```

It could be split into:

```text
Profile Service  → UserID, Name, Email
Address Service  → UserID, Address
Payment Service  → UserID, PaymentDetails
```

### Advantages

- Frequently accessed columns can remain compact.
- Sensitive data can receive stronger controls.
- Features can scale and evolve independently.
- Different storage technologies can serve different workloads.

### Trade-Offs

- Reconstructing one complete entity may require several requests or joins.
- Transactions across partitions become harder.
- Service boundaries and ownership must be designed carefully.

Vertical partitioning helps organize responsibilities, but it does not automatically divide a large number of rows.

---

## Horizontal Partitioning

Horizontal partitioning divides data by **rows** while preserving the schema.

```text
Shard 1: UserID 1–1,000
Shard 2: UserID 1,001–2,000
Shard 3: UserID 2,001–3,000
```

Or:

```text
Shard 1: Region = Americas
Shard 2: Region = Europe
Shard 3: Region = APAC
```

### Advantages

- Storage and traffic can be distributed across machines.
- Each shard manages a smaller dataset and smaller indexes.
- Shards can scale independently.
- Failures may be isolated to part of the data.

### Trade-Offs

- The system must know where each row lives.
- Cross-shard joins and transactions are difficult.
- Uneven data or traffic can overload one shard.
- Adding and removing shards may require data movement.

Sharding is the distributed form of this pattern.

---

## What Is a Sharding Key?

A **sharding key** is the field—or group of fields—used to decide which shard stores a record.

Common examples include:

- User ID
- Account ID
- Customer ID
- Tenant ID
- Device ID
- Geographic region

### Simple Hash Example

For three shards:

```text
shardNumber = hash(userId) mod 3
```

For `userId = 12345`, a simplified example might be:

```text
12345 mod 3 = 0
```

The router sends the request to Shard 0.

```text
User ID
   ↓
Sharding Function
   ↓
Shard 0
   ↓
Healthy Replica
```

### Properties of a Good Sharding Key

A useful sharding key should:

- Have high cardinality
- Distribute data and traffic evenly
- Be present in common requests
- Keep related data together when helpful
- Avoid predictable hot spots
- Remain stable over the record's lifetime

### A Poor Sharding Key

Using a low-cardinality or heavily skewed value can overload one shard.

```text
subscriptionPlan = FREE | PRO | ENTERPRISE
```

If most users are on `FREE`, that shard receives most of the data and traffic.

---

## How Shard Routing Works

Sharding determines **where data lives**. Routing determines **where a request goes**.

### Request Flow

```text
Request with userId
        ↓
Extract Sharding Key
        ↓
Apply Sharding Strategy
        ↓
Identify Logical Shard
        ↓
Find Healthy Replica
        ↓
Execute Query
        ↓
Return Response
```

### Router Responsibilities

A shard router may:

1. Extract the key from the request.
2. Apply a hash, range lookup, or directory lookup.
3. Identify the correct logical shard.
4. Discover the current shard location.
5. Select the primary or an eligible replica.
6. Forward the operation.
7. Retry or fail safely when appropriate.

Routing can live in:

- Application code
- A shared client library
- A database proxy
- A dedicated routing service
- The distributed database itself

### Routing Metadata

Some strategies need a shard map:

```text
Range 1–1,000       → Shard A
Range 1,001–2,000   → Shard B
Range 2,001–3,000   → Shard C
```

This metadata must be consistent and highly available. Stale routing information can send operations to the wrong destination.

---

## Sharding Strategies

### Range-Based Sharding

Keys are divided into ordered ranges:

```text
UserID 1–1,000       → Shard A
UserID 1,001–2,000   → Shard B
UserID 2,001–3,000   → Shard C
```

**Advantages:**

- Easy to understand
- Efficient range queries
- Related ordered records stay together

**Trade-Offs:**

- Sequential keys can create a hot newest shard.
- Unequal ranges can cause skew.
- Splitting a busy range requires data movement.

### Hash-Based Sharding

A hash function transforms the key, and the result selects a shard:

```text
shard = hash(key) mod numberOfShards
```

**Advantages:**

- Usually produces an even distribution
- Reduces hot spots caused by sequential keys
- Direct lookup when the key is known

**Trade-Offs:**

- Range queries may contact many shards.
- Changing the shard count with simple modulo hashing moves a large amount of data.
- Related keys may land on different shards.

### Directory-Based Sharding

A lookup service stores the mapping:

```text
Tenant A → Shard 2
Tenant B → Shard 5
Tenant C → Shard 1
```

**Advantages:**

- Flexible placement
- Individual tenants can be moved
- Supports irregular assignments

**Trade-Offs:**

- The directory is critical infrastructure.
- Every lookup may require cached or remote metadata.
- Mapping changes must be coordinated safely.

### Geographic Sharding

Data is placed according to location:

```text
US customers   → US shard
EU customers   → EU shard
APAC customers → APAC shard
```

**Advantages:**

- Lower latency near users
- Can support data-residency requirements
- Regional failures can be isolated

**Trade-Offs:**

- Users and data can move between regions.
- Global queries become more complex.
- Cross-region consistency has latency costs.

### Composite Sharding

Large systems often combine strategies:

```text
Region → Tenant Hash → Shard
```

This can satisfy locality and distribution requirements, but it increases routing and operational complexity.

---

## Load Balancing vs. Sharding

These concepts answer different questions.

| Concept | Main question | Typical decision |
|---|---|---|
| Load balancing | Which healthy server should handle this request? | Choose among equivalent servers |
| Sharding | Which shard contains this data? | Choose the data partition |
| Routing | Where should this operation be sent? | Connect the request to its destination |

### Load-Balanced Stateless Servers

```text
Request → Any healthy application server
```

Every application server can usually process the request.

### Sharded Data Servers

```text
Request for User 42 → Only the shard containing User 42
```

The destination is constrained by data placement.

### Both Together

A shard is often replicated, so the system first finds the correct shard and then selects a suitable server within it:

```text
Request
   ↓
Shard Router
   ↓
Correct Logical Shard
   ↓
Primary or Healthy Replica
```

> Sharding narrows the destination set; load balancing chooses an eligible server within that set.

---

## Replication Inside a Shard

Sharding and replication solve different problems:

- **Sharding** divides the dataset for capacity and throughput.
- **Replication** copies data for availability and read scale.

```text
Shard A
├── Primary
├── Replica 1
└── Replica 2

Shard B
├── Primary
├── Replica 1
└── Replica 2
```

### Write Routing

Writes are commonly sent to the shard's primary:

```text
Write → Correct Shard → Primary
```

### Read Routing

Reads may go to:

- The primary for the freshest result
- A replica for additional capacity
- A nearby replica for lower latency

Replica reads can be stale when replication is asynchronous. The router must honor the consistency requirements of each operation.

---

## Queries Across Multiple Shards

Queries are easiest when they include the sharding key:

```sql
SELECT * FROM users WHERE user_id = 12345;
```

The router can target exactly one shard.

### Scatter-Gather

Without the sharding key, the system may need to query every shard:

```text
             ┌──→ Shard 1 ──┐
Query ───────┼──→ Shard 2 ──┼──→ Merge results
             └──→ Shard 3 ──┘
```

This is called a **scatter-gather query**.

It can be expensive because:

- Every shard performs work.
- Overall latency is influenced by the slowest shard.
- Results must be merged, sorted, or aggregated.
- One failed shard can make the result incomplete.

### Cross-Shard Joins

Related rows on different shards cannot use a simple local join. Common alternatives include:

- Co-locating related data with the same shard key
- Denormalizing frequently needed data
- Joining in the application layer
- Precomputing materialized views
- Using an analytics system for global queries

### Cross-Shard Transactions

Atomic updates across shards may require distributed coordination, such as two-phase commit, or an application workflow based on sagas and compensating actions. Both approaches add failure modes and complexity.

---

## Rebalancing and Resharding

Over time, a system may need to:

- Add shards for capacity
- Remove shards
- Split a hot range
- Move a large tenant
- Replace infrastructure
- Correct uneven distribution

### The Modulo Problem

With:

```text
hash(key) mod N
```

changing `N` changes the result for many keys. Adding one shard can therefore move a large portion of the dataset.

### Safe Migration Pattern

A controlled shard move may follow this sequence:

1. Create the destination shard.
2. Copy existing data.
3. Capture or dual-write ongoing changes.
4. Verify consistency.
5. Update routing metadata.
6. Shift reads and writes.
7. Monitor the new placement.
8. Retire the old copy after a safe period.

Migrations should be throttled so data movement does not overwhelm production traffic.

### Consistent Hashing

Consistent hashing maps keys and servers onto a logical ring. When a server is added or removed, only a portion of the keys move.

That is the next topic in this learning path.

---

## Hot Shards and Skew

A **hot shard** receives much more traffic or data than others.

### Common Causes

- A celebrity or very large tenant
- Time-ordered writes sent to the newest range
- A low-cardinality shard key
- Uneven geographic traffic
- One popular product or event
- Poor hash distribution

### Mitigation Strategies

- Choose a higher-cardinality key.
- Add a hash prefix or suffix.
- Split a busy range.
- Isolate very large tenants.
- Cache frequently read data.
- Replicate read-heavy shards.
- Rate-limit abusive workloads.
- Repartition with a different strategy.

Evenly distributed row counts do not guarantee evenly distributed traffic. Both storage and access patterns must be measured.

---

## Operational Challenges

Sharding increases capacity but also introduces complexity.

### Schema Changes

Migrations must run safely across every shard, possibly while different shards temporarily use different schema versions.

### Backups and Recovery

Backups should be coordinated when a globally consistent snapshot is required. Restoring one shard to a different point in time can violate cross-shard assumptions.

### Unique Constraints

A shard can enforce uniqueness locally, but global uniqueness may require:

- Globally generated IDs
- A central allocation service
- Namespaced identifiers
- A coordination step

### Observability

Monitor each shard separately and the fleet as a whole:

- Storage utilization
- Read and write throughput
- Query latency
- Error and timeout rates
- Replication lag
- Connection count
- Hot-key frequency
- Scatter-gather fan-out

### Failure Handling

The system must distinguish between:

- The wrong shard
- An unavailable shard
- An unavailable replica
- Stale routing metadata
- A partially completed cross-shard operation

Retries must be bounded and safe. Retrying a non-idempotent write can duplicate an operation.

---

## Choosing a Strategy

Start with the simplest architecture that satisfies the requirements.

| Workload characteristic | Possible approach |
|---|---|
| Dataset fits comfortably on one server | Single database plus replicas |
| Features have distinct data and ownership | Vertical partitioning |
| Natural ordered ranges and range queries | Range-based sharding |
| High-cardinality point lookups | Hash-based sharding |
| Large tenants need custom placement | Directory-based sharding |
| Strong locality or residency requirements | Geographic sharding |
| Frequent global analytics | Separate analytical data platform |

Before sharding, consider whether the immediate bottleneck can be solved with:

- Better indexes and query plans
- Caching
- Read replicas
- Archiving old data
- Table partitioning on one database
- Vertical scaling
- Connection pooling

Sharding should address a measured scaling need because it permanently affects queries, transactions, operations, and application design.

---

## Key Takeaways

- Partitioning divides data into manageable pieces.
- Vertical partitioning splits columns or responsibilities.
- Horizontal partitioning splits rows.
- Sharding distributes horizontal partitions across independent servers.
- The sharding key determines where data lives.
- Routing maps each operation to the correct shard and a suitable server.
- Load balancing chooses among equivalent healthy servers; sharding locates the required data.
- Queries without the sharding key may require expensive scatter-gather operations.
- Rebalancing, hot shards, schema changes, backups, and cross-shard transactions are major operational concerns.
- A scalable design combines good data placement, accurate routing, replication, and health-aware server selection.

> 🧩 Right data partitioning + smart routing + efficient load balancing = a scalable distributed system.

---

## Continue the Learning Path

- **Previous:** Part 3 — Load Balancers in [`load-balancers.md`](./load-balancers.md)
- **Next:** Part 5 — Hashing Fundamentals in [`hashing-fundamentals.md`](./hashing-fundamentals.md)
