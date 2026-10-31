# Cab Service — System Design Deep Dive (Interview Prep)

For each decision: what we chose, what else we considered, why the alternative lost, and what trade-off we accepted. Use this to explain *reasoning*, not just the final architecture — that's what interviews actually probe for.

---

## 1. Service boundaries: modular monolith, not microservices (v1)

**Chosen:** One Spring Boot API deployable + one separate Worker deployable (outbox processor). Logical service boundaries (matching, trip, payment) inside the monolith as separate packages/modules.

**Alternative considered:** Full microservices (separate deployables for matching, trip, payment, notification).

**Why monolith won for v1:** Microservices add network calls, distributed tracing, service discovery, and partial-failure handling — real costs that only pay off once a *team* needs independent deploy cadence per service, or one module has wildly different scaling needs than another. Solo project, no team, no proven scale requirement yet → that cost isn't justified.

**Why we still split out the Worker:** This wasn't "microservices," it was recognizing the worker has a genuinely different resource profile (background polling vs request/response) and needs independent scaling — the same reasoning that would eventually justify splitting the WebSocket gateway out too, at real scale.

**Interview framing:** "I chose a modular monolith with clear internal boundaries so it could be split into services later without a rewrite — only the worker was split early because its scaling trigger (notification volume) is genuinely independent of API traffic."

---

## 2. Geospatial matching: Redis GEO, not PostGIS (v1)

**Chosen:** Redis `GEOADD`/`GEOSEARCH` for live driver locations.

**Alternative considered:** PostgreSQL + PostGIS with a GiST spatial index.

**Why Redis won:** Driver location changes every ~2 seconds per driver. That's a write-heavy, latency-sensitive workload. PostGIS would mean maintaining a disk-backed GiST index under constant high-frequency writes — expensive, and unnecessary since location data is inherently ephemeral (old position is worthless the instant a new one arrives). Redis is in-memory, so writes and nearest-radius reads are both sub-millisecond.

**Why we didn't fully discard PostGIS:** It's still the right tool for *durable* geo data (e.g., saved addresses, service-area boundaries) — just wrong for a value that changes every 2 seconds.

**What we explicitly deferred:** Straight-line radius search, not road-distance search. Real systems (Uber) use S2/H3 grid indexing plus a two-phase search (cheap broad filter on straight-line candidates, then expensive road-distance/ETA call via a routing engine on just those few candidates). We named this as a known gap, not an oversight — worth explicitly saying so in an interview: "v1 trades routing accuracy for simplicity; the upgrade path is a two-phase search, which I can explain."

---

## 3. Driver-accept race condition: Redis SETNX + fencing token

**Chosen:** `SET trip:{id}:lock <token> NX EX 30`, with the unlock step done via an atomic Lua script comparing the token before deleting.

**Alternative considered (naive):** Database row lock (`SELECT ... FOR UPDATE`) directly on the trip row at accept time, with no Redis involved.

**Why Redis+SETNX won over a bare DB lock:** A DB transaction lock ties up a connection for the duration of the request and doesn't naturally self-expire — if the app crashes mid-transaction, the lock can hang until a timeout/rollback. Redis's `EX` TTL gives automatic self-healing (max 30 sec of a stuck trip) without relying on crash-recovery logic.

**Why the fencing token, specifically:** Without it, a delayed cleanup (driver A's expired lock) could race with a new valid lock (driver B) and delete the wrong one — classic check-then-act bug. The token makes the delete conditional on "only if this is still *my* lock," and it must be atomic (Lua `EVAL`), because a plain GET-then-DEL has the same race one level down.

**Interview framing:** "I used Redis as a distributed lock with a TTL for self-healing and a fencing token to prevent a stale lock-holder from deleting someone else's valid lock — this is the same reasoning behind the Redlock algorithm."

---

## 4. Trip assignment + notification: transactional outbox, not direct push

**Chosen:** Postgres transaction writes (trip status + driver assignment + outbox row) together; a separate worker polls the outbox and publishes notifications.

**Alternative considered:** Just call the notification/WebSocket push directly inside the same request that confirms the trip (no outbox).

**Why outbox won:** If you push the notification directly and the process crashes *after* the DB commit but *before* the push succeeds, the trip is assigned but the rider never finds out — a silent, hard-to-detect failure. Writing the "a notification is owed" fact into the *same* DB transaction as the business state change guarantees they succeed or fail together. The actual delivery can then be retried independently without risking double-writing business state.

**Trade-off accepted:** Slight delivery delay (worker poll interval) and at-least-once delivery (receiver must tolerate duplicate notifications, typically via an idempotency key).

**Why `SKIP LOCKED` for the worker, not plain `SELECT`:** Multiple worker instances polling the same query would otherwise grab and process the same rows twice. `FOR UPDATE SKIP LOCKED` lets concurrent workers partition the queue automatically with no coordination between them.

---

## 5. Real-time delivery: WebSocket + Redis connection registry + Pub/Sub

**Chosen:** Each driver/rider holds one WebSocket connection to *some* server instance; a Redis key (`conn:driver:{id} -> serverX`) tracks which instance; cross-server messages go through Redis Pub/Sub, which every instance subscribes to.

**Alternative considered:** Sticky sessions / assume one server for everyone (no registry).

**Why the registry won even for a single-instance v1:** WebSockets are stateful — a connection lives on exactly one process. The moment you have more than one instance (which you will, under real traffic), Server B (handling an HTTP request) has no direct way to reach a socket open on Server A. Building the registry from day one costs almost nothing extra now and avoids a structural rewrite later. This is a case of paying a small design cost upfront to avoid a large one later — worth saying explicitly in an interview as a conscious trade-off, not default complexity.

---

## 6. Live location stream: Pub/Sub (ephemeral), kept separate from the outbox (durable)

**Chosen:** Location pings go over Redis Pub/Sub, completely bypassing Postgres and the outbox in the hot path. Only the *latest* position is cached (`SET ... EX 60`, overwrite).

**Why not route this through the same durable pipeline as trip events:** The outbox guarantees *no event is lost*. Losing one location ping out of a stream that repeats every 2 seconds is irrelevant — the next ping supersedes it. Paying a full DB-transaction cost per ping (for potentially thousands of concurrent drivers) would be pure waste for data with no durability requirement.

**Why isolate it from business-event traffic at the transport/processing level, not just by channel name:** Two different Redis channels still share the same server process and resource pool. A burst of location traffic could starve processing of critical events (OTP results, assignment confirmations) even with separate channels. The fix is isolating by resource contention profile — separate processing pools now, a fully separate gateway service if/when scale demands it.

**What's explicitly NOT stored:** Full GPS trail. Only trip start/end locations are persisted for history — storing every 2-second ping forever is a cost/value trade-off we consciously declined for v1.

---

## 7. Price locking: immutable at acceptance, decoupled from live distance tracking

**Chosen:** `quoted_fare` is written once to the trip row at driver-acceptance time and never recalculated from actual route distance.

**Why this separation matters:** Live GPS tracking exists for map display and (optionally) trip-path history — it must never feed back into billing. If it did, a driver taking a longer-than-estimated route (traffic, detour, GPS drift) would silently change what the rider is charged after they already agreed to a price — a trust violation. Keeping pricing and live-tracking as fully separate code paths, with pricing locked immutably at one specific moment, removes the possibility of that bug entirely rather than relying on discipline to avoid it.

**Explicitly deferred:** Comparing actual route vs. estimated route as a driver-efficiency/fraud signal. Real systems do this, but it's a trust-and-safety feature layered on top of a working core loop, not part of it.

---

## 8. OTP: Redis-only, not persisted in Postgres

**Chosen:** OTP code + attempt counter + regeneration counter all live in Redis with TTLs; never written to the trip row in Postgres.

**Why Redis, not Postgres, and why this differs from the trip/ledger data:** The OTP has zero value after trip start and no audit requirement (unlike payment, which must be durable and explainable). It's short-lived and single-purpose — the same profile as the accept-lock, so it gets the same treatment.

**Why `INCR` for attempts rather than logging each guess:** The *count* of wrong attempts is all that's needed to enforce a cap; storing each individual guessed value would add complexity (a list to clear on regeneration) for information that's never used. This is a case of matching the data structure to the actual access pattern, not defaulting to "store everything just in case."

**Why cap both wrong-attempts (3) and total regenerations (3):** Uncapped attempts = brute-force risk (a 4-digit OTP has only 10,000 combinations — trivial to script through without a limit). Uncapped regenerations = a dishonest driver could deliberately fail OTP checks to force cancellations onto a rider, so the *system* (not just the rider) needs a hard ceiling that results in a clean, traceable outcome (auto-cancel with a distinct reason code) rather than an infinite retry loop.

---

## 9. Payments: immutable ledger + cached balance, not a single mutable balance field

**Chosen:** Every trip writes an immutable `ledger_entries` row (`OWES_APP` or `APP_OWES_DRIVER`); a `current_balance` field on the driver is updated incrementally in the same transaction as a fast read-optimized cache; periodic reconciliation re-sums the ledger to detect drift.

**Alternative considered:** Just maintain one mutable `balance` column, update it per trip, no ledger table.

**Why the ledger won:** A single mutable number gives you a result with no explanation — if a driver disputes their balance, or you find a bug in your own calculation logic later, there's no history to audit against. Financial data specifically needs to be explainable after the fact, not just correct in the moment. This is standard practice in real accounting/fintech systems: never mutate state directly, append immutable facts and derive current state from them.

**Why keep a cached balance at all, instead of just `SUM()`-ing the ledger on every read:** At scale (thousands of ledger rows per active driver over time), summing on every wallet view is unnecessary repeated I/O for a value that only changes on new events. The cache is the fast read path; the ledger remains the source of truth and the thing reconciliation checks against.

**Why this is the same pattern as the matching lock, twice removed:** Both are "don't trust a single read-then-write — make it atomic or transactional." The lock uses `SET NX`; the balance update uses an atomic `UPDATE ... SET balance = balance + ?` (or `SELECT FOR UPDATE`) to avoid a lost update when two trips complete for the same driver near-simultaneously. Recognizing this as the *same underlying problem* reappearing is the actual system-design skill being demonstrated here.

---

## 10. Ratings: cached average via incremental mean update

**Chosen:** `average_rating` + `total_ratings_count` cached on the driver row; new rating folds in via `new_avg = ((old_avg × old_count) + new_rating) / (old_count + 1)`, inside a transaction with a row lock to prevent the same lost-update race as balance.

**Why not recompute `AVG()` from the ratings table on every profile view:** Same reasoning as balance — O(1) cached read vs. an unbounded scan that grows worse as a driver accumulates history.

**Why server-side validation matters here specifically:** The business rule ("rating ≤ 2 requires a comment") only has integrity if enforced where it can't be bypassed. A frontend-only check (especially one built quickly/by AI) can always be skipped by calling the API directly. The backend is the only trust boundary that counts — this is a general principle, not specific to ratings, and it should be stated as such if asked: *never rely on frontend validation for anything that affects data integrity or business rules.*

---

## 11. The pattern underneath all of it (good to say explicitly in an interview)

Almost every hard decision in this system reduces to one of three repeated techniques:

1. **Short-lived, high-churn data → Redis with TTL** (locks, OTP, live location) — because durability and audit-ability aren't needed, and in-memory speed is.
2. **Durable, audit-critical data → Postgres, append-only where correctness matters** (trips, ledger entries, ratings, outbox events) — because you need to explain *how* a value was reached, not just what it currently is.
3. **Concurrent read-modify-write on a shared value → atomic operation or transactional row lock** (SETNX+fencing, ledger balance update, rating average update, outbox `SKIP LOCKED`) — because naive read-then-write always has a race window under concurrency.

Being able to say "this module is a different name for a problem I already solved in module X" is a stronger signal in an interview than knowing any single pattern in isolation — and it's genuinely how you arrived at most of these decisions in this conversation, not something imposed after the fact.
