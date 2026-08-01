# Facebook News Feed System Design Case Study

## Overview

Facebook's News Feed is a personalized, continuously updating stream of stories from friends, pages, groups, recommendations, ads, and engagement signals. In system design interviews, this case study is usually framed as:

> Design Facebook's News Feed.

The hard part is not only storing posts. The hard part is building a low-latency, highly available, personalized feed for billions of users while handling extreme fan-out, celebrity accounts, ranking, media, freshness, privacy, and failure recovery.

This guide uses the linked reference as a learning anchor:

- Reference: [02-newsfeed-answers-T.md](https://github.com/kanmaytacker/system-design/blob/master/case-studies/02-newsfeed-answers-T.md)
- Key idea from the reference: a scalable feed is mainly about **fan-out strategy + ranking strategy + materialized timelines + hot-key protection**.

As of Meta's Q1 2026 report, Meta reported **3.56 billion Family daily active people** across Facebook, Instagram, Messenger, WhatsApp, and other services. We should not design only for millions of users; a Facebook-like design must assume multi-billion-user scale, highly skewed social graphs, and global traffic.

---

## Table of Contents

1. [Problem Statement](#problem-statement)
2. [Clarifying Questions](#clarifying-questions)
3. [Requirements](#requirements)
4. [Scale Assumptions](#scale-assumptions)
5. [Core Concepts](#core-concepts)
6. [API Design](#api-design)
7. [Data Model](#data-model)
8. [High-Level Architecture](#high-level-architecture)
9. [Feed Publishing Flow](#feed-publishing-flow)
10. [Feed Reading Flow](#feed-reading-flow)
11. [Fan-Out Strategies](#fan-out-strategies)
12. [Ranking System](#ranking-system)
13. [Caching Strategy](#caching-strategy)
14. [Database and Sharding](#database-and-sharding)
15. [Handling Celebrities and Hot Keys](#handling-celebrities-and-hot-keys)
16. [Media Handling](#media-handling)
17. [Privacy, Blocking, and Moderation](#privacy-blocking-and-moderation)
18. [Reliability and Failure Handling](#reliability-and-failure-handling)
19. [Global Deployment](#global-deployment)
20. [Observability](#observability)
21. [Trade-Offs](#trade-offs)
22. [Final Architecture Summary](#final-architecture-summary)
23. [Interview Answer Template](#interview-answer-template)
24. [Learning References](#learning-references)

---

## Problem Statement

Design a Facebook-like News Feed where users can:

- Create posts with text, images, videos, and links
- Follow or friend other users, pages, and groups
- Open the app and see a personalized feed
- Scroll through paginated feed results
- Like, comment, share, hide, report, or save posts
- Receive fresh content within seconds

The system should support:

- Very high read traffic
- High write traffic
- Personalized ranking
- Global availability
- Privacy and visibility checks
- Celebrity accounts with millions of followers
- Graceful degradation during partial failures

---

## Clarifying Questions

Before designing, ask:

### Product Scope

- Is the feed only for friends, or also pages, groups, recommendations, and ads?
- Should posts be chronological or ranked?
- Do we support images and videos?
- Do we include comments inline?
- Are stories, reels, notifications, search, and profile timelines in scope?

### Scale

- How many daily active users?
- Average friends or followed entities per user?
- Average posts created per user per day?
- Feed reads per user per day?
- Maximum followers for celebrity accounts?

### Freshness and Latency

- How quickly should a new post appear?
- What is the target feed read latency?
- Is eventual consistency acceptable?
- Can we serve stale feeds during outages?

### Ranking

- Is feed ranking rule-based or machine-learning based?
- Do we rank at write time, read time, or both?
- Do we need real-time engagement signals?

### Privacy

- Are posts public, friends-only, group-only, or custom audience?
- How are blocks, unfriends, and deleted posts handled?

---

## Requirements

### Functional Requirements

1. Users can create posts.
2. Users can upload images and videos.
3. Users can follow, friend, join groups, and follow pages.
4. Users can read a personalized home feed.
5. Feed supports pagination.
6. Users can like, comment, share, hide, report, and save posts.
7. Feed ranking uses social graph, recency, engagement, content quality, and user preferences.
8. Privacy rules must be enforced before showing content.
9. Deleted, blocked, or hidden content should disappear from feed results.

### Non-Functional Requirements

| Requirement | Target |
|---|---|
| Availability | Very high, feed should almost always load |
| Feed read latency | p95 under 300 ms for cached/home feed path |
| Post write latency | User post creation should return quickly |
| Freshness | Normal posts visible within a few seconds |
| Consistency | Eventual consistency is acceptable |
| Scalability | Billions of users, trillions of feed edges |
| Durability | Posts and engagement events must not be lost |
| Privacy | Strong correctness requirement |

### Out of Scope

- Full ad auction design
- Full comment-thread design
- Full video transcoding pipeline
- Full search system
- Full notification system
- Full recommendation graph design

---

## Scale Assumptions

These are interview-friendly estimates, not exact Facebook numbers.

| Metric | Assumption |
|---|---:|
| Daily active users | 1B+ for Facebook-like app |
| Feed reads per DAU per day | 20 |
| Posts per DAU per day | 1 |
| Average friends/follows | 300 |
| Max friends/follows | 5,000+ |
| Celebrity followers | 1M to 100M+ |
| Feed page size | 20 posts |
| Materialized feed size | 500 to 1,000 post IDs per user |

### Traffic Estimate

If 1B users read feed 20 times/day:

```text
20B feed reads/day
= 231K reads/sec average
= 2M+ reads/sec peak, depending on traffic spikes
```

If 1B users create 1 post/day:

```text
1B posts/day
= 11.5K posts/sec average
= much higher during peak events
```

Reads dominate writes, so we optimize the read path heavily.

---

## Core Concepts

### News Feed

A personalized list of posts ranked for a specific viewer.

### Social Graph

The graph of relationships:

```text
User A follows User B
User A is friends with User C
User A joined Group G
User A follows Page P
```

### Fan-Out

The process of distributing a new post to people who may see it.

There are two main types:

- **Fan-out on write**: push post IDs into followers' feed caches when the post is created.
- **Fan-out on read**: fetch posts from followed users when the viewer opens the feed.

### Materialized Timeline

A precomputed per-user feed list, usually stored as post IDs sorted by time or score.

Example:

```text
feed:user:123 = [
  post_991,
  post_882,
  post_771
]
```

### Ranking

Scoring candidate posts so the most relevant posts appear first.

---

## API Design

### Create Post

```http
POST /v1/posts
Authorization: Bearer <token>
Content-Type: application/json
```

Request:

```json
{
  "text": "Hello world",
  "media_ids": ["media_123"],
  "visibility": "FRIENDS",
  "group_id": null
}
```

Response:

```json
{
  "post_id": "post_987",
  "status": "created",
  "created_at": "2026-08-01T10:00:00Z"
}
```

### Get Feed

```http
GET /v1/feed?cursor=<opaque_cursor>&limit=20
Authorization: Bearer <token>
```

Response:

```json
{
  "items": [
    {
      "post_id": "post_987",
      "author_id": "user_456",
      "text": "Hello world",
      "media": [],
      "score": 0.982,
      "created_at": "2026-08-01T10:00:00Z"
    }
  ],
  "next_cursor": "eyJvZmZzZXQiOjIwfQ"
}
```

### Follow User

```http
PUT /v1/users/{target_user_id}/follow
Authorization: Bearer <token>
```

### Unfollow User

```http
DELETE /v1/users/{target_user_id}/follow
Authorization: Bearer <token>
```

### Record Engagement

```http
POST /v1/posts/{post_id}/engagement
Authorization: Bearer <token>
Content-Type: application/json
```

Request:

```json
{
  "type": "LIKE"
}
```

---

## Data Model

### User Table

```text
users
-----
user_id          PK
name
profile_photo_id
created_at
status
```

### Post Table

```text
posts
-----
post_id          PK
author_id        indexed
text
media_ids
visibility
created_at       indexed
deleted_at
```

### Follow Edge Table

```text
follow_edges
------------
follower_id
followee_id
created_at

Primary key: (follower_id, followee_id)
Index: (followee_id, follower_id)
```

We need both directions:

- Get all users I follow.
- Get all followers of a user who just posted.

### Feed Timeline Store

```text
home_feed
---------
user_id
score_or_time
post_id
author_id
inserted_at

Primary key: (user_id, score_or_time, post_id)
```

This can be stored in a distributed cache or wide-column store.

### Engagement Events

```text
engagement_events
-----------------
event_id
user_id
post_id
event_type
created_at
metadata
```

Events are append-only and processed asynchronously.

---

## High-Level Architecture

```text

System-Design/handWrittenPdfs/facebookfeed.png
---

## Feed Publishing Flow

When a user creates a post:

```text
Client
  ↓
API Gateway
  ↓
Post Service
  ↓
Write post to Post Store
  ↓
Publish PostCreated event to Queue
  ↓
Fan-out Workers consume event
  ↓
Graph Service fetches followers
  ↓
Ranking / Filtering computes initial eligibility
  ↓
Insert post_id into followers' materialized feeds
```

### Important Detail

The `POST /posts` API should not wait for fan-out to finish. It should:

1. Validate request.
2. Store post durably.
3. Emit event.
4. Return success.

Fan-out is asynchronous.

---

## Feed Reading Flow

When a user opens the feed:

```text
Client
  ↓
Feed Service
  ↓
Fetch candidate post IDs from materialized feed
  ↓
Pull recent posts from celebrity/followed accounts if needed
  ↓
Fetch post bodies from Post Cache/Post Store
  ↓
Apply privacy, block, and deletion filters
  ↓
Fetch ranking features
  ↓
Rank candidates
  ↓
Hydrate media and author info
  ↓
Return paginated response
```

### Candidate Generation

The feed service should fetch more candidates than it returns.

Example:

```text
Need 20 posts in response
Fetch 300 candidate post IDs
Filter invalid posts
Rank remaining posts
Return top 20
```

This leaves room for filtering, privacy checks, hidden posts, and ranking.

---

## Fan-Out Strategies

### Option 1: Fan-Out on Write

When Alice posts, push the post ID into every follower's feed.

```text
Alice posts
  ↓
Fetch Alice's followers
  ↓
Insert post ID into each follower's feed
```

#### Pros

- Feed reads are very fast.
- Good for users with small or moderate follower counts.
- Works well when reads greatly outnumber writes.

#### Cons

- Bad for celebrities.
- One post by a user with 100M followers creates 100M feed writes.
- Wasteful for inactive followers.

### Option 2: Fan-Out on Read

When Bob opens feed, fetch recent posts from everyone Bob follows.

```text
Bob opens feed
  ↓
Fetch Bob's followees
  ↓
Fetch recent posts from each followee
  ↓
Merge and rank
```

#### Pros

- Write path is simple.
- Great for celebrity posters.
- No huge write amplification.

#### Cons

- Feed reads become expensive.
- Bad for users who follow thousands of accounts.
- Harder to keep latency low.

### Option 3: Hybrid Fan-Out

Use both:

- Normal users: fan-out on write.
- Celebrities: fan-out on read.
- Inactive followers: optionally skip push and pull later.

```text
If author follower_count < threshold T:
  push post into followers' feeds
else:
  store post only; pull it at read time
```

This is the most practical answer for a Facebook-scale feed.

---

## Ranking System

Modern Facebook feed is ranked, not purely chronological.

### Ranking Goals

- Show relevant posts.
- Keep feed fresh.
- Promote meaningful interactions.
- Reduce spam, clickbait, misinformation, and low-quality content.
- Respect user controls such as hide, unfollow, mute, and report.

### Ranking Pipeline

```text
Candidate Generation
  ↓
Lightweight Filtering
  ↓
Feature Fetching
  ↓
ML Scoring
  ↓
Diversity and Policy Rules
  ↓
Final Feed Page
```

### Candidate Sources

- Materialized timeline
- Recent posts from close friends
- Group posts
- Page posts
- Celebrity posts pulled at read time
- Recommended posts
- Ads

### Example Features

| Feature Type | Examples |
|---|---|
| User features | interests, language, location, device |
| Author features | relationship strength, past interactions |
| Post features | age, type, topic, quality score |
| Engagement features | likes, comments, shares, dwell time |
| Negative signals | hides, reports, blocks, spam score |
| Context features | time of day, network quality, session intent |

### Scoring Formula

A simplified scoring model:

```text
score =
  relevance_score
  + freshness_boost
  + relationship_score
  + engagement_quality_score
  - spam_penalty
  - seen_penalty
```

In real systems, this is usually a multi-stage ML pipeline:

1. **Recall model**: select a few hundred or thousand candidates.
2. **Ranking model**: score candidates more precisely.
3. **Re-ranking layer**: enforce diversity, freshness, policy, and product rules.

### Why Ranking and Fan-Out Are Coupled

Fan-out decides which candidates are available. Ranking decides which candidates are shown.

If we push too few candidates, ranking has poor choices.
If we pull too many candidates, feed reads become slow.

A good design keeps a bounded but rich candidate set.

---

## Caching Strategy

### What to Cache

| Cache | Purpose |
|---|---|
| Feed cache | Store materialized feed post IDs |
| Post cache | Store hot post bodies |
| User cache | Store author profile snippets |
| Graph cache | Store follow lists and follower lists |
| Feature cache | Store ranking features |
| Media CDN | Serve images and videos |
| Celebrity post cache | Store latest posts from high-follower accounts |

### Feed Cache

Use Redis, Memcached, or a custom distributed cache.

Example:

```text
key: feed:user:123
value: sorted set of post IDs
limit: latest 500-1000 candidate posts
```

### Cache Policies

- Keep only recent candidates.
- Use TTL for old feed entries.
- Remove deleted or blocked posts lazily during read.
- Rebuild feed from source of truth if cache is lost.

### Cache Stampede Protection

For hot posts or celebrity timelines:

- Request coalescing
- Stale-while-revalidate
- Soft TTL and hard TTL
- Replicated hot-key cache
- Background warming

---

## Database and Sharding

### Post Store

Use a distributed database or wide-column store.

Access patterns:

- Get post by `post_id`
- Get recent posts by `author_id`
- Get posts by time range

Possible partition key:

```text
partition: author_id
sort: created_at DESC
```

But pure author-based partitioning can hot-spot celebrities. For celebrity users, split by time bucket:

```text
partition: author_id + month_bucket
sort: created_at DESC
```

### Graph Store

The graph store must support:

- Get followees of viewer.
- Get followers of author.
- Add/remove edge.
- Handle high-degree nodes.

Large adjacency lists should be sharded.

Example:

```text
followers:{author_id}:{shard_id}
```

### Feed Store

Feed timelines can be partitioned by `user_id`.

```text
partition: user_id
sort: score_or_time DESC
```

This supports fast feed reads.

---

## Handling Celebrities and Hot Keys

Celebrity accounts create two problems:

1. **Write amplification**: pushing one post to millions of feeds is expensive.
2. **Read hot key**: everyone may request the same celebrity post at once.

### Solution

Use hybrid fan-out:

```text
Small/normal accounts:
  fan-out on write

Celebrity accounts:
  fan-out on read
```

### Threshold T

Set a follower-count threshold:

```text
if follower_count > T:
  celebrity mode
else:
  normal push mode
```

`T` is not fixed forever. It changes based on:

- Infrastructure capacity
- Active follower ratio
- Region
- Post frequency
- Cache hit rate
- Read latency

### Celebrity Read Cache

For each celebrity:

```text
key: recent_posts:celebrity:{author_id}
value: latest 50-100 post IDs
```

Use:

- Higher replication factor
- Request coalescing
- Cache warming
- Stale reads during refresh

---

## Media Handling

Posts may contain photos and videos. Do not store large media inside the post database.

### Media Upload Flow

```text
Client requests upload URL
  ↓
Media Service returns pre-signed URL
  ↓
Client uploads media to Object Storage
  ↓
Media Service scans/transcodes media
  ↓
Post Service stores media_id references
  ↓
CDN serves media to feed clients
```

### Components

- Object storage for original media
- CDN for delivery
- Transcoding service for video
- Thumbnail generator
- Malware and policy scanner
- Metadata store

---

## Privacy, Blocking, and Moderation

Privacy is more important than feed freshness.

### Visibility Rules

Each post may be:

- Public
- Friends only
- Group only
- Custom audience
- Private

### Filtering Rules

Before returning a post:

- Is the viewer allowed to see it?
- Did the author block the viewer?
- Did the viewer block the author?
- Was the post deleted?
- Was the post hidden by the viewer?
- Was the post removed by moderation?
- Is the group/page membership still valid?

### When to Apply Privacy Checks

Apply privacy checks:

1. During fan-out, to reduce bad candidates.
2. During read, to guarantee correctness.

Read-time privacy checks are necessary because relationships can change after fan-out.

---

## Reliability and Failure Handling

### Queue Failure

If fan-out workers are delayed:

- Post still exists in source of truth.
- Feed may be stale briefly.
- Workers catch up from queue.
- For important accounts, read path can pull recent posts directly.

### Feed Cache Loss

Materialized feed is derived data.

If feed cache is lost:

- Rebuild from recent posts of followees.
- Serve degraded chronological feed.
- Use older backup feed entries if available.

### Ranking Service Failure

Fallback options:

1. Use cached ranking scores.
2. Use lightweight heuristic ranking.
3. Use reverse chronological order.
4. Serve stale feed instead of error.

### Post Store Failure

If some post bodies cannot be fetched:

- Skip unavailable posts.
- Return fewer posts if needed.
- Retry in background.

### At-Least-Once Fan-Out

Fan-out queue can be at-least-once because timeline inserts should be idempotent.

Use unique key:

```text
user_id + post_id
```

Duplicate fan-out events should update or no-op, not create duplicate feed entries.

---

## Global Deployment

Facebook-like systems are global.

### Regional Design

```text
User traffic
  ↓
Nearest region
  ↓
Regional API, cache, feed, graph replicas
  ↓
Cross-region replication for durable stores
```

### Strategy

- Route users to nearest healthy region.
- Keep hot feed data close to users.
- Replicate posts and media globally.
- Store graph data with regional replicas.
- Use eventual consistency across regions.
- Prefer availability for feed reads.

### Multi-Region Challenges

- Cross-region latency
- Data residency laws
- Privacy enforcement
- Duplicate event processing
- Conflict resolution
- Disaster recovery

---

## Observability

Track:

### Product Metrics

- Feed load success rate
- Time to first feed item
- Scroll depth
- Engagement rate
- Hide/report rate
- Freshness delay

### System Metrics

- Feed read latency p50/p95/p99
- Post write latency
- Queue lag
- Fan-out throughput
- Cache hit rate
- Ranking latency
- Feature store latency
- Error rate by dependency

### Alerts

- Queue lag above threshold
- Feed read p99 too high
- Cache hit rate drops
- Ranking service errors
- Privacy filtering errors
- Celebrity cache hot spots

---

## Trade-Offs

### Push vs Pull

| Approach | Best For | Problem |
|---|---|---|
| Fan-out on write | Normal users, fast reads | Expensive for celebrities |
| Fan-out on read | Celebrities, low write cost | Expensive feed reads |
| Hybrid | Real large-scale systems | More operational complexity |

### Consistency vs Availability

For feed freshness:

- Eventual consistency is acceptable.
- Slightly stale feed is better than no feed.

For privacy:

- Strong correctness is required.
- Never show content the viewer is not allowed to see.

### Ranking at Write vs Read

| Approach | Pros | Cons |
|---|---|---|
| Rank at write | Faster reads | Scores become stale |
| Rank at read | Fresher personalization | Higher read latency |
| Hybrid | Balanced | More complex |

---

## Final Architecture Summary

```text
Create Post Path
----------------
Client
  → API Gateway
  → Post Service
  → Post Store
  → Message Queue
  → Fan-out Workers
  → Graph Service
  → Feed Store / Feed Cache

Read Feed Path
--------------
Client
  → API Gateway
  → Feed Service
  → Feed Cache / Feed Store
  → Celebrity Recent Post Cache
  → Post Cache / Post Store
  → Feature Store
  → Ranking Service
  → Privacy Filter
  → Response
```

### The Core Design

Use a **hybrid fan-out model**:

- Push normal posts into followers' materialized feed caches.
- Pull celebrity posts at read time.
- Store only post IDs in the feed cache.
- Fetch post bodies separately.
- Rank candidates at read time using user, author, post, engagement, and context features.
- Enforce privacy at read time.
- Use caches aggressively but treat materialized feeds as rebuildable derived state.

---

## Interview Answer Template

If you need to answer quickly:

> I would design Facebook News Feed using a hybrid fan-out architecture. For normal users, when they create a post, we asynchronously push the post ID into followers' materialized feed caches using a queue and fan-out workers. For celebrities or very high-follower accounts, we avoid huge write amplification and instead pull their recent posts at feed-read time from a heavily cached celebrity-post store. The feed read path fetches candidate post IDs from the user's materialized timeline, merges in pulled celebrity posts, filters deleted/private/blocked content, fetches post bodies and ranking features, ranks candidates, hydrates media and author metadata, and returns a paginated feed. Posts are durable source-of-truth data; feed timelines are derived and can be rebuilt. The system favors availability and low latency for feed reads, but privacy checks must be correct before any post is shown.

---

## Learning References

### Provided Reference

- [02-newsfeed-answers-T.md](https://github.com/kanmaytacker/system-design/blob/master/case-studies/02-newsfeed-answers-T.md)  
  Useful for understanding hybrid fan-out, materialized timelines, celebrity handling, cache stampede protection, and the relationship between fan-out and ranking.

### Related Topics to Study Next

- Distributed caching
- Cache invalidation
- Message queues
- Event-driven architecture
- Consistent hashing
- Database sharding
- Wide-column databases
- CDN design
- Feature stores
- Recommendation systems
- Ranking pipelines
- Privacy and access-control systems

### Internal Repo Notes

- [Caching](./caching.md)
- [Cache Design](./cache-design.md)
- [Data Sharding and Routing](./data-sharding-routing.md)
- [Load Balancers](./load-balancers.md)
- [DNS](./dns.md)
- [Hashing Fundamentals](./hashing-fundamentals.md)

---

## Key Takeaways

- A News Feed is not just a database query; it is a distributed personalization system.
- Reads dominate writes, so feed reads must be fast and cache-friendly.
- Pure fan-out on write breaks for celebrities.
- Pure fan-out on read breaks for users who follow many accounts.
- Hybrid fan-out is the practical large-scale design.
- Materialized timelines should be treated as derived state, not the source of truth.
- Ranking and fan-out must be designed together.
- Privacy checks must be enforced at read time.
- The system should degrade gracefully by serving stale or chronological feeds when ranking or fan-out is delayed.
