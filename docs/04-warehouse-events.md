# Warehouse Event Schema — Enterprise Texting Gateway
**Stream:** outbox table → RabbitMQ `etg.events` → NDJSON files (`./warehouse/events-YYYY-MM-DD.ndjson`) → `GET /v1/exports/events`
**Grain:** one row per domain event. Message bodies are never exported (SHA-256 hash only, PII minimization).

## Envelope (every event)
| Field | Type | Notes |
|---|---|---|
| `id` | bigint | Monotonic cursor for `sinceId` pagination |
| `aggregateType` | string | `message` \| `delivery` \| `consent` |
| `aggregateId` | string | Row id, or `{phone}:{topic}` for consent |
| `eventType` | string | See below |
| `payload` | object | Event-specific fields |
| `createdAt` / `publishedAt` | ISO-8601 | `publishedAt` null until the relay publishes |

## Event types
### `message.created`
`payload: {to, topic, sid, key}` — every accepted send (incl. agent replies, renewals, campaigns).

### `delivery.updated`
`payload: {sid, status, errorCode}` — every Twilio DLR. Join to `message.created` on `sid`.

### `consent.updated`
`payload: {phone, topic, oldStatus, newStatus, source, actor}` — every opt-in/out with proof source.

## Consumption
- **Cursor export:** `GET /v1/exports/events?sinceId=0&limit=100` → `{events[], nextCursor}` (limit clamped to 1000).
- **Snowflake:** `COPY INTO etg_events FROM @stage/events-*.ndjson FILE_FORMAT=(TYPE=NDJSON);`
- **BigQuery:** `bq load --source_format=NEWLINE_DELIMITED_JSON dataset.etg_events events-*.ndjson`
- **Suggested marts:** `delivery_rate by topic/day` (created vs delivered), `opt_out_rate by campaign`,
  `consent_coverage`, `agent_sla` (join inbox export when added), `cost per segment`.

## SLOs
Warehouse lag <5 min (relay tick 5s + loader schedule). Export is read-only; relay owns `publishedAt`.
