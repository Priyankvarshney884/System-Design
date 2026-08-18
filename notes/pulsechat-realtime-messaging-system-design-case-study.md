# PulseChat: Real-Time Messaging System Design Case Study

> **Application:** PulseChat — a modern WhatsApp/Slack-style application for 1-to-1 and group messaging.
>
> **Learning goal:** use one complete case study to practise moving from requirements to a scalable architecture.

## 1. The interview approach: do not start with technology

An interviewer asks: **“Design a real-time chat application.”** Do not answer “I will use Redis, Kafka, and Cassandra.” First find out what problem those components would solve.

### A five-step design method

1. Clarify the problem, users, and scope.
2. List functional and non-functional requirements.
3. Estimate traffic, storage, and connection scale.
4. Identify access patterns; choose consistency, data model, and partitioning.
5. Draw the simplest architecture and explain failure handling and trade-offs.

The decision chain is:

```text
requirements -> scale -> access patterns -> consistency -> data model
             -> sharding -> storage/cache -> reliability -> trade-offs
```

## 2. Problem statement, MVP, and actors

PulseChat lets people exchange durable text messages in direct conversations and groups. Connected users should see messages almost immediately; offline users should receive a notification and retrieve missed messages after reconnecting.

### MVP scope

- Send text messages in direct and group conversations (up to 500 members).
- Persist and paginate message history.
- Show sent, delivered, and read status.
- Push live events to connected clients and notifications to offline devices.
- Safely retry a send without creating duplicate messages.

### Explicitly out of scope

Voice/video calling, search, reactions, media upload, moderation, and end-to-end encryption implementation. Mentioning exclusions keeps an architecture focused; each can be added later without hiding the core design.

### Actors

| Actor | What they do |
| --- | --- |
| Sender | sends a message and sees acknowledgement/status |
| Recipient | receives live messages, opens history, marks messages read |
| Group member | sends and receives in a group conversation |
| Push provider | wakes an offline mobile device |
| Operations team | monitors latency, capacity, abuse, and failures |

## 3. Requirements and consistency

### Functional requirements

1. Create direct/group conversations and manage membership.
2. Send, receive, and fetch messages.
3. Preserve order inside one conversation.
4. Show delivery/read progress.
5. Reconnect and catch up after a device was offline.

### Non-functional requirements

| Requirement | Target | Reason |
| --- | --- | --- |
| Send acknowledgement | p99 under 300 ms in-region | sender needs immediate feedback |
| Live delivery | usually under 1 second | chat should feel live |
| Durability | no accepted message is lost | messages are user data |
| Availability | 99.99% send/receive target | core product feature |
| Ordering | ordered per conversation | replies must make sense |
| Privacy | authorization and encryption | content is sensitive |

### Consistency choices, in plain language

- **Strong consistency:** every reader sees the latest committed value. Use it sparingly: for example, checking current group membership before accepting a message.
- **Eventual consistency:** another device may see a change shortly later. Good for push delivery, presence, and unread-count updates.
- **Read-your-writes:** the sender must see their own accepted message immediately, even if a replica is briefly behind.
- **Monotonic reads:** once a client saw message sequence 42, it must never later show only sequence 41.

PulseChat does not need a global order for every message in the world. It needs one durable order **per conversation**. That smaller consistency boundary makes scaling practical.

## 4. Scale estimation and capacity planning

Assume 100M MAU, 20M DAU, 40 sent messages per DAU per day, 2M peak connected users, 500-byte average text message, and a 10x peak factor.

```text
messages/day       = 20M x 40 = 800M
average writes/sec = 800M / 86,400 ≈ 9,300
peak writes/sec    ≈ 93,000
raw storage/day    = 800M x 500 B ≈ 400 GB
raw storage/year   ≈ 146 TB
```

These are estimates, not promises. Add replication, indexes, metadata, retention, and media references to get a real capacity plan. Also separate **message writes** from **recipient deliveries**: one group message can create hundreds of delivery events.

For 2M persistent connections, if a gateway supports 50,000 mostly idle WebSockets, the baseline is 40 gateways before redundancy and regional headroom.

## 5. Read-heavy vs write-heavy access patterns

| Operation | Pattern | Implication |
| --- | --- | --- |
| Send message | ordered write to one conversation | append efficiently by conversation |
| Open history | latest page, then older pages | range reads with cursor pagination |
| Receive live message | fan-out to online members | locate users’ active connections quickly |
| Load chat list | many summaries for one user | precompute/materialize inbox view |
| Mark as read | small frequent update | store one read cursor, not a row per message |

History reads and inbox loads are read-heavy. Sending is write-heavy. A design that serves every chat-list load with live joins over all messages will fail early; build a user-oriented summary view instead.

## 6. Data model and database selection

```text
Conversation(conversation_id, type, created_at, metadata)
ConversationMember(conversation_id, user_id, role, last_read_sequence)
Message(conversation_id, sequence, message_id, sender_id, body, created_at,
        client_message_id)
UserInbox(user_id, conversation_id, latest_sequence, preview, unread_count)
UserConnection(user_id, connection_id, gateway_id, expires_at)
```

### Which database type fits which job?

| Type | PulseChat use | Why |
| --- | --- | --- |
| SQL | users, group membership, administration | joins and transactions are useful |
| Key-value | sessions, connection lookup, idempotency records | very fast lookups by exact key |
| Document | flexible profiles/conversation metadata | schema can evolve easily |
| Column-family/wide-column | message history by conversation and sequence | high write throughput and range scans |
| Object storage | media attachments | cheaper large-file storage; save URL in message |

At MVP scale, a relational database with indexes can serve all core data. At very high append volume, use a wide-column message store keyed by `(conversation_id, sequence)` while retaining SQL for membership. Database selection follows the workload; it is not a brand-name contest.

### Indexes and materialized views

- Index `ConversationMember(conversation_id, user_id)` to quickly authorize a send.
- Read history using `WHERE conversation_id = ? AND sequence < ? ORDER BY sequence DESC LIMIT 50`; avoid slow offset pagination.
- Materialize `UserInbox` so a chat list is a quick lookup by `user_id`, not an expensive join across every conversation.
- Use read replicas for older history only when small replication lag is acceptable.

## 7. Sharding, keys, and routing

### `user_id` versus `conversation_id`

| Partition key | Best table/workload | Limitation |
| --- | --- | --- |
| `user_id` | user inbox, sessions, active connection lookup | a chat involves more than one user |
| `conversation_id` | messages and message ordering | a massive group can be hot |

Messages are sharded by `conversation_id`, because the system appends and reads one conversation in sequence. Inbox summaries and connection mappings are sharded by `user_id`, because they are retrieved for one person.

### Consistent hashing and shard routing

```text
hash(conversation_id) -> virtual node on hash ring -> message shard
```

Virtual nodes spread partitions across machines. Adding a shard moves only a fraction of keys, rather than reassigning all conversations. The router uses a versioned shard map; during a migration, forward/dual-read as needed until cutover.

### 1-to-1 and groups

A direct conversation receives a stable `conversation_id` (generated or based on ordered member IDs). A group also gets one ID. Both use the same message model. The difference is fan-out: a direct message has one recipient; a group message may create hundreds of delivery tasks.

For a celebrity-sized group, retain one canonical ordered message log but distribute recipient delivery work through queues. Do not split competing writers across shards unless you can still assign one correct sequence.

## 8. Core real-time architecture

```text
Client --WebSocket--> Connection Gateway --> Message API/Service
                                      |             |
                                      |             v
                                      |       Message store + transactional outbox
                                      |             |
                                      v             v
                              Connection registry  Durable event stream
                                                    |       |       |
                                             delivery workers  inbox updater  notification worker
                                                    |
                                           online recipient gateways
```

### Why WebSockets?

WebSockets maintain a bidirectional connection. They fit chat because clients both send data and need immediate server pushes. Gateways remain mostly stateless; a short-lived registry maps `user_id -> active gateway/connection` and is refreshed by heartbeats.

**SSE** is suitable for one-way server-to-browser events. **Polling** is simpler for infrequent updates but creates latency and wasted requests. **Pub/sub/event streams** distribute events between backend services; they do not replace the browser/mobile connection.

## 9. Send flow, idempotency, and ordering

### Send message flow

1. Client sends body and a locally generated `client_message_id`.
2. API authenticates the sender and checks group membership.
3. Service checks whether this client message was already accepted.
4. Conversation shard assigns the next sequence number.
5. Persist the message and an outbox event atomically.
6. Return the durable message ID and sequence to the sender.
7. Publish the outbox event to the event stream.
8. Delivery workers update inbox/cache, push online users, and queue offline notifications.

The sender is acknowledged after durable persistence, not after every device receives the message. This gives low latency and allows fan-out to scale independently.

### Idempotency

Network failure can happen after the server stores a message but before the reply reaches the client. The client retries with the **same** ID. Store `(sender_id, client_message_id) -> message_id, sequence` for a retry window; return the existing result rather than adding a duplicate.

### Message ordering

The conversation shard assigns monotonically increasing sequences:

```text
conversation 81: 41, 42, 43
client receives: 41, 43, 42
client displays: 41, 42, 43 (buffer or refetch missing 42)
```

Use at-least-once delivery plus deduplication by `message_id`/sequence. Avoid claiming “exactly once” end-to-end: retries and failures make that misleading. Idempotent producers/consumers provide the practical result users need.

### Statuses

- **Sent:** durably accepted by PulseChat.
- **Delivered:** received by at least one recipient device/session, according to product definition.
- **Read:** a member advances `last_read_sequence`.

For a group, one per-user read cursor is cheaper than storing a receipt for every message.

## 10. Cache design, TTL, invalidation, and eviction

The database is the source of truth. Cache is the fast, disposable copy.

| Cached data | Pattern | Why |
| --- | --- | --- |
| group membership | cache-aside, short TTL, invalidate on membership change | permission check is frequent |
| recent messages | cache-aside + TTL | active chats are read repeatedly |
| connection/presence | write-through + short TTL | rapidly changing, non-durable state |
| inbox summaries | update from event stream | fast chat-list reads |
| idempotency keys | TTL equal to retry period | duplicate protection |

### Cache routing and failure handling

Use `hash(cache_key)` to select a cache node; consistent hashing minimizes movement when nodes change. On a cache miss, read the source store and refill. On cache outage, fall back to the source store with rate limits, circuit breakers, and degraded features so the database is not overwhelmed.

### Invalidation and eviction

- **TTL:** expires stale data eventually; add jitter to avoid many simultaneous expirations.
- **Explicit invalidation:** on `MembershipChanged`, invalidate/update `conversation:{id}:members` immediately.
- **Event-based invalidation:** durable event consumers update all cache nodes/services.
- **LRU:** evicts least-recently-used keys; strong default for recent messages.
- **LFU:** preserves frequently used keys; useful for repeatedly hot conversations.
- **FIFO:** evicts oldest inserted keys; simple but may remove a key still in use.

Use TTL even with event invalidation as a safety net for missed events. Protect popular-key expiry with single-flight/refill locking to prevent a cache stampede.

## 11. Reliability, real-time recovery, and availability

| Failure | What happens | Design response |
| --- | --- | --- |
| sender times out | retry may occur | idempotency returns original message |
| gateway disconnects | live stream stops | reconnect and sync missing history |
| event duplicated | delivery worker runs twice | deduplicate by message/event ID |
| event arrives out of order | UI could look wrong | compare sequence, buffer/fetch gaps |
| cache is down | reads slow down | source-store fallback + protection |
| push provider fails | notification delayed | retry asynchronously; message remains durable |
| shard fails | reduced capacity/risk | replicas, failover, backups, tested recovery |

On reconnect, the client reports the last sequence it saw (or asks for history since a cursor). The server reads missed messages from durable storage. This is vital: WebSocket push improves latency, but it is not the only delivery guarantee.

Monitor send p99, end-to-end delivery lag, active connections, reconnect rate, event queue lag, cache hit rate, source-store latency, hot shards, duplicate rate, and failed notifications. Alert on user effects such as delivery lag above 10 seconds.

## 12. Scalability and engineering trade-offs

### Start simple, then split by measured pressure

**MVP:** API service + WebSocket gateway + relational DB + Redis + job queue.  
**At scale:** dedicated message store, durable event stream, delivery workers, materialized inbox pipeline, cache cluster, and sharding by conversation.

| Decision | Gain | Cost |
| --- | --- | --- |
| conversation-based partitioning | local ordering and efficient appends | hot-group risk |
| asynchronous fan-out | fast sender acknowledgement | eventual recipient delivery; queues to operate |
| materialized inbox | fast chat-list reads | duplicated data / update pipeline |
| cache-aside recent history | lower latency and DB load | stale-data/invalidation complexity |
| read replicas | scalable historical reads | replica lag |
| per-conversation consistency | excellent scalability | no global order |

## 13. Interview-ready summary

> “For PulseChat, I guarantee durable ordered messages per conversation and read-your-writes for the sender. I shard message history by `conversation_id`, which matches ordered appends and range reads; I shard inbox/connection lookup by `user_id`. A client-generated ID makes sends idempotent. After atomically storing the message and outbox event, workers fan out over WebSocket gateways, update materialized inboxes, and notify offline users. Redis accelerates hot membership and recent data but is never authoritative. Reconnecting clients fetch missed messages from durable history, so real-time push is an optimization rather than a correctness dependency.”

## 14. Final takeaway

Good system design is good questions, clear requirements, measured scale, and explicit trade-offs. For PulseChat, the essential decisions are: persist before fan-out, order within the conversation boundary, make retries idempotent, use the right partition key for each workload, and make every live feature recover correctly after failure.
