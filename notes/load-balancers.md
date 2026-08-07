# System Design & Networking Fundamentals — Part 3

## Load Balancers Explained

When an application receives thousands or millions of requests, sending all traffic to a single server quickly creates a bottleneck. That server has limited CPU, memory, network capacity, and concurrent connections—and if it fails, the entire application may become unavailable.

A **load balancer** solves this problem by acting as a traffic manager between clients and a pool of servers.

> The key idea: distribute traffic intelligently so that one server does not become a bottleneck or a single point of failure.

### ✍️ Handwritten Notes

- [View the handwritten study sheet](../assets/handwritten/load-balancers/Load_Balancers_Handwritten_Notes.png)
- [Download the printable PDF](../assets/handwritten/load-balancers/Load_Balancers_Handwritten_Notes.pdf)

---

## 📚 Table of Contents

1. [What Is a Load Balancer?](#what-is-a-load-balancer)
2. [Why Do We Need Load Balancing?](#why-do-we-need-load-balancing)
3. [The Basic Request Flow](#the-basic-request-flow)
4. [What Happens Inside a Load Balancer?](#what-happens-inside-a-load-balancer)
5. [Load-Balancing Algorithms](#load-balancing-algorithms)
6. [Layer 4 and Layer 7 Load Balancing](#layer-4-and-layer-7-load-balancing)
7. [Health Checks and Failover](#health-checks-and-failover)
8. [Listeners, Rules, and Target Groups](#listeners-rules-and-target-groups)
9. [Load Balancers and Reverse Proxies](#load-balancers-and-reverse-proxies)
10. [Scaling the Load-Balancing Layer](#scaling-the-load-balancing-layer)
11. [Sessions and State](#sessions-and-state)
12. [Benefits and Trade-Offs](#benefits-and-trade-offs)
13. [Key Takeaways](#key-takeaways)

---

## What Is a Load Balancer?

A load balancer receives incoming connections or requests and distributes them across multiple backend servers.

Its main responsibilities are to:

- Accept traffic through a stable entry point
- Track which backend servers are available
- Select a suitable healthy server
- Forward each connection or request
- Return the server's response to the client
- Stop routing traffic to unhealthy servers

The servers behind a load balancer are often called:

- **Backends**
- **Targets**
- **Upstreams**
- **Origin servers**

The exact term depends on the platform or product.

---

## Why Do We Need Load Balancing?

### Without a Load Balancer

```text
Clients ──────────────→ Single Server
                         ├── Limited capacity
                         ├── Possible overload
                         └── Single point of failure
```

All requests compete for the resources of one machine. Vertical scaling—adding more CPU or memory—can help temporarily, but it has physical and financial limits.

### With a Load Balancer

```text
                         ┌──→ Server 1
Clients → Load Balancer ─┼──→ Server 2
                         └──→ Server 3
```

The application can scale horizontally by adding servers to the pool. If one server becomes unhealthy, the load balancer can direct new traffic to the remaining healthy servers.

> A load balancer removes the backend server as a single point of failure only when multiple healthy backends exist. The load-balancing layer must also be deployed redundantly.

---

## The Basic Request Flow

The simplest flow is:

```text
Client → Load Balancer → Healthy Server → Load Balancer → Client
```

A more realistic cloud request path is:

```text
User
  ↓
DNS
  ↓
Load Balancer
  ↓
Listener
  ↓
Routing Rule
  ↓
Target Group
  ↓
Healthy Backend
  ↓
Response
```

### Step-by-Step Example

1. The user requests `https://api.example.com/products`.
2. DNS resolves the hostname to an address for the load-balancing service.
3. The load balancer accepts the connection on its HTTPS listener.
4. It may terminate TLS and inspect the HTTP request.
5. Routing rules match the host and path.
6. A target group is selected.
7. A balancing algorithm chooses a healthy target.
8. The request is forwarded to that target.
9. The response returns through the load balancer to the client.

---

## What Happens Inside a Load Balancer?

When traffic arrives, a load balancer typically performs several operations.

### 1️⃣ Accept the Connection

A **listener** waits for connections on a protocol and port, such as:

| Protocol | Common port |
|---|---:|
| HTTP | `80` |
| HTTPS | `443` |
| gRPC | Commonly `443` or `50051` |
| PostgreSQL | `5432` |
| MySQL | `3306` |

### 2️⃣ Apply Security and Connection Policies

Depending on its capabilities, the load balancer may:

- Terminate TLS
- Validate protocol settings
- Apply access-control rules
- Enforce connection or request limits
- Attach forwarding headers

### 3️⃣ Evaluate Routing Rules

An application-layer load balancer can route using properties such as:

- Hostname
- URL path
- HTTP method
- Headers
- Query parameters
- Source IP address

### 4️⃣ Select a Healthy Target

The load balancer considers only targets that are eligible and healthy, then applies its configured selection algorithm.

### 5️⃣ Forward the Request

It opens or reuses a backend connection and sends the request to the selected server.

### 6️⃣ Relay the Response

The response is returned to the client. The load balancer may also add headers, compress data, collect metrics, or record access logs.

---

## Load-Balancing Algorithms

Different algorithms optimize for different traffic and server characteristics.

### Round Robin

Requests are distributed sequentially:

```text
Request 1 → Server A
Request 2 → Server B
Request 3 → Server C
Request 4 → Server A
```

**Best suited for:** Similar servers and requests with roughly similar processing costs.

**Limitation:** It does not account for how busy each server already is.

### Least Connections

The next request goes to the server with the fewest active connections.

```text
Server A: 100 active connections
Server B:  20 active connections  ← selected
Server C:  70 active connections
```

**Best suited for:** Long-lived connections or requests with uneven processing times.

**Limitation:** Connection count does not always represent actual CPU or memory load.

### IP Hash

A hash of the client's IP address determines the backend:

```text
hash(client IP) → Server B
```

This can repeatedly route the same client IP to the same server.

**Best suited for:** Simple session affinity.

**Limitations:**

- Many users may share one IP because of NAT or proxies.
- A client's IP can change.
- Adding or removing servers may remap clients.
- Uneven client activity can create hot spots.

### Weighted Round Robin

Servers receive traffic in proportion to assigned weights:

```text
Server A: weight 5
Server B: weight 3
Server C: weight 2
```

Over time, Server A receives approximately half of the traffic.

**Best suited for:** Backend servers with different capacities or gradual deployments.

### Least Response Time

Traffic is sent using a combination of observed response time and active connections.

**Best suited for:** Pools where backend latency changes over time.

**Limitation:** Measurements must be continually updated, and short-term latency does not always predict the cost of the next request.

### Random or Power of Two Choices

A load balancer randomly samples two servers and selects the less-loaded one. This approach is inexpensive and can provide good distribution in large pools.

### Consistent Hashing

A request attribute—such as a client, cache key, or tenant—is mapped to a position on a hash ring. When servers change, only some keys need to move.

**Best suited for:** Distributed caches and systems where stable key-to-server mapping matters.

---

## Layer 4 and Layer 7 Load Balancing

Load balancers are commonly classified by the network information they use.

| Capability | Layer 4 Load Balancer | Layer 7 Load Balancer |
|---|---|---|
| Operates on | TCP/UDP connections | HTTP/HTTPS requests |
| Understands IP addresses and ports | Yes | Yes |
| Understands paths and headers | No | Yes |
| Content-based routing | No | Yes |
| Typical overhead | Lower | Higher |
| Common use cases | TCP, UDP, databases, games | Websites, APIs, microservices |

### Layer 4: Transport Layer

A Layer 4 load balancer routes connections using information such as:

- Source and destination IP
- Source and destination port
- Transport protocol

It does not need to understand the application payload.

### Layer 7: Application Layer

A Layer 7 load balancer understands HTTP semantics and can route requests such as:

```text
api.example.com/*  → API target group
example.com/admin/* → Admin target group
example.com/images/* → Image target group
```

This enables flexible routing but requires more processing and a deeper understanding of the protocol.

---

## Health Checks and Failover

A load balancer should send traffic only to backends that can serve it correctly.

### Active Health Checks

The load balancer periodically probes each target:

```text
GET /health

Server 1 → 200 OK      → Healthy
Server 2 → Timeout     → Unhealthy
Server 3 → 200 OK      → Healthy
```

Health-check configuration commonly includes:

- Protocol, port, and path
- Check interval
- Timeout
- Healthy threshold
- Unhealthy threshold
- Expected status codes or response

Requiring several consecutive successes or failures prevents a single temporary error from repeatedly adding and removing a server.

### Passive Health Checks

Some load balancers also observe real user traffic. Repeated connection failures, timeouts, or server errors can temporarily remove a target from rotation.

### Failure Flow

```text
                         ┌──→ Server 1 ✓
Client → Load Balancer ──┼─X→ Server 2 ✗
                         └──→ Server 3 ✓
```

When Server 2 fails its health checks:

1. It is marked unhealthy.
2. New requests are sent to Servers 1 and 3.
3. Existing connections may finish, fail, or be retried depending on the protocol and configuration.
4. After Server 2 recovers and passes its healthy threshold, it returns to rotation.

> A health endpoint should verify that the server can actually handle traffic, but it should avoid expensive checks that create their own failures.

---

## Listeners, Rules, and Target Groups

Cloud load balancers often organize configuration into three main concepts.

### Listener

A listener defines where the load balancer accepts traffic:

```text
HTTP  :80
HTTPS :443
TCP   :3306
```

An HTTPS listener also references a TLS certificate and security policy.

### Routing Rule

Rules decide where a request should go:

```text
Host = api.example.com     → API targets
Path starts with /products → Product targets
Path starts with /admin    → Admin targets
Default                    → Web targets
```

Rules usually have priorities. The first matching rule is applied, followed by a default action when no specific rule matches.

### Target Group

A target group is a logical collection of backend resources that receive traffic:

- Virtual machines
- Containers
- IP addresses
- Serverless functions, when supported

Each group can have its own:

- Health-check configuration
- Protocol and port
- Load-balancing algorithm
- Session-affinity settings

---

## Load Balancers and Reverse Proxies

A **reverse proxy** accepts requests on behalf of backend servers. It hides the backend topology from clients and forwards traffic to internal services.

```text
Client → Reverse Proxy → Private Backend
```

Many modern products perform both reverse-proxy and load-balancing functions:

```text
Client → Reverse Proxy / Load Balancer → Backend Pool
```

The concepts overlap, but their emphasis differs:

- **Reverse proxy:** Intermediates and controls access to one or more backends.
- **Load balancer:** Distributes work across multiple backends.

Common capabilities include TLS termination, caching, compression, authentication, header manipulation, and request routing.

---

## Scaling the Load-Balancing Layer

Adding backend servers does not help if one load-balancer instance becomes the next bottleneck or single point of failure.

A production design may use:

- Multiple load-balancer instances
- Health-checked DNS records
- Anycast addressing
- An active-active or active-passive deployment
- A managed cloud load-balancing service
- Regional and global load-balancing layers

```text
                       ┌──→ Load Balancer A ──→ Backend Pool
Clients → DNS / Anycast┤
                       └──→ Load Balancer B ──→ Backend Pool
```

The load-balancing layer itself must be monitored and scaled for:

- Connections per second
- Concurrent connections
- Requests per second
- Bandwidth
- TLS handshakes
- Latency and error rate

---

## Sessions and State

Horizontal scaling works best when application servers are **stateless**.

### Stateless Backends

Any server can process any request because shared state is stored outside the server:

```text
Application Servers → Shared Cache / Database / Object Storage
```

This makes scaling, deployment, and failover easier.

### Sticky Sessions

Session affinity attempts to route a client to the same backend using:

- A load-balancer cookie
- An application cookie
- An IP hash

Sticky sessions can help legacy stateful applications, but they introduce trade-offs:

- Traffic may become uneven.
- A server failure can still lose in-memory session state.
- Scaling and deployments become harder.

Whenever practical, externalizing session state is more resilient than depending on affinity.

---

## Benefits and Trade-Offs

### Benefits

- **High availability:** Failed backends can be removed from rotation.
- **Horizontal scalability:** Servers can be added or removed as demand changes.
- **Better performance:** Work is distributed and overloaded targets can be avoided.
- **Operational flexibility:** Backends can be deployed or maintained gradually.
- **Security:** Private backend addresses can remain hidden.
- **TLS offloading:** Centralized certificate handling reduces repeated backend work.
- **Traffic management:** Rules support APIs, microservices, and gradual releases.

### Trade-Offs

- **Additional latency:** Traffic passes through another network hop.
- **Cost:** Managed services or redundant infrastructure consume resources.
- **Configuration complexity:** Incorrect timeouts, health checks, or rules cause failures.
- **New failure domain:** A poorly designed load-balancing tier can become a single point of failure.
- **Observability challenges:** The original client identity and request context must be forwarded safely.

---

## Key Takeaways

- A load balancer distributes traffic across a pool of backend servers.
- Health checks prevent new traffic from reaching failed targets.
- Algorithms such as round robin, least connections, weighted routing, and hashing solve different problems.
- Layer 4 balances transport connections; Layer 7 can inspect and route HTTP requests.
- Listeners accept traffic, rules select a destination, and target groups contain backends.
- Stateless application servers are easier to balance and scale.
- The load-balancing tier must itself be redundant, observable, and scalable.

> 🚀 One entry point, many healthy servers, and intelligent traffic distribution.

---

## Continue the Learning Path

- **Previous:** Part 2 — DNS in [`dns.md`](./dns.md)
- **Next:** Part 4 — Data Sharding & Routing in [`data-sharding-routing.md`](./data-sharding-routing.md)
