# Enterprise Texting Gateway — Implementation Plan
**Version:** 1.0 | **Date:** 2026-09-09 | **Method:** Agile, 2-week sprints | **Team:** 2 BE + 1 FE + 1 QA + 0.5 DevOps + 0.5 Compliance + PM/TPM

## 1. Milestones & Timeline (18-20 weeks)
| Phase | Sprints | Exit Criteria |
|---|---|---|
| **Phase 0 Foundation (2-3 wks)** S0-S1 | Twilio org + TrustHub filed, gateway skeleton, consent model live | 10DLC Brand submitted, inbound/outbound SMS in sandbox, consent API blocks unconsented send |
| **Phase 1 MVP (6-8 wks)** S2-S5 | Servicing + billing + claim status + agent inbox + Salesforce sync, 1 LOB pilot | Pilot agency sends renewal/pay/STATUS live on 10DLC, audit passes, SSO+RABC live |
| **Phase 2 Scale (6 wks)** S6-S8 | WhatsApp/RCS, bulk + approvals, Guidewire deep + warehouse + dashboards | CAT dry-run 100 MPS, Guidewire FNOL create live, warehouse lag <5min |
| **Phase 3 Harden + AI (4 wks)** S9-S10 | Short-code (optional), AI drafts/summaries, multi-language, GA | CSAT + conversion lift measured, SOC2 evidence pack, GA cutover |

Critical path: TrustHub vetting (start Day 1) -> consent migration -> Guidewire API access -> pilot.

## 2. Sprint Breakdown
### S0 (wk 1-2) — Platform skeleton + compliance start
- Provision Twilio: master account + `prod/sandbox` subaccounts, Messaging Services per LOB, toll-free + long-codes, Debugger webhooks.
- File 10DLC Brand + 3 campaigns (servicing/billing/marketing), Toll-Free verification. Owner: Compliance.
- Scaffold: API Gateway + Ingress (signature verify), Postgres + Redis + Outbox, CI/CD + Terraform + observability.
- Data: `contacts, consents, messages, deliveries` schema + Lookup validation.
- DoD: `POST /v1/messages` sandbox send + DLR stored; unconsented send returns `403 CONSENT_DENIED`.

### S1 (wk 3) — Consent + inbound keywords
- Opt-in/out engine (STOP/HELP/BAL keywords), quiet hours + freq cap, consent import CLI with proof CSV validation.
- Inbound pipeline + keyword router + audit log (hash-chained).
- DoD: STOP opts-out <5s; HELP replies; legacy import rejects rows without proof.

### S2-S3 (wk 4-7) — Servicing/Billing + Agent Inbox
- Templates (renewal T-30/T-7, due/failed, receipt) EN + Liquid; pay/esign link signing (HMAC, expiry).
- BillingCenter/PolicyCenter webhook consumer (idempotent) + polling fallback.
- Inbox on Conversations: assignment, collision, notes, SLA timer, after-hours route, CSAT.
- Salesforce sync: Contact/Activity bi-dir.
- DoD: pilot agency 1:1 + renewal flow live; transcript in Salesforce; UAT sign-off.

### S4-S5 (wk 8-11) — Claims + pilot hardening
- STATUS lookup + adjuster slot pick + photo intake -> ClaimCenter.
- FNOL flow (CLAIM keyword) behind feature flag.
- SSO/SCIM + RBAC + tenant isolation + spend caps + sandbox/prod separation.
- Pilot: 1 agency, 5k numbers, success metrics (delivery >98%, reply <10s p95, opt-out <1%).
- DoD: pen-test clean-ish, 7y archive verified, runbook + on-call live.

### S6-S7 (wk 12-15) — Campaigns + WhatsApp/RCS + Guidewire deep
- Campaign manager: snapshot, throttle, tz-schedule, A/B, legal approval gate.
- Content API sync for WA/RCS templates + fallback chain.
- Guidewire Cloud API full CRUD (FNOL create, status, appointment) + DLQ.
- Warehouse sink (Kinesis -> Snowflake/BQ) + PowerBI/Metabase dashboards.
- DoD: CAT dry-run passes; warehouse lag <5min; marketing UAT with legal approval.

### S8 (wk 16-17) — Scale + chaos
- Load test (k6): 10k steady, 100 MPS burst; Twilio 429 handling; DLQ replay; multi-AZ failover drill.
- Cost dashboard + per-LOB caps; number warm-up plan.
- DoD: RTO 30min demonstrated; spend breaker tested.

### S9-S10 (wk 18-20) — AI + GA
- Intent classify + draft reply (agent approves), summarization to claim notes, PII redaction pre-LLM.
- ES templates, short-code order (if needed), SOC2 evidence pack, GA cutover + hypercare 2 wks.

## 3. Team & RACI (abridged)
- BE: gateway, consent, hub, journeys. FE: inbox + admin + preference center. QA: contract + E2E + load. DevOps: IaC, secrets, alerts. Compliance: TrustHub, copy vetting, archive.
- RACI: campaign launch — Marketing (R), Legal (A), Eng (C), Compliance (I). FNOL mapping — Claims SME (A), Eng (R).

## 4. Backlog Linkage (Epics -> Phases)
Phase 0: EPIC-01 (skeleton), EPIC-02 (consent core), EPIC-03 (contacts). Phase 1: EPIC-04/05/06 + 08.1/08.2-lite + 09. Phase 2: EPIC-07/08 full + 10. Phase 3: EPIC-11.

## 5. Dependencies & Lead Times
- Day 1: request Twilio TrustHub + TCR vetting (2-4 wks), Guidewire API creds + rate docs, Salesforce sandbox, payment/esign creds, brand domain for short links.
- Blockers: no marketing send before 10DLC approved; no FNOL write before ClaimCenter idempotency agreed.

## 6. Testing Strategy
- Contract: Twilio webhook signature + DLR matrix (30007 etc). E2E: seeded Twilio test numbers + Magic numbers; keyword matrix; quiet-hour tz tests.
- UAT per workflow with claims/billing SMEs; CAT game-day; chaos (kill AZ, replay DLQ).
- Entry/exit: 95% AC automated, 0 P0 open for pilot/GA.

## 7. Launch Checklists
**Pilot:** 10DLC approved, consent migrated w/ proof, SSO enforced, runbook + PagerDuty, spend cap, archive verified.
**GA:** load test pass, RTO drill, warehouse + dashboards, legal approval flow, hypercare roster, rollback (feature flags + Terraform revert).

## 8. Cost Model (plan for)
Twilio: $0.0079/SMS segment US, MMS ~$0.02, 10DLC $15/mo/campaign + $2/brand, Toll-Free $2/mo, WhatsApp convo pricing, Lookup $0.005, Conversations $0.05/active participant/hr. Infra: RDS + Redis + EKS + Kinesis (~$2-4k/mo pilot). Buffer 20% for retries/CAT.

## 9. Risks + Mitigations
| Risk | Mitigation |
|---|---|
| TCR rejection/delay | File Day 1, servicing-first copy, toll-free fallback |
| Legacy consent w/o proof | Re-opt-in campaign, servicing-only until proof |
| Guidewire throttle | Cache status 5min, bulk jobs off-peak, backoff + DLQ |
| Carrier filtering | Pre-vet copy, avoid SHAFT, registered senders only |
| PII leak to logs/LLM | Redaction middleware + log tests in CI |

## 10. Definition of Done (every story)
Code + tests + OpenAPI updated + dashboards/alerts + audit event + runbook note + compliance tag (needs-legal or not) + demo to SME.

## 11. Next Actions (this week)
1. Create Twilio master + subaccounts, file TrustHub. 2. Get Guidewire/Salesforce sandbox creds. 3. Freeze template copy v1 + quiet-hour matrix. 4. Approve stack (.NET vs Node, Snowflake vs BQ). 5. Schedule pilot agency + UAT slots.
