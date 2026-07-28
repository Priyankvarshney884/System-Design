# System Design & Distributed Systems — Part 5

## Hashing Fundamentals

Hashing appears everywhere in software engineering: hash tables, caches, database indexes, sharding, request routing, integrity checks, and distributed systems.

At its core, hashing follows a simple flow:

```text
Input Key
   ↓
Hash Function
   ↓
Fixed-Size Hash Value
   ↓
Bucket / Server / Position
```

For example:

```text
"user_123"
     ↓
Hash Function
     ↓
872351
```

A hash function maps an input of arbitrary size to a fixed-size value. The output helps a system locate, distribute, compare, or verify information efficiently.

---

### ✍️ Handwritten Notes

- [View the handwritten study sheet](./handWrittenPdfs/Hashing_Fundamentals_Handwritten_Notes.png)
- [Download the printable PDF](./handWrittenPdfs/Hashing_Fundamentals_Handwritten_Notes.pdf)

---

## 📚 Table of Contents

1. [What Is a Hash Function?](#what-is-a-hash-function)
2. [How Hashing Works](#how-hashing-works)
3. [Properties of a Good Hash Function](#properties-of-a-good-hash-function)
4. [Hash Space, Buckets, and the Modulo Operation](#hash-space-buckets-and-the-modulo-operation)
5. [What Is a Hash Collision?](#what-is-a-hash-collision)
6. [Collision-Handling Strategies](#collision-handling-strategies)
7. [Load Factor and Rehashing](#load-factor-and-rehashing)
8. [Hashing in Distributed Systems](#hashing-in-distributed-systems)
9. [Traditional Distributed Hashing](#traditional-distributed-hashing)
10. [General-Purpose and Cryptographic Hashing](#general-purpose-and-cryptographic-hashing)
11. [Password Hashing Is Different](#password-hashing-is-different)
12. [Universal Hashing](#universal-hashing)
13. [Common Mistakes](#common-mistakes)
14. [Choosing a Hash Function](#choosing-a-hash-function)
15. [Key Takeaways](#key-takeaways)

---

## What Is a Hash Function?

A hash function accepts input data—often called a **key**—and produces a fixed-width value called a **hash**, **hash code**, or **digest**, depending on the context.

```text
h(key) = hash value
```

The input may be:

- A string
- An integer
- A file
- A database identifier
- A serialized object
- A network request attribute

The output size is fixed for a particular function even when the inputs have different sizes:

```text
h("cat")                  → fixed-size value
h("a much longer input")  → fixed-size value
```

### Hashing Is Not Encryption

Hashing and encryption solve different problems:

| Hashing | Encryption |
|---|---|
| Designed as a one-way transformation | Designed to be reversible with a key |
| Produces a fixed-size result | Usually preserves information needed for recovery |
| Used for lookup, distribution, and verification | Used for confidentiality |
| Hash collisions are possible | Correct decryption recovers the original plaintext |

Encoding is different again: it changes representation and is normally reversible without a secret.

---

## How Hashing Works

A simplified hash-table lookup looks like this:

```text
Key
 ↓
Hash Function
 ↓
Hash Value
 ↓
Bucket Index
 ↓
Stored Entry
```

Suppose a table contains eight buckets:

```text
hash("user_123") = 872351
bucket           = 872351 mod 8
bucket           = 7
```

The record is placed in or retrieved from Bucket 7.

### Insert

```text
1. Hash the key.
2. Convert the hash into a bucket index.
3. Resolve a collision if the bucket is occupied.
4. Store the key-value pair.
```

### Lookup

```text
1. Hash the same key again.
2. Compute the same bucket index.
3. Search that bucket using the table's collision strategy.
4. Compare the original key before returning the value.
```

The original key comparison matters because different keys may share a hash or bucket.

---

## Properties of a Good Hash Function

The ideal properties depend on the use case, but several are broadly useful.

### Uniform Distribution

Hash values should spread keys evenly across the available output space.

```text
Poor distribution:  [■■■■■■][■][ ][ ]
Good distribution:  [■■][■■][■■][■■]
```

Uniform distribution reduces:

- Overloaded buckets
- Long collision chains
- Uneven shard traffic
- Cache hot spots

The input distribution matters too. A function that behaves well for random keys may perform differently on structured or adversarial inputs.

### Deterministic Output

The same input must produce the same output under the same function and configuration:

```text
h("user_123") → 872351
h("user_123") → 872351
```

Determinism makes future lookups and routing possible.

Some language runtimes intentionally randomize a hash seed between processes for security. The function is deterministic within that configured execution, but its result may not be stable across restarts. Persisted or distributed mappings therefore need an explicitly stable algorithm and encoding.

### Fast Computation

General-purpose hashing should be inexpensive because it may run millions of times per second.

The function should avoid becoming more expensive than the operation it supports.

### Avalanche Behaviour

A small input change should significantly change the output:

```text
h("user_123") → 872351
h("user_124") → 194027
```

Good avalanche behaviour reduces correlations between similar keys and improves distribution.

### Low Practical Collision Rate

A wide, well-designed hash makes accidental collisions unlikely for a realistic number of inputs. It cannot make them mathematically impossible when the input space is larger than the output space.

---

## Hash Space, Buckets, and the Modulo Operation

These three values are related but not identical.

### Hash Value

The direct output from the function:

```text
h("order_42") = 293847561
```

### Hash Space

The complete set of possible outputs. A 32-bit hash has:

```text
2³² possible values
```

### Bucket Index

The selected slot among a smaller number of buckets:

```text
bucket = hashValue mod bucketCount
```

Many distinct hash values can map to the same bucket even if the direct hash values are different.

### Two Types of Collision

It is useful to distinguish:

1. **Hash collision:** two different inputs produce exactly the same hash value.
2. **Bucket collision:** two hash values map to the same bucket after reduction.

Hash tables must handle bucket collisions as a normal event.

---

## What Is a Hash Collision?

A collision occurs when different inputs produce the same hash value:

```text
Key A ──┐
        ├──→ Same Hash Value
Key B ──┘
```

Collisions are unavoidable in principle because the number of possible inputs is usually much larger than the number of possible outputs. This follows from the **pigeonhole principle**.

### Why Collisions Matter

Too many collisions can cause:

- Slower inserts and lookups
- Long chains or probe sequences
- Uneven load
- Denial-of-service risks with adversarial keys

### The Birthday Effect

Collisions become likely sooner than the full hash space might suggest. For an ideal `b`-bit hash, a collision becomes likely after roughly:

```text
2^(b/2) inputs
```

This is one reason cryptographic collision resistance requires a sufficiently large digest.

---

## Collision-Handling Strategies

### Chaining

Each bucket stores a collection of entries:

```text
Bucket 0 → [Key A, Value A]
Bucket 1 → [Key B, Value B] → [Key C, Value C]
Bucket 2 → empty
```

**Advantages:**

- Simple deletion
- Table can temporarily hold more entries than buckets
- Performance degrades gradually with increasing load

**Trade-Offs:**

- Additional memory for links or collections
- Poor cache locality in pointer-based implementations
- Long chains when distribution is poor

### Open Addressing

All entries stay inside the table. If the selected slot is occupied, the algorithm probes other slots.

#### Linear Probing

```text
index, index + 1, index + 2, ...
```

Simple and cache-friendly, but it can form clusters.

#### Quadratic Probing

The probe distance grows quadratically:

```text
index + 1², index + 2², index + 3², ...
```

This reduces primary clustering but requires compatible table sizing and probing rules.

#### Double Hashing

A second hash determines the probe step:

```text
index = h1(key)
step  = h2(key)
```

It generally spreads probes better but computes another hash.

### Cuckoo Hashing

Each key has multiple candidate locations. Inserting a new key may displace another key to its alternative location.

It provides fast worst-case lookups, while inserts may trigger several relocations or a rebuild.

### Rehashing

When the table becomes too full, it allocates a larger bucket array and reinserts entries using the new bucket count.

Rehashing is a resizing operation, not merely a way to compare colliding keys.

---

## Load Factor and Rehashing

The **load factor** measures how full a hash table is:

```text
load factor α = number of entries / number of buckets
```

For example:

```text
entries = 6
buckets = 8
α       = 0.75
```

As the load factor rises:

- Chaining produces longer average chains.
- Open addressing has fewer empty slots and longer probes.

Many implementations resize after a configured threshold.

### Resize Flow

```text
Old Table
   ↓ threshold reached
Allocate Larger Table
   ↓
Recompute Bucket Indexes
   ↓
Move Entries
```

Entries must be redistributed because the bucket count has changed:

```text
hash(key) mod 8  ≠  hash(key) mod 16
```

The resize is expensive at that moment, but its cost is amortized across many operations.

---

## Hashing in Distributed Systems

Hashing can map a key to a bucket, partition, cache node, or server:

```text
User Key
   ↓
Stable Hash Function
   ↓
Hash Value
   ↓
Shard / Cache Node / Server
```

### Common Uses

- Database sharding
- Distributed caches
- Request affinity
- Data partitioning
- Peer-to-peer lookup
- Deduplication
- Content-addressed storage

### Distributed Requirements

A distributed mapping needs more than a good local hash:

- Every participant must use the same algorithm.
- Keys must use the same byte encoding.
- Hash results must have identical numeric interpretation.
- Membership changes must be coordinated.
- Replication and failure handling must be defined.
- The mapping must not rely on a runtime-specific hash that changes after restart.

---

## Traditional Distributed Hashing

A simple strategy for `N` servers is:

```text
serverIndex = hash(key) mod N
```

For three servers:

```text
hash("user_123") mod 3 → Server 1
```

### Advantages

- Easy to implement
- Fast direct lookup
- Reasonably even placement with a good hash

### The Membership-Change Problem

If the number of servers changes:

```text
Before: hash(key) mod 3
After:  hash(key) mod 4
```

Many keys receive a different result. Adding or removing one server can remap a large fraction of the data.

This causes:

- Large data migrations
- Cache misses
- Extra network traffic
- Backend load spikes
- Longer rebalancing windows

Consistent hashing addresses this problem by limiting how much of the keyspace moves when membership changes.

---

## General-Purpose and Cryptographic Hashing

Not all hash functions are designed for the same goal.

### General-Purpose Hashing

Used for hash tables, partitioning, checks within trusted systems, and fast routing.

Primary goals often include:

- Speed
- Good distribution
- Good avalanche behaviour
- Low accidental collision rate

Examples include families such as MurmurHash and xxHash. Their exact suitability depends on the workload and threat model.

### Cryptographic Hashing

Used when attackers may deliberately try to reverse, alter, or collide inputs.

Important properties include:

#### Preimage Resistance

Given a hash `h`, it should be computationally infeasible to find an input `x` such that:

```text
H(x) = h
```

#### Second-Preimage Resistance

Given one input `x`, it should be difficult to find a different input `y` with:

```text
H(x) = H(y)
```

#### Collision Resistance

It should be difficult to find any two different inputs with the same digest:

```text
H(x) = H(y), where x ≠ y
```

Cryptographic functions additionally aim to resist structural and statistical attacks, not merely accidental collisions.

### Examples

- SHA-256 and SHA-3 are modern cryptographic hash families.
- MD5 and SHA-1 are broken for collision-sensitive security uses.
- A cryptographic hash may be unnecessarily expensive for an ordinary in-memory hash table.
- A fast general-purpose hash is unsafe for digital signatures or adversarial integrity checks.

---

## Password Hashing Is Different

Passwords should not be stored with a fast general-purpose hash or a single fast cryptographic digest.

A password-hashing function is intentionally expensive and uses a unique random **salt**:

```text
Password + Unique Salt
          ↓
Password-Hashing Function
          ↓
Stored Verification Value
```

Modern password storage commonly uses:

- Argon2id
- scrypt
- bcrypt
- PBKDF2 when required by an environment or standard

The configured cost slows offline guessing attacks. The salt prevents identical passwords from automatically producing identical stored values and defeats precomputed rainbow tables.

> Fast is desirable for routing and hash tables; deliberately expensive is desirable for password verification.

---

## Universal Hashing

**Universal hashing** randomly selects a function from a carefully designed family of hash functions.

```text
Choose h randomly from family H
                ↓
             h(key)
```

For any two distinct keys, the family provides a probabilistic upper bound on their chance of collision.

### Why It Helps

If an attacker does not know in advance which function will be selected, constructing a large set of colliding keys becomes harder.

Universal hashing is useful in:

- Randomized hash tables
- Algorithms with expected-time guarantees
- Adversarial or unpredictable input environments
- Data structures where deterministic worst-case input patterns are risky

Universal hashing is a mathematical strategy for collision probability. It does not automatically provide the full security properties of a cryptographic hash.

---

## Common Mistakes

### Treating Hashes as Unique IDs

A hash has a collision probability. If correctness requires uniqueness, the system must compare original values or use a proper unique-identifier strategy.

### Using an Unstable Runtime Hash

Some built-in language hashes vary across process restarts or versions. Do not persist them or use them as a cross-service routing contract unless stability is explicitly guaranteed.

### Ignoring Key Encoding

These may be different byte sequences:

```text
"42"
integer 42
UTF-8 text
UTF-16 text
```

Distributed participants must agree on canonical serialization.

### Using Modulo with a Changing Server Count

`hash(key) mod N` is simple, but changing `N` remaps many keys.

### Confusing Checksums with Secure Integrity

A checksum can detect accidental corruption but may not resist deliberate modification. Security-sensitive integrity needs a cryptographic construction, often an HMAC or digital signature.

### Storing Passwords with SHA-256 Alone

A fast digest enables fast password guessing. Use a password-hashing function with a salt and an appropriate cost.

### Ignoring Adversarial Collisions

A fast, predictable hash table can be attacked with many colliding keys. Seeded hashing, universal hashing, collision-resistant data structures, or request limits may be appropriate.

---

## Choosing a Hash Function

Start with the purpose and threat model.

| Use case | Primary requirement | Typical category |
|---|---|---|
| In-memory hash table | Very fast, good distribution | General-purpose hash |
| Shard or cache routing | Stable, portable, uniform | Stable non-cryptographic hash |
| Untrusted hash-table keys | Resist collision attacks | Seeded or randomized hashing |
| File integrity against accidents | Fast change detection | Checksum or fast digest |
| Security-sensitive integrity | Resist deliberate tampering | Cryptographic hash or HMAC |
| Digital signatures | Collision-resistant digest | Modern cryptographic hash |
| Password storage | Slow and memory-hard | Password-hashing function |

Also evaluate:

- Output width
- Input sizes
- CPU architecture
- Portability
- Stable serialization
- Library maturity
- Hardware acceleration
- Expected number of keys
- Adversarial input risk

Avoid inventing a custom hash function for production use.

---

## Key Takeaways

- A hash function maps arbitrary-size input to a fixed-size output.
- Determinism makes repeated lookup and routing possible.
- Uniform distribution prevents overloaded buckets, shards, and servers.
- Collisions are mathematically unavoidable and must be handled.
- Chaining and open addressing are common hash-table collision strategies.
- Hash-table load factor affects performance and triggers resizing.
- `hash(key) mod N` distributes data simply but remaps many keys when `N` changes.
- General-purpose, cryptographic, password, and universal hashing solve different problems.
- Distributed hashing requires a stable algorithm and canonical key encoding.
- Consistent hashing is the next step for handling changing server membership with less data movement.

> 🔑 Hashing converts data into a predictable, efficiently computable value that helps us locate, distribute, or verify information.

---

## Continue the Learning Path

- **Previous:** Part 4 — Data Sharding & Routing in [`data-sharding-routing.md`](./data-sharding-routing.md)
- **Next:** Consistent Hashing — minimizing data movement when servers join or leave

