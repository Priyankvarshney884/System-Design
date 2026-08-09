# Databases for System Design Interviews

## Deep Method for FANG-Style Interviews

Databases are not just storage choices in system design interviews. They decide how the system handles scale, correctness, latency, failures, cost, product flexibility, analytics, and operational complexity.

In FANG-style interviews, the strongest answers usually do not start with:

```text
Use SQL.
Use NoSQL.
Use Cassandra.
Use Redis.
```

They start with:

```text
What are the access patterns?
What consistency does the product need?
How much data and traffic are expected?
What are the read/write ratios?
What failures must the system tolerate?
What queries must be fast?
What can be asynchronous?
```

This note gives you a decision framework and deep coverage of the main database concepts interviewers expect.

---

## Table of Contents

1. [Database Interview Framework](#database-interview-framework)
2. [Relational Databases](#relational-databases)
3. [Pros and Cons of SQL](#pros-and-cons-of-sql)
4. [Database Schema](#database-schema)
5. [Normalization](#normalization)
6. [ACID Transactions](#acid-transactions)
7. [ACID Consistency vs CAP Consistency](#acid-consistency-vs-cap-consistency)
8. [Unstructured Data](#unstructured-data)
9. [Sharding](#sharding)
10. [Choosing a Good Sharding Key](#choosing-a-good-sharding-key)
11. [Fanout Queries](#fanout-queries)
12. [NoSQL Databases](#nosql-databases)
13. [SQL vs NoSQL](#sql-vs-nosql)
14. [ACID vs BASE](#acid-vs-base)
15. [Denormalization and Replication](#denormalization-and-replication)
16. [Key-Value Databases: Redis](#key-value-databases-redis)
17. [Document Databases: MongoDB](#document-databases-mongodb)
18. [Wide Column Databases: Cassandra](#wide-column-databases-cassandra)
19. [Large File Storage: S3](#large-file-storage-s3)
20. [Other Database Types](#other-database-types)
21. [Row-Wide vs Column-Wide Storage](#row-wide-vs-column-wide-storage)
22. [Choosing the Correct Database](#choosing-the-correct-database)
23. [Interview Decision Playbooks](#interview-decision-playbooks)
24. [Common Interview Pitfalls](#common-interview-pitfalls)
25. [Quick Revision Sheet](#quick-revision-sheet)

---

## Database Interview Framework

Before choosing a database, clarify these dimensions.

### 1. Data Model

Ask:

- Is the data relational?
- Are joins important?
- Is the structure fixed or flexible?
- Are records independent documents?
- Is the data graph-shaped?
- Is it time-series, vector, search, or file/blob data?

Examples:

| Product | Natural model |
|---|---|
| Banking ledger | Relational / strongly transactional |
| Product catalog | Document + search |
| News feed | Wide-column / key-value / cache-heavy |
| Social graph | Graph or adjacency lists over NoSQL/SQL |
| Metrics platform | Time-series / columnar analytics |
| Image/video upload | Object storage |
| Semantic search | Vector database |

### 2. Access Patterns

Interviewers care about access patterns more than database names.

Define:

- Write path: what is written, how often, and by whom?
- Read path: what queries are most common?
- Query shape: point lookup, range scan, join, aggregation, full-text search, graph traversal, nearest-neighbor search?
- Sort order: newest first, top ranked, by score, by location?
- Latency target: p50, p95, p99?

Example:

```text
Twitter timeline:
- Write: user posts tweet.
- Read: user opens home timeline.
- Query: get recent tweets from followed users.
- Challenge: high fanout, celebrity accounts, freshness.
```

### 3. Consistency Needs

Ask what must be correct immediately.

Strong consistency is usually needed for:

- Money movement
- Inventory deduction
- Seat booking
- Password or permission changes
- Idempotency records
- Unique constraints

Eventual consistency is often acceptable for:

- Like counts
- View counts
- Recommendations
- Search indexes
- Analytics dashboards
- Feed ranking

### 4. Scale

Estimate:

- Number of users
- Requests per second
- Reads vs writes
- Data size per record
- Total storage
- Growth rate
- Hot keys or celebrity users
- Geographic distribution

### 5. Failure Model

Ask:

- What happens if a database node fails?
- What happens if a region fails?
- Can we serve stale data?
- Can we reject writes?
- Can we queue work and process later?

### 6. Operations

A theoretically perfect database may be a poor operational choice.

Consider:

- Backup and restore
- Schema migrations
- Rebalancing shards
- Monitoring and alerting
- Query debugging
- Data repair
- Team familiarity
- Cloud managed service availability

---

## Relational Databases

Relational databases store data in tables with rows and columns. Tables can reference each other using keys.

Common examples:

- PostgreSQL
- MySQL
- MariaDB
- Oracle
- SQL Server

### Core Concepts

| Concept | Meaning |
|---|---|
| Table | Collection of rows with a defined schema |
| Row | One record |
| Column | A field in each row |
| Primary key | Unique identifier for a row |
| Foreign key | Reference to a row in another table |
| Index | Data structure that speeds up reads |
| Transaction | Group of operations committed together |
| Join | Combining rows from multiple tables |

### When Relational Databases Are Strong

Use relational databases when:

- Data has clear relationships.
- Correctness matters.
- Joins are useful.
- Transactions are required.
- Schema is stable enough to model.
- You need constraints like uniqueness and foreign keys.
- You want mature operational tooling.

Examples:

- Payments
- Orders
- Inventory
- User accounts
- Subscriptions
- Banking
- Enterprise applications

### Interview Example

For an e-commerce order system:

```text
users
orders
order_items
products
payments
shipments
```

Relational storage is natural because orders, payments, and inventory require correctness and transactional updates.

---

## Pros and Cons of SQL

SQL means Structured Query Language. It is used to query relational databases.

### Pros

- Mature and widely understood.
- Supports joins and complex queries.
- Supports ACID transactions.
- Enforces schemas and constraints.
- Excellent for correctness-heavy systems.
- Powerful indexing options.
- Strong tooling for migrations, backups, and debugging.

### Cons

- Horizontal scaling can be harder than NoSQL.
- Joins across huge distributed datasets are expensive.
- Schema changes can be operationally risky at large scale.
- Strict schemas can slow product iteration.
- Write throughput may bottleneck on a primary node.
- Cross-region writes with strong consistency can be slow.

### Interview Soundbite

Use SQL when correctness, relationships, constraints, and transactions are more important than extreme horizontal write scale.

---

## Database Schema

A schema defines the structure of stored data.

In relational databases, schema includes:

- Tables
- Columns
- Data types
- Primary keys
- Foreign keys
- Indexes
- Constraints

Example:

```sql
CREATE TABLE orders (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  total_amount_cents BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL
);
```

### Schema Design Questions

For interviews, think through:

- What is the primary entity?
- What are the relationships?
- What fields are required?
- What fields are frequently queried?
- What indexes are needed?
- Which fields change often?
- Which fields require uniqueness?

### Good Schema Design

Good schema design supports the product's access patterns.

For example, if the main query is:

```text
Get recent orders for a user.
```

Then this index matters:

```sql
CREATE INDEX idx_orders_user_created
ON orders (user_id, created_at DESC);
```

---

## Normalization

Normalization organizes data to reduce duplication and avoid update anomalies.

### Why Normalize?

Suppose every order row stores full user information:

```text
order_id | user_name | user_email | user_address | product_name
```

If the user's email changes, many rows may need updates. That can create inconsistency.

Instead, normalize:

```text
users
- id
- name
- email

orders
- id
- user_id
- created_at
```

### Normal Forms, Practically

You do not need to recite every normal form in most system design interviews. Understand the idea:

| Level | Practical meaning |
|---|---|
| 1NF | Each field contains atomic values |
| 2NF | Non-key fields depend on the full primary key |
| 3NF | Non-key fields do not depend on other non-key fields |

### Benefits

- Less duplication
- Easier updates
- Better data integrity
- Smaller storage footprint
- Clearer ownership of facts

### Costs

- More joins
- More query complexity
- Higher read latency for some access patterns
- Harder horizontal scaling for join-heavy workloads

### Interview Tradeoff

Normalize for correctness and maintainability. Denormalize selectively for read performance at scale.

---

## ACID Transactions

ACID describes transaction guarantees.

```text
Atomicity
Consistency
Isolation
Durability
```

### Atomicity

All operations in a transaction succeed or none do.

Example:

```text
Debit account A
Credit account B
```

You cannot allow only one side to happen.

### Consistency

A transaction moves the database from one valid state to another valid state according to constraints.

Example:

```text
balance >= 0
order must reference an existing user
email must be unique
```

### Isolation

Concurrent transactions should not corrupt each other.

Common isolation problems:

| Problem | Meaning |
|---|---|
| Dirty read | Read uncommitted data |
| Non-repeatable read | Same row changes between reads |
| Phantom read | New rows appear between range queries |
| Lost update | Two writers overwrite each other |

Common isolation levels:

| Level | Rough behavior |
|---|---|
| Read committed | Only committed data is read |
| Repeatable read | Same rows stay consistent during transaction |
| Serializable | Transactions behave like they ran one at a time |

### Durability

Once committed, data survives crashes.

Databases typically use write-ahead logs, replication, and disk persistence to achieve durability.

### Interview Example: Booking Seats

For booking a concert seat:

```text
1. Check seat availability.
2. Mark seat as reserved.
3. Create payment record.
4. Commit.
```

This needs ACID behavior because double-booking is unacceptable.

---

## ACID Consistency vs CAP Consistency

This is a common confusion.

### ACID Consistency

ACID consistency means the database preserves rules and constraints during transactions.

Example:

```text
An order cannot exist without a valid user.
Account balance cannot go below zero.
Email must be unique.
```

This is about correctness of data rules.

### CAP Consistency

CAP consistency means every read sees the latest write in a distributed system.

Example:

```text
If user changes profile name in region A,
then a read from region B immediately sees the new name.
```

This is about replica visibility in distributed systems.

### CAP Theorem

CAP says that during a network partition, a distributed system must choose between:

| Property | Meaning |
|---|---|
| Consistency | All clients see the same latest data |
| Availability | Every request receives a non-error response |
| Partition tolerance | System continues despite network splits |

In real distributed systems, partitions can happen, so the practical tradeoff is often:

```text
During partition: choose Consistency or Availability.
```

### Interview Soundbite

ACID consistency is about valid database state. CAP consistency is about latest-value visibility across replicas.

---

## Unstructured Data

Unstructured data does not fit neatly into fixed rows and columns.

Examples:

- Images
- Videos
- Audio files
- PDFs
- Logs
- Free-form text
- Chat transcripts
- Emails
- Clickstream events

### How to Store It

Usually, do not store large files directly in a relational database.

Common pattern:

```text
Metadata in SQL/NoSQL
Large object in S3/object storage
Searchable text in search index
Analytics events in data lake/warehouse
```

Example:

```text
profile_photos table:
- user_id
- object_key
- content_type
- size_bytes
- uploaded_at

Actual image:
s3://bucket/profile-photos/user123/photo.jpg
```

### Interview Rule

Store metadata in a database. Store large blobs in object storage. Index only what you need to search.

---

## Sharding

Sharding splits data across multiple machines.

Instead of:

```text
All users -> one database
```

Use:

```text
user_id % 4

Shard 0
Shard 1
Shard 2
Shard 3
```

### Why Shard?

- More storage capacity
- Higher write throughput
- Higher read throughput
- Smaller indexes per machine
- Better fault isolation

### Costs of Sharding

- More operational complexity
- Cross-shard queries become hard
- Transactions across shards are expensive
- Rebalancing data is difficult
- Hot shards can overload
- Global secondary indexes are hard

### Common Sharding Strategies

| Strategy | How it works | Pros | Cons |
|---|---|---|---|
| Range-based | A-M on shard 1, N-Z on shard 2 | Easy range scans | Hot ranges |
| Hash-based | hash(key) mod N | Even distribution | Range queries hard |
| Directory-based | Lookup service maps key to shard | Flexible | Extra routing dependency |
| Geo-based | Data by region | Low regional latency | Uneven regions, cross-region complexity |

### Sharding vs Partitioning

Partitioning means splitting data into pieces. Sharding usually means those pieces live across different servers.

---

## Choosing a Good Sharding Key

A sharding key decides where data lives.

Good keys have:

- High cardinality
- Even distribution
- Stable values
- Alignment with common queries
- Low chance of hot partitions
- Minimal cross-shard transactions

### Good Examples

| System | Good sharding key |
|---|---|
| User profile service | user_id |
| Multi-tenant SaaS | tenant_id, if tenants are balanced |
| Chat messages | conversation_id |
| Orders | user_id or merchant_id depending on query |
| IoT events | device_id plus time bucketing |

### Bad Examples

| Bad key | Problem |
|---|---|
| country | Low cardinality, uneven traffic |
| created_at | Hot latest shard |
| status | Very few values |
| celebrity_user_id only | Hot key risk |
| mutable email | Re-sharding needed if email changes |

### Interview Method

For each candidate key, ask:

```text
Will it distribute writes evenly?
Will reads usually hit one shard?
Will important transactions stay within one shard?
Can one key become extremely hot?
Can we rebalance later?
```

### Composite Sharding Keys

Sometimes use composite keys.

Example:

```text
tenant_id + hash(user_id)
```

This can preserve tenant locality while spreading large tenants.

### Hot Key Mitigation

If one key gets too much traffic:

- Add cache
- Split hot key into sub-shards
- Use write buffering
- Use time buckets
- Use read replicas
- Special-case celebrity accounts

---

## Fanout Queries

Fanout happens when one request turns into many downstream reads or writes.

### Fanout on Read

Example: home timeline.

```text
User follows 1,000 people.
Open timeline -> fetch recent posts from 1,000 users -> merge and rank.
```

Pros:

- Writes are cheap.
- Timelines are fresh.
- No need to precompute for every follower.

Cons:

- Reads are expensive.
- High latency for users who follow many accounts.
- Query may hit many shards.

### Fanout on Write

When a user posts:

```text
Write post -> push post ID into followers' home timelines.
```

Pros:

- Reads are fast.
- Timeline is precomputed.

Cons:

- Writes are expensive.
- Celebrity users can create massive fanout.
- Requires async workers and queues.

### Hybrid Fanout

Common at large scale.

```text
Normal users: fanout on write.
Celebrity users: fanout on read.
```

### Interview Examples

| Product | Fanout challenge |
|---|---|
| Twitter/X | Celebrity accounts |
| Instagram | Feed ranking |
| Facebook | Social graph and privacy filters |
| YouTube | Subscriptions and recommendations |
| LinkedIn | Feed freshness and professional graph |

---

## NoSQL Databases

NoSQL is a broad category of non-relational databases. It does not mean "no SQL ever"; it usually means flexible data models and horizontal scalability.

Main types:

- Key-value
- Document
- Wide-column
- Graph
- Time-series
- Search
- Vector

### Why NoSQL?

- Flexible schema
- High horizontal scale
- High write throughput
- Simple access patterns
- Large distributed datasets
- Eventual consistency options
- Data model closer to application reads

### Costs

- Fewer joins
- Weaker transactional guarantees in many systems
- Data duplication
- Harder ad hoc querying
- Application must handle more consistency complexity
- Operational differences across products

---

## SQL vs NoSQL

| Dimension | SQL | NoSQL |
|---|---|---|
| Data model | Tables and relations | Key-value, document, wide-column, graph, etc. |
| Schema | Usually fixed | Flexible or query-driven |
| Joins | Strong support | Limited or avoided |
| Transactions | Strong ACID | Varies by database |
| Scaling | Vertical first, then replicas/shards | Horizontal often built in |
| Query flexibility | High | Usually access-pattern oriented |
| Best for | Correctness and relationships | Scale and flexible/simple access |

### Interview Rule

Use SQL for transactional truth. Use NoSQL when access patterns are known, scale is high, and denormalized/query-shaped data is acceptable.

### Common Hybrid Design

Many real systems use both:

```text
PostgreSQL: source of truth for users, payments, orders
Redis: cache and rate limiting
Elasticsearch/OpenSearch: search
Cassandra/DynamoDB: high-volume feed/events
S3: images/videos/files
Warehouse: analytics
```

---

## ACID vs BASE

BASE is often used to describe eventually consistent NoSQL systems.

```text
Basically Available
Soft state
Eventually consistent
```

### ACID

ACID prioritizes correctness and strong transaction guarantees.

Best for:

- Banking
- Orders
- Payments
- Inventory
- Booking
- Identity and permissions

### BASE

BASE prioritizes availability and scale, accepting temporary inconsistency.

Best for:

- Feeds
- Counts
- Recommendations
- Search indexes
- Analytics
- Notifications

### Comparison

| Dimension | ACID | BASE |
|---|---|---|
| Consistency | Stronger | Eventual |
| Availability | May reject/slow requests to preserve correctness | Usually high |
| Latency | Can be higher | Often lower |
| Complexity | Database handles more | Application handles more |
| Use case | Critical correctness | Large-scale distributed reads/writes |

---

## Denormalization and Replication

### Denormalization

Denormalization duplicates data to make reads faster.

Example:

Instead of joining:

```text
posts -> users
```

Store:

```text
post_id
author_id
author_name
author_avatar_url
content
created_at
```

This avoids a user lookup on every feed item.

### Benefits

- Faster reads
- Fewer joins
- Better scaling
- Query-shaped data

### Risks

- Stale duplicated fields
- More storage
- More complex writes
- Need repair jobs or async updates

### Replication

Replication copies data across nodes.

Types:

| Type | Description |
|---|---|
| Leader-follower | One primary handles writes, followers serve reads |
| Multi-leader | Multiple writable leaders |
| Leaderless | Writes go to multiple replicas using quorum |

### Replication Lag

Replica reads may be stale.

Example:

```text
User updates profile.
Immediate read from replica still shows old profile.
```

Mitigations:

- Read-your-writes from primary
- Session stickiness
- Version checks
- Wait for replication in critical paths
- Accept staleness where product allows

---

## Key-Value Databases: Redis

Redis is an in-memory key-value store commonly used for low-latency access.

### Common Uses

- Cache
- Session store
- Rate limiting
- Distributed locks, with care
- Leaderboards
- Counters
- Pub/sub
- Queues, though dedicated queues are often better

### Data Structures

| Structure | Use |
|---|---|
| String | Simple value, counter |
| Hash | Object-like fields |
| List | Queue-like operations |
| Set | Unique members |
| Sorted set | Ranking, leaderboard |
| Stream | Append-only event stream |

### Example: Rate Limiting

```text
Key: rate:user:123:minute:2026-08-07T19:04
Value: request count
TTL: 60 seconds
```

### Pros

- Extremely fast
- Simple API
- TTL support
- Atomic operations
- Great cache layer

### Cons

- Memory is expensive
- Persistence is optional/config-dependent
- Not ideal as primary source of truth for critical data
- Cluster management and hot keys need care

### Interview Soundbite

Use Redis to reduce latency and database load, not as the default source of truth for critical durable records.

---

## Document Databases: MongoDB

Document databases store JSON-like documents.

Example:

```json
{
  "user_id": "u123",
  "name": "Priyank",
  "addresses": [
    { "type": "home", "city": "Delhi" }
  ],
  "preferences": {
    "language": "en",
    "theme": "dark"
  }
}
```

### When Document Databases Fit

Use when:

- Data is naturally document-shaped.
- Schema evolves frequently.
- Most reads fetch the whole document.
- Embedded objects are usually read together.
- Joins are limited.

Examples:

- Product catalog
- User profile
- CMS content
- Event payloads
- Configuration documents

### Embedding vs Referencing

Embed when:

- Child data is small.
- Child data is read with parent.
- Child lifecycle belongs to parent.

Reference when:

- Child data is large.
- Child data changes independently.
- Many parents share the same child.

### Pros

- Flexible schema
- Natural JSON model
- Good developer productivity
- Easier horizontal scaling than traditional relational systems in some workloads

### Cons

- Joins are less natural
- Duplicated data can become stale
- Document growth can be a problem
- Complex multi-document transactions need care

---

## Wide Column Databases: Cassandra

Wide-column databases store rows with many columns and are designed for high-scale distributed workloads.

Examples:

- Cassandra
- HBase
- Google Bigtable

### Cassandra Mental Model

Cassandra is designed around queries, not arbitrary joins.

You model tables based on access patterns.

Example:

```text
Query: Get latest messages for a conversation.

Table:
messages_by_conversation
- conversation_id as partition key
- created_at as clustering key
- message_id
- sender_id
- body
```

### Strengths

- Very high write throughput
- Horizontal scalability
- Multi-region replication
- High availability
- Handles large datasets well

### Weaknesses

- Query flexibility is limited
- Joins are not supported in the relational sense
- Data modeling is harder
- Hot partitions can hurt badly
- Eventual consistency is common

### Good Use Cases

- Time-series events
- Messaging
- Activity feeds
- IoT data
- Write-heavy logs
- Large-scale user activity storage

### Interview Soundbite

Use Cassandra when writes are huge, access patterns are known, availability matters, and you can design query-specific tables.

---

## Large File Storage: S3

Object storage like Amazon S3 stores large blobs as objects.

Alternatives:

- Google Cloud Storage
- Azure Blob Storage
- MinIO

### What Goes in Object Storage?

- Images
- Videos
- PDFs
- Backups
- Logs
- ML datasets
- Static assets

### Object Storage Pattern

```text
Client uploads file -> object storage
Application stores metadata -> database
CDN caches public delivery
```

Metadata example:

```text
files
- id
- owner_id
- bucket
- object_key
- content_type
- size_bytes
- checksum
- visibility
- created_at
```

### Pros

- Very scalable
- Durable
- Cost-effective for large files
- Works well with CDN
- Supports lifecycle policies

### Cons

- Not for low-latency small record lookups
- Not for complex queries
- Object updates are not like database row updates
- Need separate metadata database

### Interview Soundbite

Put large files in S3, store metadata in a database, and serve through CDN when read traffic is high.

---

## Other Database Types

### Graph Databases

Examples:

- Neo4j
- Amazon Neptune
- JanusGraph

Best for:

- Social graph traversal
- Fraud rings
- Recommendation graph
- Knowledge graphs
- Permission inheritance

Good query:

```text
Find friends-of-friends within 2 hops.
```

Avoid graph databases when simple adjacency lists in SQL/NoSQL are enough.

### Vector Databases

Examples:

- Pinecone
- Weaviate
- Milvus
- pgvector
- Elasticsearch/OpenSearch vector search

Best for:

- Semantic search
- Similarity search
- RAG systems
- Recommendation embeddings
- Image/audio similarity

Core idea:

```text
Store embedding vector -> find nearest vectors by similarity.
```

### Search Databases

Examples:

- Elasticsearch
- OpenSearch
- Solr

Best for:

- Full-text search
- Filtering
- Ranking
- Autocomplete
- Log search

Search indexes are usually derived data, not the primary source of truth.

### Time-Series Databases

Examples:

- InfluxDB
- TimescaleDB
- Prometheus

Best for:

- Metrics
- Monitoring
- IoT sensor data
- Financial ticks

Optimized for:

- Time-based writes
- Retention policies
- Downsampling
- Range queries over time

### Columnar Analytics Databases

Examples:

- BigQuery
- Snowflake
- Redshift
- ClickHouse
- Apache Druid

Best for:

- Analytics
- Aggregations over huge datasets
- Reporting
- OLAP workloads

Not ideal for:

- Small transactional writes
- User-facing point updates
- Strict OLTP transactions

---

## Row-Wide vs Column-Wide Storage

This topic is often confused because "wide-column database" and "columnar storage" sound similar.

### Row-Oriented Storage

Traditional relational OLTP databases often store rows together.

Example:

```text
Row:
user_id | name | email | city | created_at
```

Good for:

- Fetching whole records
- Transactional workloads
- Point lookups
- Updates to individual rows

Examples:

- PostgreSQL
- MySQL

### Column-Oriented Storage

Columnar databases store values from the same column together.

Example:

```text
all user_ids together
all cities together
all created_at values together
```

Good for:

- Analytics
- Aggregations
- Scanning few columns across many rows
- Compression

Examples:

- BigQuery
- Snowflake
- Redshift
- ClickHouse

### Wide-Column Databases

Wide-column databases like Cassandra are distributed key-value-ish stores with rows that can have many columns, often organized by partition and clustering keys.

They are not the same as columnar analytics warehouses.

### Comparison

| Storage type | Best for | Example |
|---|---|---|
| Row-oriented | OLTP transactions | PostgreSQL |
| Column-oriented | OLAP analytics | BigQuery |
| Wide-column | Distributed write-heavy access-pattern-based storage | Cassandra |

---

## Choosing the Correct Database

Use this decision table in interviews.

| Need | Good choice |
|---|---|
| Strong transactions, joins, constraints | PostgreSQL/MySQL |
| Cache, counters, sessions, rate limits | Redis |
| Flexible JSON documents | MongoDB |
| Massive write-heavy distributed data | Cassandra/DynamoDB/Bigtable |
| Large files | S3/object storage |
| Full-text search | Elasticsearch/OpenSearch |
| Analytics over huge data | BigQuery/Snowflake/ClickHouse |
| Relationship traversal | Graph database |
| Semantic similarity | Vector database |
| Metrics over time | Time-series database |

### Selection Process

1. Identify the source of truth.
2. Identify derived views.
3. Choose storage for the source of truth first.
4. Add cache/search/analytics stores as derived systems.
5. Define consistency between stores.
6. Define failure behavior.

### Example: Instagram-Like App

```text
User accounts: SQL
Posts metadata: SQL or document store
Images/videos: S3
Feed timelines: Cassandra/DynamoDB
Cache: Redis
Search: Elasticsearch/OpenSearch
Analytics: Data warehouse
Recommendations: Feature store/vector/ML pipeline
```

### Example: Payment System

```text
Ledger: SQL with ACID transactions
Idempotency keys: SQL or strongly consistent KV
Events: Kafka
Cache: limited, never source of truth
Analytics: warehouse
```

### Example: Chat System

```text
User accounts: SQL
Conversations: SQL or NoSQL
Messages: Cassandra/DynamoDB partitioned by conversation_id
Media: S3
Online presence: Redis
Search: Elasticsearch/OpenSearch
```

---

## Interview Decision Playbooks

### When Asked: Design a URL Shortener

Storage:

```text
short_code -> long_url
```

Good choice:

- SQL can work at moderate scale.
- Key-value store works well at high read scale.
- Redis can cache hot codes.

Important discussion:

- Unique short code generation
- Read-heavy workload
- Cache hot links
- Analytics async
- Custom aliases need uniqueness

### When Asked: Design Twitter/X Feed

Storage:

- User graph: SQL/NoSQL adjacency lists
- Tweets: NoSQL or SQL depending scale
- Home timeline: wide-column/key-value
- Media: S3
- Cache: Redis

Important discussion:

- Fanout on write vs fanout on read
- Celebrity users
- Timeline ranking
- Eventual consistency
- Sharding by user_id

### When Asked: Design Uber Location Tracking

Storage:

- Current driver location: Redis/geospatial index
- Location events: Cassandra/time-series
- Trip/order state: SQL
- Analytics: warehouse

Important discussion:

- High write rate
- TTL for live location
- Geospatial queries
- Eventual consistency acceptable for map movement
- Strong consistency needed for trip/payment state

### When Asked: Design Dropbox/Google Drive

Storage:

- File metadata: SQL/NoSQL
- File chunks: S3/object storage
- Permissions: SQL/graph-like model
- Search: search index

Important discussion:

- Metadata vs blob separation
- Versioning
- Deduplication
- Large upload handling
- CDN for downloads

---

## Common Interview Pitfalls

### Pitfall 1: Choosing NoSQL Only Because of Scale

Bad:

```text
It is large scale, so use NoSQL.
```

Better:

```text
The main access pattern is key-based lookup with very high write throughput and no joins, so a distributed key-value or wide-column store fits.
```

### Pitfall 2: Ignoring Transactions

If the system handles money, inventory, booking, or permissions, discuss transactions explicitly.

### Pitfall 3: Overusing Joins at Huge Scale

Joins are powerful, but cross-shard joins are expensive. At scale, precompute or denormalize critical read paths.

### Pitfall 4: Ignoring Hot Keys

Any design with celebrities, viral posts, popular products, or large tenants must address hot partitions.

### Pitfall 5: Treating Cache as Source of Truth

Cache improves latency and load, but durable data should live elsewhere unless you deliberately choose a durable database.

### Pitfall 6: Forgetting Derived Data Consistency

If SQL updates and search index updates are separate, define how they stay in sync:

- Outbox pattern
- Change data capture
- Event stream
- Retry jobs
- Reconciliation jobs

---

## Quick Revision Sheet

### SQL

Best for:

- Relationships
- Joins
- Transactions
- Constraints
- Correctness

Tradeoff:

- Harder horizontal scaling and schema evolution.

### NoSQL

Best for:

- High scale
- Flexible schema
- Known access patterns
- Denormalized reads

Tradeoff:

- More application-level consistency complexity.

### Sharding

Use when one database cannot handle storage or throughput.

Good sharding key:

- High-cardinality
- Evenly distributed
- Stable
- Query-aligned
- Avoids hot shards

### ACID vs BASE

```text
ACID: correctness and strong transactions.
BASE: availability and eventual consistency.
```

### ACID Consistency vs CAP Consistency

```text
ACID consistency: valid database state.
CAP consistency: latest write visible to all readers.
```

### Denormalization

Use to make reads fast. Manage stale duplicated data carefully.

### Replication

Use for availability and read scaling. Watch for replication lag.

### Storage Choices

| Data | Storage |
|---|---|
| Orders/payments | SQL |
| Sessions/cache | Redis |
| JSON product catalog | MongoDB |
| Feed/events/messages | Cassandra/DynamoDB |
| Images/videos | S3 |
| Search | Elasticsearch/OpenSearch |
| Analytics | Columnar warehouse |
| Recommendations/search similarity | Vector DB |
| Social graph traversal | Graph DB |

---

## Final Interview Answer Template

When choosing storage, say:

```text
I would use <database> for <data> because the access pattern is <query/write pattern>,
the consistency requirement is <strong/eventual>,
and the scale concern is <storage/read/write/geographic>.

For <derived data>, I would use <cache/search/index/object storage>.
The source of truth remains <system>.
Updates propagate via <sync writes/events/CDC/outbox>.
The main risks are <hot keys/replication lag/rebalancing/cross-shard queries>,
which I would handle with <mitigation>.
```

That structure shows interviewers you are making a reasoned engineering tradeoff, not just naming technologies.
