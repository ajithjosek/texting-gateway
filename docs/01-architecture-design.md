# Enterprise Texting Gateway — Architecture & Design
**Org:** Insurance Co. | **Channels:** SMS/MMS + WhatsApp + RCS | **Provider:** Twilio
**Version:** 1.0 | **Date:** 2026-09-09 | **Status:** Approved for planning

## 1. Executive Summary
Central, compliant, API-first messaging platform for all LOBs (P&C, Life, Commercial). Replaces point-to-point SMS in Guidewire/Salesforce with a governed gateway that owns consent, senders, templates, journeys, inbox, audit, and warehouse feed. Twilio is the delivery plane; we own orchestration, compliance, and insurance workflows.

Goals:
- One consent + audit source of truth for TCPA/10DLC.
- 4 hero workflows in MVP: policy servicing/renewals, billing/pay, claims/FNOL, agent 1:1, plus marketing campaigns.
- Deep integrations: Salesforce (CRM), Guidewire PolicyCenter/ClaimCenter/BillingCenter, Snowflake/BigQuery.
- 99.9% delivery pipeline availability, <5s event-to-send p95, 100 MPS CAT burst.

Non-goals (v1): voice IVR rebuild, email/push, short-code vanity (Phase 3).

## 2. Personas & Use Cases
| Persona | Needs |
|---|---|
| Policyholder | renewal/pay reminders, claim status, FNOL via text, e-sign/pay links, STOP/HELP |
| Agent/CSR | shared inbox, templates, assignment, notes, CSAT |
| Marketing Ops | bulk/drip, approvals, A/B, quiet-hour-safe scheduling |
| Compliance/Legal | consent proof, archive/eDiscovery, approval gates |
| LOB Admin | tenant senders, limits, templates per agency |
| Engineers | REST + webhooks, sandbox, OpenAPI |

## 3. High-Level Architecture

```
[Policyholder handset]
  SMS/MMS <-> WhatsApp <-> RCS
        |
[Twilio: Programmable Messaging + Messaging Services + Conversations + Lookup + Content API + Verify]
        | webhooks (status + inbound) / REST send
        v
[API Gateway (AuthN/Z, rate-limit, idempotency)] 
  -> Ingress Worker (signature verify, E.164 normalize, Dedupe)
  -> Consent Service (OPT-IN? topic? quiet-hour? cap? DNC?) -- BLOCK if fail
  -> Router: {Journey Engine | Inbox Service | Campaign Sender}
  -> Sender Worker -> Twilio REST + DLR handler
  -> Event Bus -> [Warehouse Sink] [CRM Sync] [Core Sync] [Audit Log]
[Integration Hub] <-> Salesforce <-> Guidewire (Policy/Claim/Billing) <-> Stripe/InvoiceCloud <-> DocuSign
[Control Plane] TemplateSvc + ApprovalSvc + SegmentSvc + Admin UI + Agent Inbox UI + Analytics
```

Deployment: cloud-native microservices (containers on EKS/AKS), regional US-only. Async-first.

## 4. Twilio Mapping
| Need | Twilio Product | Notes |
|---|---|---|
| SMS/MMS send/receive | Programmable Messaging + Messaging Services | pools, sticky sender, geomatch, fallback, Advanced Opt-Out |
| 1:1 conversations | Conversations API | agent collision, multi-participant |
| Identity validation | Lookup v2 (line type, carrier, port) | pre-save |
| Templates (WA/RCS) | Content API + Content Templates | approval sync |
| Journeys (v1 simple) | Studio Flows (or custom engine) | migrate to custom by Phase 2 for Guidewire branching |
| Number mgmt | Phone Numbers + TrustHub (10DLC Brand/Campaign, Toll-Free verification) | TCR lead time 2-4 wks |
| Debugger/alerts | Monitor + Debugger webhooks | -> PagerDuty |

Sender strategy: 10DLC (servicing + marketing per LOB), Toll-Free (claims 2-way + overflow), Long-code pool for agent 1:1, Short-code Phase 3 for CAT/marketing burst.

## 5. Component Design
### 5.1 API Gateway + Ingress
- REST: `POST /v1/messages`, `POST /v1/campaigns`, `GET /v1/consent/{phone}`, `POST /v1/fnol`. OpenAPI 3.1.
- Auth: OAuth2 client-credentials + per-LOB API keys, mTLS for Guidewire. Idempotency-Key required on sends.
- Twilio webhooks: validate `X-Twilio-Signature`, respond <500ms, enqueue, ack 200 with empty TwiML.

### 5.2 Consent & Compliance Service (source of truth)
State machine per (phone, topic): `unknown -> opted_in -> opted_out`. Topics: `servicing, billing, claims, marketing`.
Checks on every send (order): E.164 valid? -> opted_in for topic? -> STOP in last 5s race? -> quiet hours (marketing only, 8am-9pm local + FL/OK overrides)? -> freq cap? -> DNC/litigator scrub (marketing)? Deny with coded reason `CONSENT_DENIED_*`.
Storage: Postgres `consents(phone_e164, topic, status, source, proof_text_version, actor, ip, ts)` + immutable `consent_events` append-only.

### 5.3 Contact & Segment Service
`contacts(id, crm_id, policy_ids[], phones[], primary_phone, locale, tz, lOB, agent_id)`. Dedup: exact phone match + fuzzy (name+DOB+zip) score >0.9 auto-merge, else review queue. Segments materialized nightly + realtime flag updates via CDC.

### 5.4 Template Service
Liquid vars `{{first_name, policy_number, amount_due, pay_link, claim_number}}`. Versioned, locale EN/ES. Marketing requires Legal approval state machine `draft -> pending_legal -> approved`. WhatsApp/RCS approval status synced hourly.

### 5.5 Journey / Campaign Engine
- Campaigns: segment snapshot + throttle (per-campaign MPS) + schedule in recipient tz.
- Journeys: DAG (send -> wait_reply 24h -> branch on intent/claim status). Persist execution state in Redis + Postgres for resume/rollback.
- Keyword engine: JOIN, STOP, HELP, BAL, PAY, STATUS {claim#}, CLAIM.

### 5.6 Agent Inbox Service (on Conversations)
Conversation per (policyholder, topic/claim). Assignment, SLA timer, private notes (never sent), canned replies, after-hours routing. Transcript archived + summarized to Salesforce Task.

### 5.7 Integration Hub (anti-corruption layer)
- Salesforce: Bulk API 2.0 + Platform Events. Contact/Activity/consent bi-dir <2min. DLQ on fail.
- Guidewire: Cloud API (Policy/Claim/Billing) REST + webhooks; polling fallback 15min; respect 100 req/s; idempotent FNOL create via `fnol_dedup_key`.
- Payments: Stripe/InvoiceCloud short links with expiry + signed HMAC; payment webhook -> receipt SMS.
- E-sign: DocuSign envelope link + `signed/declined` webhook -> SMS nudge.
- Warehouse: outbox -> Kinesis/EventHub -> Snowflake/BigQuery `<5min lag`: `messages, deliveries, clicks, consent_events, journeys`.

### 5.8 Analytics & Admin
Dashboards (Metabase/PowerBI): delivery/30007 breakdown, cost/segment, conversion, opt-out/CSAT, agent leaderboard. Admin UI: tenants, senders, limits, spend caps, feature flags, sandbox toggle.

## 6. Data Model (core tables)
- `contacts, phones, policies_map, consents, consent_events, messages (id, tenant, to, from, channel, template_v, body_hash, status, twilio_sid, idempotency_key UNIQUE), deliveries (sid, status, error_code, ts), clicks, campaigns, journey_runs, conversations, audit_log (immutable, hash-chained)`.
- PII: encrypt columns (KMS), body stored encrypted; logs store `body_hash` only + redacted preview. Retention: 7y archive (S3 Glacier + WORM), 90d hot.

## 7. Key Flows
**Outbound (renewal):** BillingCenter event -> Hub -> Gateway (idempotent) -> Consent pass -> Template render -> Sender -> Twilio -> DLR -> Warehouse/CRM.
**Inbound (STATUS 12345):** Twilio inbound -> Ingress -> Keyword router -> ClaimCenter lookup -> reply in <10s; if unknown, create task for agent.
**FNOL:** CLAIM -> Studio/custom flow (verify policy+DOB last4 -> loss date/type) -> ClaimCenter create -> return claim# + adjuster ETA.
**CAT burst:** LOB admin selects geo segment -> dry-run count -> compliance override flag (emergency only) -> throttled burst 100 MPS -> live DLR wall.

## 8. Security & Compliance
- SSO (SAML/OIDC Okta/Entra) + MFA, SCIM, RBAC (Super/LOB Admin/Agent/Compliance/API-only), per-tenant isolation (sender pool + template namespace + rate/spend caps).
- Encryption TLS1.2+ / AES-256 + KMS, US residency, no PII to LLM without redaction, IP allowlist + key rotation 90d.
- TCPA: documented opt-in proof, HELP/STOP handling, quiet hours, DNC scrub, 7y archive. 10DLC TCR + Toll-Free verification pre-launch.
- SOC2: hash-chained audit log, change tickets for template/campaign approval, annual pen-test.

## 9. NFRs / SLOs
- Availability 99.9% send pipeline; RPO 5min / RTO 30min (multi-AZ, DLQ replay).
- Latency: webhook ack <500ms, keyword reply p95 <10s, event-to-send p95 <5s.
- Scale: 10k MPS steady, 100 MPS burst per campaign; idempotent under Twilio retries.
- Cost guard: per-msg cost tagged by tenant/campaign; spend cap circuit-breaker.

## 10. Tech Stack (recommended)
- Runtime: Node 20 / .NET 8 microservices (pick one; .NET fits Guidewire shops), Postgres 15 (RDS), Redis (ElastiCache), SQS/ServiceBus + Kinesis/EventHub, S3 + Glacier.
- Hosting: EKS/AKS, API Gateway (APIGW/APIM), Secrets Manager/KeyVault, OpenTelemetry -> Datadog, PagerDuty.
- IaC: Terraform, GitHub Actions, ephemeral preview envs + `sandbox` Twilio subaccount.

## 11. ADRs / Open Decisions
- ADR-01: Custom journey engine vs Twilio Studio long-term -> custom by Phase 2 (Guidewire branching).
- ADR-02: Single Twilio subaccount per LOB vs per agency -> per LOB + isolated Messaging Services.
- TODO: confirm Guidewire Cloud vs self-hosted API version; confirm payment provider; confirm warehouse (Snowflake vs BQ).

## 12. Risks
10DLC vetting delay, legacy consent migration without proof, Guidewire throttle, carrier filtering on marketing copy (avoid SHAFT), WhatsApp opt-in quality rating.
