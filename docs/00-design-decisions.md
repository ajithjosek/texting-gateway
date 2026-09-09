# Design Decisions (ADRs) — Enterprise Texting Gateway
**Date:** 2026-09-09 | **Status:** Accepted for v1 | **Deploys:** Linux VM + Docker Compose (1 install/customer, connected on-prem), portable to AWS/GCP/Azure

Locked context: Java-first team, SMS/MMS + WhatsApp/RCS via Twilio, workflows = servicing/renewals + billing + claims/FNOL + agent 1:1 + marketing, integrations = Salesforce + Guidewire + warehouse.

## ADR-001: Java 21 + Spring Boot 3.3 (over Rust / .NET / Node)
- **Decision:** Java 21 LTS, Spring Boot 3.3, Virtual Threads, Maven, Jib/distroless images.
- **Why:** team is Java-comfortable; official Twilio Java SDK; mature SOAP/REST for Guidewire/Salesforce; LTS + hiring pool; Virtual Threads give Rust-class throughput for webhook/DLR fan-out without reactive complexity.
- **Rejected:** Rust (no official Twilio SDK, slower enterprise integration), .NET (good for Guidewire but team preference Java), Node (weaker transactional guarantees for consent/audit).
- **Consequence:** larger images than Rust (~180MB vs ~20MB) — acceptable on VM; enforce `cargo-like` hygiene via `mvn enforcer + OWASP check`.

## ADR-002: Single-JVM modular monolith first (over microservices)
- **Decision:** One Spring Boot app, strict package modules (`consent`, `messaging`, `inbox`, `journey`, `hub.*`), Postgres schemas per module. Split to services only if CAT scale demands it.
- **Why:** single VM v1, one install/customer — ops simplicity, single Flyway chain, single backup. Virtual Threads handle concurrency.
- **Consequence:** module boundaries enforced by ArchUnit tests; outbox table preserves future service split.

## ADR-003: Postgres 16 + Redis 7 + RabbitMQ (over Kafka / cloud-native queues)
- **Decision:** Postgres (source of truth + outbox), Redis (idempotency/SLA/rate-limit), RabbitMQ (domain events) in Compose. Cloud queues behind Spring Cloud Stream binder abstraction.
- **Why:** all run in Docker on one VM; no Kafka ops burden; swap binder to SQS/PubSub/Service Bus in cloud with zero business-logic change.
- **Consequence:** cap steady throughput at ~5k msg/min on VM; document migration to managed Kafka/Redpanda if burst >100 MPS sustained.

## ADR-004: S3-compatible storage + MinIO locally
- **Decision:** code against S3 API only (`software.amazon.awssdk:s3`). Compose runs MinIO; cloud uses S3/GCS/Azure Blob (via S3 gateway or adapter).
- **Why:** MMS media + 7y archive must be portable; MinIO gives WORM/object-lock semantics locally.

## ADR-005: Keycloak bundled (over direct Okta/Entra)
- **Decision:** Keycloak 24 in Compose as OIDC broker. App trusts Keycloak issuer only. In cloud, Keycloak federates to Okta/Entra or is removed and issuer URL repointed.
- **Why:** works offline-ish/on-prem with no SaaS IdP dependency; same `spring-boot-starter-oauth2-resource-server` path everywhere.

## ADR-006: Docker Compose on Linux VM (over k3s/K8s in v1)
- **Decision:** Compose profile `app, db, redis, rabbitmq, minio, keycloak, frontend, observability`. Helm chart deferred to Phase 2 but images are Helm-ready (12-factor env, no host mounts except volumes).
- **Why:** lowest ops for single-customer VM; `install.sh` + `backup.sh` sufficient. K8s would add ops without scale need.

## ADR-007: Twilio as delivery plane, custom consent gate
- **Decision:** Twilio Messaging Services + Conversations + Lookup + Content API. Every send passes local `ConsentService` BEFORE Twilio call. Twilio Advanced Opt-Out enabled as second line, not primary.
- **Why:** TCPA proof + quiet-hours + caps are product IP; must survive carrier/Twilio retry duplication via `Idempotency-Key` + `messages.idempotency_key UNIQUE`.

## ADR-008: Next.js static frontend (over Vaadin/Thymeleaf-fullstack)
- **Decision:** React + Next.js static export + Nginx container for Agent Inbox + Admin + Preference Center. Backend is pure REST + OpenAPI.
- **Why:** richer inbox UX (realtime via SSE/WebSocket), portable static asset, Java team not blocked on JS build (separate `frontend/`).

---

## Diagrams

### C4 Context
```mermaid
flowchart LR
  PH[Policyholder handset<br/>SMS/WhatsApp/RCS] <--> TW[Twilio Cloud<br/>Messaging/Conversations/Lookup]
  TW <--> GW[Enterprise Texting Gateway<br/>Java 21 + Spring Boot<br/>this product]
  GW <--> SF[Salesforce CRM]
  GW <--> CW[Guidewire Policy/Claim/Billing]
  GW <--> PAY[Stripe/InvoiceCloud + DocuSign]
  GW --> WH[Snowflake/BigQuery<br/>warehouse]
  AG[Agent/CSR browser<br/>Inbox + Admin UI] --> GW
  KC[Keycloak<br/>OIDC broker] --> GW
```

### Container (VM Compose)
```mermaid
flowchart TB
  subgraph VM[Linux VM - docker compose]
    CADDY[Caddy :443<br/>TLS + reverse proxy]
    APP[app :8080<br/>Spring Boot modular monolith]
    FE[frontend :80<br/>Next.js static + Nginx]
    PG[(Postgres 16<br/>+ Flyway)]
    RD[(Redis 7)]
    RMQ[RabbitMQ<br/>domain events]
    MINIO[(MinIO<br/>S3-compat media/archive)]
    KC[Keycloak :8081<br/>OIDC]
    OBS[Prometheus + Grafana + Loki<br/>profile: observability]
    CADDY --> APP
    CADDY --> FE
    CADDY --> KC
    APP --> PG
    APP --> RD
    APP --> RMQ
    APP --> MINIO
    APP --> KC
  end
  TW[Twilio] <--> CADDY
  SF[Salesforce] <--> APP
  GW2[Guidewire] <--> APP
```

### Outbound send sequence (consent-gated, idempotent)
```mermaid
sequenceDiagram
  participant Core as Guidewire/Billing
  participant App as Gateway API
  participant Consent as ConsentService
  participant Tw as Twilio
  participant Bus as Outbox/RabbitMQ
  Core->>App: POST /v1/messages + Idempotency-Key
  App->>App: validate + normalize E.164 (Lookup cache)
  App->>Consent: check(phone, topic, quietHours, caps, DNC)
  alt denied
    App-->>Core: 403 CONSENT_DENIED_* + audit event
  else allowed
    App->>App: render template + sign pay/esign link
    App->>Tw: POST Messages (MessagingServiceSid)
    Tw-->>App: SID + queued
    App->>Bus: message.created + delivery.pending
    Tw-->>App: DLR webhook /twilio/dlr (validate signature)
    App->>Bus: delivery.updated -> CRM + warehouse sink
  end
```

### Inbound keyword sequence
```mermaid
sequenceDiagram
  participant Tw as Twilio
  participant App as Ingress/TwilioController
  participant KW as KeywordRouter
  participant CC as ClaimCenter adapter
  Tw->>App: POST /twilio/inbound (signature check, <500ms ack)
  App->>KW: parse BAL/PAY/STATUS/CLAIM/STOP/HELP
  alt STOP
    KW->>KW: opt-out + audit + reply STOP-confirm
  else STATUS 12345
    KW->>CC: claim lookup (cached 5min)
    CC-->>KW: status + adjuster ETA
    KW->>Tw: reply SMS
  else CLAIM
    KW->>Tw: start FNOL flow (policy verify -> loss info -> create)
  else unknown
    KW->>KW: enqueue to agent inbox + auto-reply
  end
```

### Cloud-portability adapter boundary
```mermaid
flowchart LR
  CORE[Core business code<br/>no cloud imports] --> PORTS[Ports: QueuePort, StorePort, IdpPort]
  PORTS --> L_RMQ[RabbitMQ binder<br/>profile: local]
  PORTS --> L_MINIO[MinIO S3<br/>profile: local]
  PORTS --> L_KC[Keycloak<br/>profile: local]
  PORTS -.-> C_AWS[SQS + S3 + Okta<br/>profile: aws]
  PORTS -.-> C_GCP[PubSub + GCS<br/>profile: gcp]
  PORTS -.-> C_AZ[ServiceBus + Blob + Entra<br/>profile: azure]
```
Only `application-*.yml` + binder starter changes per cloud; `CORE` untouched.
