# ledger-service

A small account ledger microservice — deposits, withdrawals, balance
lookups — built to be a compact, complete example of hexagonal
architecture: pure domain logic, protocol-defined ports, and swappable
adapters for real infrastructure (DynamoDB, Kafka).

## Why hexagonal architecture

The domain logic (`ledger.domain.ledger`) has no idea whether it's backed
by an in-memory atom or a real DynamoDB table. It takes and returns plain
maps, has zero I/O, and is fully unit-testable without Docker, AWS
credentials, or a running broker. Everything that touches the outside
world — persistence, event publishing — is expressed as a protocol
(`ledger.ports.*`), with concrete adapters plugged in at the edge
(`ledger.main`).

The payoff shows up concretely in the commit history: the DynamoDB
adapter and the Kafka adapter were each added without a single line of
`ledger.domain.ledger` or `ledger.http` changing. That's not a
theoretical benefit of the architecture — it's the actual diff.

```
                      ┌─────────────────────────┐
                      │   ledger.http (Reitit)   │
                      │  routes → calls ports    │
                      └────────────┬─────────────┘
                                   │
                      ┌────────────▼─────────────┐
                      │  ledger.domain.ledger     │
                      │  pure functions, no I/O   │
                      │  deposit / withdraw /     │
                      │  balance                  │
                      └────────────┬─────────────┘
                                   │ (via protocols)
              ┌────────────────────┼────────────────────┐
              │                    │                     │
   ┌──────────▼─────────┐ ┌────────▼────────┐            
   │   LedgerStore       │ │ EventPublisher  │            
   │   (port/protocol)   │ │ (port/protocol) │            
   └──────────┬─────────┘ └────────┬────────┘            
              │                    │
     ┌────────┴────────┐  ┌────────┴────────┐
     │                 │  │                 │
┌────▼─────┐    ┌──────▼────┐  ┌───────▼──────┐
│ in-memory│    │ DynamoDB  │  │  in-memory /  │
│  (atom)  │    │ (Local or │  │  Kafka /      │
│          │    │ real AWS) │  │  Redpanda     │
└──────────┘    └───────────┘  └───────────────┘
```

## Layout

```
src/ledger/domain     pure business logic (deposit/withdraw/balance), no I/O
src/ledger/ports      protocols: LedgerStore, EventPublisher
src/ledger/adapters   concrete implementations of the ports
src/ledger/http.clj   Reitit routes + handlers, calling domain via ports
src/ledger/metrics.clj Prometheus registry (request counts, latency)
src/ledger/main.clj   entrypoint: picks adapters, starts the HTTP server
```

| Port            | Adapters                          |
|-----------------|------------------------------------|
| `LedgerStore`    | in-memory (atom), DynamoDB (Local or real AWS) |
| `EventPublisher` | in-memory (atom), Kafka (or any Kafka-protocol-compatible broker, e.g. Redpanda) |

## Running it

Requires the [Clojure CLI](https://clojure.org/guides/install_clojure).

```bash
clojure -M:run
```

By default this uses the in-memory adapters — no external services needed.

### With Docker Compose (real DynamoDB Local + Redpanda)

```bash
docker compose up --build
```

Wires the service to DynamoDB Local and Redpanda automatically — see
`docker-compose.yml` for the environment variables it sets.

### Manually, against DynamoDB Local

```bash
docker run -d -p 8000:8000 amazon/dynamodb-local

AWS_ACCESS_KEY_ID=fake AWS_SECRET_ACCESS_KEY=fake AWS_REGION=us-east-1 \
  LEDGER_STORE=dynamodb clojure -M:run
```

The table (`ledger-accounts`) is created automatically on startup if it
doesn't exist — dev/test convenience only, not how a real deployment would
provision infrastructure.

### Manually, against Kafka / Redpanda

```bash
docker run -d -p 9092:9092 redpandadata/redpanda start --smp 1 \
  --overprovisioned --node-id 0 \
  --kafka-addr PLAINTEXT://0.0.0.0:9092 \
  --advertise-kafka-addr PLAINTEXT://localhost:9092

LEDGER_EVENT_PUBLISHER=kafka clojure -M:run
```

Publishes a `transaction-recorded` event (JSON) to that topic on every
deposit/withdrawal.

Both adapters are selected independently, so any combination of
`LEDGER_STORE` / `LEDGER_EVENT_PUBLISHER` env vars works.

## API

| Method | Path                        | Body               | Description                    |
|--------|-----------------------------|--------------------|--------------------------------|
| GET    | `/health`                   | —                  | Liveness check                 |
| GET    | `/metrics`                  | —                  | Prometheus exposition (request counts, latency) |
| GET    | `/accounts/:id/balance`     | —                  | Current balance (404 if unknown) |
| POST   | `/accounts/:id/deposit`     | `{"amount": 100}`  | Deposit; creates the account if it doesn't exist |
| POST   | `/accounts/:id/withdraw`    | `{"amount": 100}`  | Withdraw; 400 on insufficient funds or invalid amount |

```bash
curl -X POST localhost:3000/accounts/abc/deposit \
  -H 'Content-Type: application/json' -d '{"amount": 100}'
curl localhost:3000/accounts/abc/balance
```

## Testing

```bash
clojure -M:test
```

Currently covers the pure domain logic (`ledger.domain.ledger`): deposits,
withdrawals, invalid-amount rejection, insufficient-funds rejection. Runs
in CI (`.github/workflows/ci.yml`) on every push and PR to `main`, followed
by a Docker build (and, on `main`, a push to GHCR).

## Deployment

- `Dockerfile` — single-stage image on the official Clojure tools-deps base
- `docker-compose.yml` — service + DynamoDB Local + Redpanda, wired together for local dev
- `k8s/deployment.yaml`, `k8s/service.yaml` — a plausible Kubernetes deployment (2 replicas, `/health` readiness and liveness probes, AWS credentials from a Secret). Written to be correct and reviewable, not to actually be applied to a cluster — there's no cluster behind this project.

## Implemented so far

- Pure domain logic for deposit/withdraw/balance
- `LedgerStore` and `EventPublisher` ports (protocols)
- In-memory adapters for both, wired end-to-end via HTTP
- DynamoDB (Local-compatible) `LedgerStore` adapter
- Kafka-backed `EventPublisher` adapter
- Prometheus `/metrics` (request counts, latency histograms)
- Dockerfile + docker-compose (service, DynamoDB Local, Redpanda)
- CI: test → build → push image to GHCR on `main`
- Kubernetes manifests (deployment + service)
- Unit tests for the domain logic

## What I'd do with more time

- **Datomic alongside DynamoDB.** The posting calls out both; DynamoDB
  gives fast key-value lookups for current balance, but Datomic's
  bitemporal model (querying "what did this account look like as of last
  Tuesday, as we believed it at the time") is a much more natural fit for
  a ledger's actual requirement — a full, queryable transaction history,
  not just current state. I'd add it as a second `LedgerStore`
  implementation, likely used for the audit/history read path while
  DynamoDB serves the hot balance-lookup path.
- **A real Kubernetes deployment**, including a Helm chart or Kustomize
  overlays per environment, an actual EKS cluster, and IAM Roles for
  Service Accounts (IRSA) instead of the static AWS credentials the
  manifest currently pulls from a Secret.
- **Finagle-style RPC** between services if this ledger were split into
  collaborators (e.g. a separate account/identity service) — right now
  everything is a single deployable, so there's no inter-service call to
  make.
- **Idempotency keys** on deposit/withdraw — right now a retried request
  double-applies. A real payments path needs a client-supplied request ID
  the store can dedupe on.
- **Kafka producer error handling** — `.send` is currently fire-and-forget;
  a production publisher would check delivery acks or attach a callback
  rather than silently drop failures.
