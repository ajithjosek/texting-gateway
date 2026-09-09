#!/usr/bin/env bash
set -euo pipefail
# ETG install: Linux VM, connected on-prem (Option A). Run after `docker compose up -d --build`.
echo "==> waiting for app health..."
for i in $(seq 1 30); do
  if curl -fs http://localhost:8080/actuator/health >/dev/null 2>&1; then echo "app is up"; break; fi
  sleep 5
  if [ "$i" = "30" ]; then echo "app did not start; run: docker compose logs app"; exit 1; fi
done
echo "==> flyway migrations run automatically on boot (spring.flyway.enabled=true)"
echo "==> next: configure Twilio webhook URLs to https://<your-domain>/twilio/inbound and /twilio/dlr"
echo "==> create Keycloak realm + OIDC client, then file 10DLC Brand/Campaigns (see docs/02-implementation-plan.md S0)"
echo "done."
