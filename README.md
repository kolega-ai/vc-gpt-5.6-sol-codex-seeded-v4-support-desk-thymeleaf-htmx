# Northstar Customer Support Desk

Production-style customer support desk for `support-desk-001`, built with Java 21, Spring Boot 3, Thymeleaf, htmx, Spring Security, JPA, Flyway, and PostgreSQL.

## Run locally

Requirements: Docker with Compose. No SaaS accounts or external infrastructure are used.

```bash
docker compose up --build
```

Open <http://localhost:8080>. The first build downloads Gradle/Maven dependencies. Flyway creates three isolated schemas and loads realistic demo data; uploaded files are stored in the `support-files` Docker volume.

To build and test outside Docker, install Java 21 or allow the committed Gradle wrapper to provision it:

```bash
./gradlew clean test bootJar
```

If the host has no default JVM but Homebrew Gradle is available, `gradle test bootJar` also provisions the declared Java 21 toolchain.

## Demo accounts

All demo accounts use password `Support123!`.

| Role | Email |
|---|---|
| Support admin | `admin@desk.test` |
| Team lead | `lead@desk.test` |
| Support agent | `agent@desk.test` |
| Customer user | `customer@desk.test` |

The seed contains 10 organisations, 30 customer users, 12 support users, 70 tickets, 25 locally stored attachments, 20 escalations, 20 knowledge articles, replies, internal notes, notifications, SLA policies, and audit history.

## Architecture and trust boundary

The Gradle parent contains exactly three deployable tiers:

- `channel` authenticates users with a secure session cookie, handles CSRF, and renders all full-page and htmx fragment responses.
- `decision` authorizes every request independently. The channel forwards a short-lived opaque signed session token containing only the authenticated username. Decision verifies it, then loads role, enabled state, organisation, and team scope from its own database on every request. It ignores client-supplied role, scope, organisation, user, and limit values.
- `core-mock` models an external webhook receiver. Decision mints short-lived signed scope tokens; core-mock verifies the signature and enforces the token scope against the subscription in the payload. Raw scope parameters are never trusted.

In short: **channel authenticates; decision authorizes**. Fragment endpoints call the same independently authorized decision routes as full pages.

## Main workflows and screens

- Role-aware dashboards with recent work, SLA posture, escalations, and notifications.
- Organisation-scoped customer ticket submission/history, public replies, and file handling.
- Team-scoped agent queue with htmx search and status/priority filters.
- Ticket detail with public conversation, staff-only notes, attachments, SLA timeline, escalation, transitions, and audit history.
- Lead escalation queue with explicit `requested → accepted/rejected → in_review → resolved/rejected` transitions.
- Knowledge authoring with explicit `draft → in_review → published → archived` workflow and public-only customer search.
- Admin user access, teams/queues, categories, SLA policies, local webhooks, customer/contact CSV import, sensitive ticket/SLA CSV export, and append-only audit viewer.
- Login/logout, session fixation protection, profile update, and single-use 30-minute password reset tokens. Development email is written to `channel.auth_outbox`.
- In-process scheduled webhook delivery to core-mock; attempts are persisted locally.

## Security controls

- `@PreAuthorize` on privileged decision endpoints plus object-level organisation/team checks in the shared ticket service.
- Internal notes are filtered before customer DTOs are created and never enter customer-visible exports or webhook payloads.
- Explicit allow-list DTOs; assignment, requester, organisation, team, SLA deadlines, and roles are derived server-side.
- CSRF enabled for forms and htmx, contextual Thymeleaf escaping, no unescaped user HTML.
- Bound JPA/JDBC parameters; the sole dynamic report sort column is bounded by an allow-list.
- 10 MB upload limit, content-type allow-list, generated storage names, SHA-256 metadata, and canonical-base download resolution.
- BCrypt password hashes; HMAC-SHA256 session tokens; single-use hashed recovery tokens; secure cookie controls configurable with `COOKIE_SECURE=true`.
- Append-only audit data has no update/delete endpoint. State changes, privileged actions, reads, downloads, internal notes, role changes, imports, and exports are recorded with authorization basis.
- Secrets come from environment variables; checked-in values are local-development defaults only.

The five required benign scanner-precision cases are preserved and commented: centralized ownership check, `java.util.Random` static cache-buster, MD5 reference ETag, allow-listed parameterized report query, and canonicalized file download.

## Operations

- Health: `decision:8081/actuator/health`, `core-mock:8082/health` (internal Compose network).
- Jenkins: root `Jenkinsfile` runs tests, builds executable jars, publishes JUnit results, and archives artifacts.
- Reset local data: `docker compose down -v` (destructive; removes local demo DB and uploads).

## UI summary

The responsive “operations notebook” visual system uses deep service navy, accessible blue actions, coral escalation emphasis, compact data typography, consistent panels/tables/forms/badges, visible focus rings, and reduced-motion support. The signature SLA rail keeps the service promise visually anchored on every ticket.

## Completion note

All specified primary journeys and required capability classes are implemented. External integrations are intentionally local mocks, as required.
