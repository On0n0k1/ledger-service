# ledger-service

A small account ledger microservice — deposits, withdrawals, balance
lookups — built with hexagonal architecture in Clojure. Domain logic is
pure and has no idea whether it's backed by an in-memory atom or a real
DynamoDB table; persistence and event publishing are swappable adapters
behind protocols.

## Architecture

```
src/ledger/domain     pure business logic (deposit/withdraw/balance), no I/O
src/ledger/ports      protocols: LedgerStore, EventPublisher
src/ledger/adapters   concrete implementations of the ports
src/ledger/http.clj   Reitit routes + handlers, calling domain via ports
src/ledger/main.clj   entrypoint: picks adapters, starts the HTTP server
```

Two adapters exist per port so far:

| Port            | Adapters                          |
|-----------------|------------------------------------|
| `LedgerStore`    | in-memory (atom), DynamoDB (Local or real AWS) |
| `EventPublisher` | in-memory (atom), Kafka (or any Kafka-protocol-compatible broker, e.g. Redpanda) |

## Running it

Requires the [Clojure CLI](https://clojure.org/guides/install_clojure).

```bash
clj -M:run
```

By default this uses the in-memory adapters — no external services needed.

### Against DynamoDB Local

```bash
docker run -d -p 8000:8000 amazon/dynamodb-local

AWS_ACCESS_KEY_ID=fake AWS_SECRET_ACCESS_KEY=fake AWS_REGION=us-east-1 \
  LEDGER_STORE=dynamodb clj -M:run
```

The table (`ledger-accounts`) is created automatically on startup if it
doesn't exist — dev/test convenience only, not how a real deployment would
provision infrastructure.

### Against Kafka / Redpanda

```bash
docker run -d -p 9092:9092 redpandadata/redpanda start --smp 1 \
  --overprovisioned --node-id 0 \
  --kafka-addr PLAINTEXT://0.0.0.0:9092 \
  --advertise-kafka-addr PLAINTEXT://localhost:9092

LEDGER_EVENT_PUBLISHER=kafka clj -M:run
```

Publishes a `transaction-recorded` event (JSON) to that topic on every
deposit/withdrawal.

Both adapters are selected independently, so any combination of
`LEDGER_STORE` / `LEDGER_EVENT_PUBLISHER` env vars works.

## API

| Method | Path                        | Body               | Description                    |
|--------|-----------------------------|--------------------|--------------------------------|
| GET    | `/health`                   | —                  | Liveness check                 |
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
clj -M:test
```

Currently covers the pure domain logic (`ledger.domain.ledger`): deposits,
withdrawals, invalid-amount rejection, insufficient-funds rejection.

## Implemented so far

- Pure domain logic for deposit/withdraw/balance
- `LedgerStore` and `EventPublisher` ports (protocols)
- In-memory adapters for both, wired end-to-end via HTTP
- DynamoDB (Local-compatible) `LedgerStore` adapter
- Kafka-backed `EventPublisher` adapter
- Unit tests for the domain logic

## Not yet implemented

- Prometheus `/metrics` endpoint
- Dockerfile / `docker-compose.yml` tying the service + DynamoDB Local +
  Redpanda together
- CI (GitHub Actions): test → build → image
- Kubernetes manifests (`deployment.yaml` / `service.yaml`)
