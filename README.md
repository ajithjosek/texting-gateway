# Enterprise Texting Gateway (ETG)

Product: compliant texting gateway for insurers. Twilio delivery plane; Java owns consent, senders, journeys, inbox, audit.
Deploys: **Linux VM + `docker compose up -d`** (1 install/customer, connected on-prem). Portable to AWS/GCP/Azure via same images + Spring profiles.

Stack: Java 21 + Spring Boot 3.3, Postgres 16, Redis 7, RabbitMQ, MinIO (S3-compat), Keycloak (OIDC), Next.js static frontend.

Docs:
- `docs/00-design-decisions.md` — ADRs + diagrams (start here)
- `docs/01-architecture-design.md` — full architecture
- `docs/02-implementation-plan.md` — phased build plan

## Quickstart (local dev, no Docker needed for compile)
```bash
mvn -q -DskipTests package
```

## Quickstart (full stack on Linux VM)
```bash
cp .env.example .env   # set TWILIO_* , POSTGRES_PASSWORD, KEYCLOAK_ADMIN_*
docker compose up -d --build
./scripts/install.sh   # waits for db, runs flyway via app, seeds demo tenant
curl http://localhost:8080/actuator/health
```

## Profiles / portability
- `local` (default): RabbitMQ + MinIO + Keycloak, `application-local.yml`
- `aws|gcp|azure`: same image, swap binder/store/idp via env — see `src/main/resources/application.yml`

## Pushing to a remote later
```bash
git remote add origin <your-empty-repo-url>
git push -u origin main
```

## Layout
```
src/main/java/com/etg/         # modular monolith: consent, messaging, inbox, journey, hub, common
src/main/resources/db/migration # Flyway V1 init
docker-compose.yml             # vm topology
Dockerfile                     # app image (eclipse-temurin:21-jre)
frontend/                      # Next.js static placeholder + nginx
helm/etg/                      # Phase-2 Helm stub
scripts/                       # install.sh, backup.sh
```
