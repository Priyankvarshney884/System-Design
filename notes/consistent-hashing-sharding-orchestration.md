# System Design & Distributed Databases

## Consistent Hashing, Sharding, Replication, and Orchestration

This note connects several ideas that often appear together in scalable database design:

```text
Consistent Hashing → Sharding → Replication → Orchestration → Rebalancing
```

The goal is simple:

```text
Store huge data safely
Serve reads/writes quickly
Survive server failures
Add capacity without downtime
```

---

## Table of Contents

1. [Consistent Hashing](#consistent-hashing)
2. [Sharding Without Replication](#sharding-without-replication)
3. [Sharding With Master-Slave Replication](#sharding-with-master-slave-replication)
4. [Database Orchestration](#database-orchestration)
5. [Adding New Servers](#adding-new-servers)
6. [Reserved Servers](#reserved-servers)
7. [Seamless Shard Creation](#seamless-shard-creation)
8. [Consistency During Shard Creation](#consistency-during-shard-creation)
9. [Sharding With Multi-Master Replication](#sharding-with-multi-master-replication)
10. [Multi-Master Consistency](#multi-master-consistency)
11. [Replica Placement](#replica-placement)
12. [Load Balancing and Routing](#load-balancing-and-routing)
13. [Final Principles](#final-principles)

---

## Consistent Hashing

Consistent hashing is a way to distribute keys across servers or shards while minimizing data movement when the cluster changes.

Traditional hashing often looks like this:

```text
server = hash(key) mod number_of_servers
```

The problem is that when `number_of_servers` changes, many keys may move.

```text
Before: hash(user_7) mod 3 → Server B
After:  hash(user_7) mod 4 → Server D
```

That causes large rebalancing work.

Consistent hashing solves this by placing both keys and nodes on a circular hash space called a **hash ring**.

```text
                 0 / 360
                    |
          Server D  |     Server A
              ●     |        ●
                    |
     270 -----------+----------- 90
                    |
              ●     |        ●
          Server C  |     Server B
                    |
                  180
```

### Key Placement on the Ring

Each key is hashed to a position on the ring. The key is assigned to the first node found while moving clockwise.

```text
Ring direction: clockwise

        key_1
          ●
          ↓ clockwise
        Server A owns key_1

     ● Server D              ● Server A


     ● Server C              ● Server B
```

Simplified ownership:

```text
Key hash falls between Server D and Server A → stored by Server A
Key hash falls between Server A and Server B → stored by Server B
Key hash falls between Server B and Server C → stored by Server C
Key hash falls between Server C and Server D → stored by Server D
```

### Adding a New Server

When a new server joins, only the keys in its new range need to move.

```text
Before:

Server A owns range: D → A

     ● Server D              ● Server A


After adding Server X:

     ● Server D     ● Server X     ● Server A

Server X now owns only: D → X
Server A keeps: X → A
```

This is the big benefit:

```text
Only nearby keys move.
Most keys stay where they are.
```

### Hashing Under Different Settings

| Approach | What Happens When Servers Change? |
|---|---|
| `hash(key) mod N` | Many keys move when `N` changes |
| Consistent hashing | Only affected ranges move |
| Consistent hashing with virtual nodes | Load spreads more evenly |

Virtual nodes are multiple positions per physical server.

```text
Physical Server A appears as:

A1, A2, A3, A4 on the ring

This avoids one server owning one huge unlucky range.
```

---

## Sharding Without Replication

### What Is Sharding?

Sharding means splitting data horizontally across multiple logical partitions.

```text
All Users
   ↓
Sharding Function
   ↓
┌─────────┬─────────┬─────────┐
│ Shard 1 │ Shard 2 │ Shard 3 │
└─────────┴─────────┴─────────┘
```

Each shard stores only a subset of the data.

### Shard vs Server

A **shard** is a logical data partition.

A **server** is a physical or virtual machine that stores and serves data.

They are related, but not the same.

```text
Logical view:

Shard 1: users 0-999
Shard 2: users 1000-1999
Shard 3: users 2000-2999

Physical view:

Server A hosts Shard 1
Server B hosts Shard 2
Server C hosts Shard 3
```

A server can host multiple shards:

```text
Server A → Shard 1, Shard 4
Server B → Shard 2, Shard 5
Server C → Shard 3, Shard 6
```

### What Gets Placed on the Consistent-Hashing Ring?

Usually, the ring should place **logical shards** or **virtual nodes**, not just raw physical servers.

```text
Better:

Key → Hash Ring → Logical Shard → Healthy Server

Less flexible:

Key → Hash Ring → Physical Server
```

Placing logical shards on the ring gives the orchestrator more control. It can move a shard from one server to another without changing the key-to-shard mapping.

### Can a Shard Go Down?

Without replication, yes.

```text
Shard 2 lives only on Server B

Server B crashes
   ↓
Shard 2 unavailable
   ↓
Users belonging to Shard 2 fail
```

Diagram:

```text
Shard 1 → Server A → OK
Shard 2 → Server B → DOWN
Shard 3 → Server C → OK

Only Shard 2 data is unavailable.
```

Sharding improves scalability, but by itself it does not guarantee high availability.

---

## Sharding With Master-Slave Replication

Replication means keeping multiple copies of the same shard on different servers.

In master-slave replication, one replica is the **master** or **primary** for writes. Other replicas are **slaves**, **followers**, or **secondaries**.

```text
Shard 1 Replica Set

          Writes
            ↓
        Master A
          /   \
         /     \
  Replica B   Replica C

Reads may go to master or replicas, depending on consistency needs.
```

### Shard Placement on the Ring

The ring maps keys to shards. The shard metadata then tells the router which servers hold that shard.

```text
user_123
   ↓
hash(user_123)
   ↓
Hash Ring
   ↓
Shard 7
   ↓
Replica Set:
   Master: Server A
   Slave:  Server C
   Slave:  Server D
```

### Replication Factor

Replication Factor, or **RF**, is the number of copies of each shard.

```text
RF = 1 → one copy, no redundancy
RF = 2 → two copies, can survive one copy loss in many cases
RF = 3 → common choice for stronger availability
RF = 4 → more durability, more storage and network cost
```

Example:

```text
Shard 12, RF = 3

Shard 12 copy 1 → Server A
Shard 12 copy 2 → Server B
Shard 12 copy 3 → Server C
```

### What Happens When a Server Crashes?

Suppose Server A is the master for Shard 1.

```text
Before failure:

Shard 1:
  Server A → Master
  Server B → Slave
  Server C → Slave

Server A crashes.
```

The orchestrator or consensus system promotes a replica.

```text
After failover:

Shard 1:
  Server B → New Master
  Server C → Slave
  Server A → Down
```

Then the system tries to restore the desired replication factor.

```text
Current RF = 2
Target RF  = 3

Create new copy:
  Shard 1 → Server D
```

### Master Election

Master election chooses a new master when the old one fails.

The system should avoid split brain:

```text
Bad:

Server A thinks it is master
Server B also thinks it is master

Two masters accept writes → conflicts or data loss
```

Many systems use consensus or coordination services for this, such as Raft, Paxos, ZooKeeper, etcd, or a database-specific control plane.

---

## Database Orchestration

Database orchestration means managing the cluster automatically.

The orchestrator knows:

- Which shards exist
- Which servers exist
- Which replicas belong to each shard
- Which replica is master
- Which servers are healthy
- Which shards are overloaded
- What the target replication factor is
- What data movement is currently happening

```text
                 ┌────────────────────┐
                 │    Orchestrator     │
                 └─────────┬──────────┘
                           │
       ┌───────────────────┼───────────────────┐
       │                   │                   │
┌──────▼──────┐     ┌──────▼──────┐     ┌──────▼──────┐
│ Config Store│     │ Health Check│     │ Rebalancer  │
└─────────────┘     └─────────────┘     └─────────────┘
       │                   │                   │
       ▼                   ▼                   ▼
Shard mappings      Server status       Data movement
Replica sets        Disk/CPU/load       Add/remove nodes
Policies            Lag/errors          Restore RF
```

### Cluster Configuration

Configuration management stores facts like:

```text
Cluster size
Shard mappings
Replica sets
Master assignments
Server capacity
Credentials and access policies
Replication factor
Failure domains
Migration status
```

Example metadata:

```text
Shard 8:
  key range: 1000-1999
  master: Server B
  replicas: Server B, Server D, Server F
  status: healthy
  target RF: 3
```

### Orchestrator Responsibilities

The orchestrator handles:

- Health monitoring
- Failure detection
- Replica failover
- Master election coordination
- Re-replication
- Rebalancing
- Adding and removing servers
- Updating cluster configuration
- Enforcing placement rules
- Avoiding overloaded servers

In short:

```text
Observe → Decide → Simulate → Execute → Verify → Update Metadata
```

---

## Adding New Servers

New servers may be added for several reasons:

- Expanding capacity
- Creating new shards
- Increasing replication
- Replacing failed servers
- Replacing overloaded servers
- Adding read replicas

### What Should the Orchestrator Do?

When servers join, the orchestrator should not blindly place data on them. It should evaluate the cluster.

```text
New servers join
   ↓
Validate health and capacity
   ↓
Compare current load with target load
   ↓
Simulate shard movement
   ↓
Choose migration plan
   ↓
Execute gradually
```

### Should New Servers Be Assigned to Existing Shards?

Sometimes yes.

Use new servers for existing shards when:

- Existing servers are overloaded
- Replication factor is below target
- Read traffic needs more replicas
- A server recently failed

```text
Before:

Shard 4 → Server A, Server B
Target RF = 3

After adding Server C:

Shard 4 → Server A, Server B, Server C
```

### Should New Shards Be Created?

Create new shards when existing shards are too large or too hot.

```text
Before:

Shard 1 owns users 0-9999

After split:

Shard 1 owns users 0-4999
Shard 9 owns users 5000-9999
```

Not every new server needs a new shard. A server is capacity. A shard is a data partition.

```text
Add 10 servers
Possible actions:

1. Move existing replicas to balance load
2. Add replicas to hot shards
3. Split a few large shards
4. Keep some servers reserved
```

---

## Reserved Servers

Reserved servers are spare capacity kept available for failures, spikes, or planned growth.

They do not always need to be completely idle. They can run light workloads, analytics, backup validation, or low-priority replicas, but the cluster should be able to free them quickly.

### Why Keep Spare Capacity?

Reserved capacity helps with:

- Failed-server replacement
- New shard creation
- Capacity expansion
- Extra read replicas
- Rebalancing after a hot spot
- Faster recovery after a crash

```text
Cluster:

Active servers:   A B C D E F
Reserved servers: R1 R2

Server C crashes
   ↓
Orchestrator copies C's shard replicas to R1
   ↓
Target RF restored
```

### Optimal Number of Reserved Servers

There is no universal number. It depends on:

- Replication factor
- Failure domain size
- Recovery time objective
- Data size per server
- Expected growth
- Traffic spikes
- Cost tolerance

Common thinking:

```text
Keep enough spare capacity to survive expected failures
and rebalance without overloading the remaining cluster.
```

---

## Seamless Shard Creation

Seamless shard creation means creating or splitting shards without taking the system offline.

A safe approach is a two-phase process:

```text
Simulation Phase → Real Phase
```

### Simulation Phase

The simulation phase is a dry run.

```text
Input:
  Current ring
  Current shard sizes
  Current server load
  Target RF
  New servers

Calculate:
  New ring
  New key ranges
  Data movement
  Impacted servers
  Expected disk usage
  Expected network usage
  Migration time
  Failure risk
```

Diagram:

```text
Current cluster metadata
          ↓
    Dry-run planner
          ↓
Proposed cluster metadata
          ↓
Validate thresholds
          ↓
Approve or reject plan
```

### Real Phase

The real phase applies the migration plan carefully.

```text
1. Update metadata with migration state
2. Create target shard/replicas
3. Copy existing data
4. Stream new writes or deltas
5. Catch up until lag is small
6. Switch routing
7. Validate reads and counts
8. Clean old data
9. Mark migration complete
```

Overall flow:

```text
Add Servers
   ↓
Orchestrator
   ↓
Simulate
   ↓
Execute
   ↓
Rebalance and Sync
   ↓
Stable State
```

---

## Consistency During Shard Creation

The hardest part of shard creation is serving reads and writes while data is moving.

### Option 1: Pause Affected Writes

This is simpler but hurts availability.

```text
Migration starts
   ↓
Block writes for affected key range
   ↓
Copy data
   ↓
Switch routing
   ↓
Enable writes
```

Use this only when downtime or partial write blocking is acceptable.

### Option 2: Online Migration With Delta Copying

This is more common for high-availability systems.

```text
Step 1: Bulk copy existing data
Step 2: Copy changes that happened during Step 1
Step 3: Copy later changes again
Step 4: Repeat until lag is tiny
Step 5: Briefly lock or coordinate final switch
Step 6: Route new traffic to new shard
```

Text diagram:

```text
Old Shard
   │
   ├── Bulk snapshot ───────────────► New Shard
   │
   ├── Delta 1: writes during copy ─► New Shard
   │
   ├── Delta 2: later writes ───────► New Shard
   │
   └── Final catch-up ──────────────► New Shard

Then routing switches.
```

### Serving Requests During Migration

One common pattern is dual tracking:

```text
Writes:
  Write to old shard
  Record change in migration log
  Apply change to new shard

Reads:
  Read from old shard until cutover
  Or read from new shard only after validation
```

During the final cutover:

```text
1. Stop or coordinate affected writes briefly
2. Apply final delta
3. Validate new shard
4. Update routing metadata
5. Resume traffic using new shard
```

### When to Delete Old Data

Do not delete old shard data immediately.

Clean up only after:

- Data counts match
- Checksums or sampled records match
- Replication has caught up
- New routing is stable
- Rollback window has passed
- Backups exist

```text
Migrate → Validate → Route → Observe → Cleanup
```

This avoids data loss and inconsistent reads.

---

## Sharding With Multi-Master Replication

In multi-master replication, multiple replicas can accept writes.

```text
Shard 5, RF = 4

Server A ◄────► Server B
   ▲              ▲
   │              │
   ▼              ▼
Server C ◄────► Server D

All can accept writes for Shard 5.
```

### What Gets Placed on the Ring?

The ring usually maps a key to a shard or replica set.

```text
user_42
   ↓
hash(user_42)
   ↓
Shard 5
   ↓
Replicas:
  Server A
  Server B
  Server C
  Server D
```

Any healthy replica may accept the write, depending on routing and consistency policy.

### Can a Shard Go Down?

A shard goes down only when not enough replicas are available to satisfy the required consistency level.

```text
RF = 4
Write quorum = 3

Available replicas = 4 → OK
Available replicas = 3 → OK
Available replicas = 2 → Cannot satisfy quorum
```

### How Data Reaches Other Servers

Multi-master systems use background replication and repair mechanisms:

- Peer-to-peer replication
- Gossip
- Anti-entropy
- Merkle trees
- Read repair
- Hinted handoff

Simplified:

```text
Client writes to Server A
        ↓
Server A stores write locally
        ↓
Server A sends write to B, C, D
        ↓
Slow or failed replicas catch up later
```

### Hinted Handoff

If a replica is down, another server temporarily stores the missed write as a hint.

```text
Server D is down

Write intended for D
   ↓
Server B stores hint for D
   ↓
D recovers
   ↓
B replays hint to D
```

### Merkle Trees and Anti-Entropy

Merkle trees help compare large datasets efficiently.

```text
Replica A data summary
        ↓
Merkle tree hash
        ↓
Compare with Replica B
        ↓
Only different ranges are repaired
```

---

## Multi-Master Consistency

Multi-master systems often provide **eventual consistency** or **tunable consistency**.

### Eventual Consistency

If no new writes happen, replicas eventually converge to the same value.

```text
Time 1:
Server A has value = X
Server B has old value

Time 2:
Replication happens

Time 3:
Server A and Server B both have value = X
```

### Tunable Consistency

The client or database can choose how many replicas must acknowledge reads and writes.

```text
RF = 3

Write CL = ONE     → fast, weaker consistency
Write CL = QUORUM  → balanced
Write CL = ALL     → strongest, slowest
```

Quorum rule:

```text
R + W > RF

R = read replicas required
W = write replicas required
RF = replication factor
```

Example:

```text
RF = 3
R = 2
W = 2

R + W = 4 > 3

At least one replica overlaps between read and write.
```

### Handling Concurrent Writes

Concurrent writes can conflict.

```text
User updates profile on Server A
User updates same profile on Server B

Both writes happen before replication catches up.
```

Conflict handling options:

- Last-write-wins
- Version vectors
- Timestamps
- Application-level merge
- Conflict records that humans or services resolve

---

## Replica Placement

Replica placement decides which servers hold copies of each shard.

Good placement avoids putting all replicas in the same failure domain.

```text
Bad:

Shard 7 replicas:
  Server A in Rack 1
  Server B in Rack 1
  Server C in Rack 1

Rack 1 fails → Shard 7 unavailable
```

Better:

```text
Shard 7 replicas:
  Server A in Rack 1
  Server D in Rack 2
  Server G in Rack 3
```

### Consistent Hashing for Replica Placement

With RF = 4:

```text
Key → Primary position on ring
    → First suitable server
    → Next suitable server
    → Next suitable server
    → Next suitable server
```

Example:

```text
user_123 belongs to Shard 9
RF = 4

Shard 9 replicas:
  Server A
  Server C
  Server F
  Server H
```

The placement policy should consider:

- Server capacity
- Rack or zone
- Existing load
- Disk usage
- Network cost
- Current replica count

---

## Load Balancing and Routing

Routing is the path from client request to the correct shard and replica.

```text
Client
  ↓
Router / Coordinator
  ↓
Find shard using key
  ↓
Find healthy replica
  ↓
Send request
```

### Master-Slave Routing

```text
Write request
   ↓
Router
   ↓
Shard 3 master

Read request
   ↓
Router
   ↓
Shard 3 master or read replica
```

Diagram:

```text
          ┌────────────┐
Client ──►│   Router   │
          └─────┬──────┘
                │
        ┌───────▼───────┐
        │ Shard Metadata │
        └───────┬───────┘
                │
        ┌───────▼────────┐
        │ Shard 3 Master │
        └────────────────┘
```

### Multi-Master Routing

In multi-master replication, the router may choose from several healthy replicas.

```text
Client write
   ↓
Router chooses replica using:
  - Round-robin
  - Least connections
  - Lowest latency
  - Local region
  - Load score
   ↓
Selected master replica
```

Example:

```text
Shard 8 replicas:
  Server A: latency 20 ms, load 80%
  Server B: latency 35 ms, load 20%
  Server C: latency 10 ms, load 90%

Router may choose Server B if it prefers lower load.
Router may choose Server C if it prefers lowest latency.
```

---

## Final Principles

### Quick Recap

```text
Consistent Hashing → Minimize data movement
Sharding           → Distribute data horizontally
Replication        → Improve availability and durability
Orchestration      → Automate cluster management
Replication Factor → Decide how many copies exist
Rebalancing        → Keep load even as the cluster changes
Failover           → Promote healthy replicas after failure
Recovery           → Rejoin repaired servers safely
```

### Important Orchestration Principles

- Monitor health, load, disk, network, and replication lag.
- Keep minimum replication factor at all times.
- Automate failure handling and recovery.
- Simulate changes before applying them.
- Rebalance gradually to avoid overwhelming the cluster.
- Avoid hot spots by tracking key distribution and traffic.
- Place replicas across failure domains.
- Validate migrated data before deleting old copies.
- Keep reserved capacity for failures and growth.
- Update configuration atomically and consistently.

### End-to-End Mental Model

```text
Client Request
     ↓
Router / Coordinator
     ↓
Consistent Hashing
     ↓
Logical Shard
     ↓
Replica Set
     ↓
Healthy Server
     ↓
Read / Write
     ↓
Replication
     ↓
Orchestrator watches and repairs
```

### Failure Recovery Flow

```text
Server fails
   ↓
Health monitor detects failure
   ↓
Orchestrator marks server unavailable
   ↓
Replica is promoted if needed
   ↓
Traffic is rerouted
   ↓
Missing replicas are rebuilt elsewhere
   ↓
Cluster returns to target RF
```

### Adding Capacity Flow

```text
Add servers
   ↓
Validate servers
   ↓
Simulate new placement
   ↓
Create or move shards
   ↓
Copy data
   ↓
Catch up deltas
   ↓
Switch routing
   ↓
Clean old data
   ↓
Stable cluster
```

The core idea:

```text
Do not think only in terms of servers.

Think in terms of:

Keys → Shards → Replica Sets → Servers → Orchestrator
```

