# DiCar Remote — Python Backend Specification

**Status:** Proposed implementation specification (v1.0)  
**Repository:** https://github.com/IlyaKochetov/dicar2  
**Target implementation:** `server/` in this repository  
**API protocol:** `v1`  
**Primary language:** Python 3.12+  
**Audience:** Backend, DiLink head-unit, and Android mobile developers

## 1. Goal

Create an open-source relay server that lets a DiCar-enabled BYD vehicle communicate with a companion mobile app over the Internet. The same server implementation must support:

- **Hosted mode:** a publicly accessible multi-user service; owners install both apps, create an account, and pair their car by QR code.
- **Self-hosted mode:** an owner deploys a single Docker Compose stack and points both apps to their own HTTPS endpoint. There is **no dependency on a central DiCar account or cloud**.
- **Interoperability:** any conforming DiCar phone and car client can use any conforming v1 backend. The backend URL is configurable, not embedded in the APK.

The server is a **transport, identity, pairing, and authorization layer**. It never reads BYDAuto, runs ADB, or talks to the car's CAN bus itself. These operations remain inside the existing head-unit app.

### Non-goals for v1

- Waking a powered-down/suspended head unit, changing modem/T-Box firmware, or bypassing BYD region restrictions.
- Lock/unlock, vehicle movement, ignition/start, flashing firmware, raw arbitrary BYDAuto calls, or remote shell/ADB.
- A complete trip-history or vehicle-location tracking platform.
- Native push notifications requiring Google Play Services.
- End-to-end encryption of telemetry against the backend operator. TLS protects transport; the selected backend processes decrypted data.

## 2. Existing application integration

The current repository already exposes:

| Existing component | Purpose | Required addition |
|---|---|---|
| `app/src/main/java/com/dicar/vehicle/data/VehicleRepository.kt` | `state: StateFlow<VehicleState>`, `send(VehicleCommand)`, polling lifecycle | Safe, correlated remote-command adapter |
| `data/model/VehicleState.kt` | Typed vehicle observations | Allowlisted network DTO serializer |
| `data/model/VehicleCommand.kt` | Typed climate and other commands | Explicit mapping from permitted v1 remote actions |
| `service/VehicleMonitorService.kt` | Foreground telemetry/polling service | Optional remote-connection lifecycle |
| `data/SettingsStore.kt` | Local app preferences | Server URL, remote enabled, pairing state |

Add a car-side `RemoteConnector`, not a second vehicle data source. It shall subscribe to the existing repository, maintain **one outbound WSS** connection, and call only approved existing commands. The existing local car interface must continue working with remote access disabled.

**Critical:** `VehicleRepository.send()` currently returns `Unit` and uses asynchronous debounce/readback. A network command must not be reported as "confirmed" merely because `send()` was invoked. The car-side adapter must correlate each server command ID with dispatch and repository readback/event completion, or explicitly report `sent_unconfirmed`.

## 3. Topology

```text
DiCar Mobile (Android)
    | HTTPS REST + optional WSS
    v
Caddy (public TLS :443)
    |
    v
FastAPI (Python)
    |-- Auth + access control
    |-- One-time QR pairing
    |-- WebSocket session registry
    |-- State snapshot cache
    |-- Command relay + audit
    |
    +-- SQLite (single-instance deployment)
    +-- PostgreSQL (hosted/multi-user deployment)
    +-- Redis (optional; required before horizontal scaling)
    ^
    | outbound WSS only
DiCar on BYD head unit
    |-- VehicleRepository.state
    +-- VehicleRepository / allowed VehicleCommand
```

The vehicle must not listen on any public TCP port. Do not expose its ADB port 5555, DiPlus port 8988, or an unauthenticated HTTP endpoint to the Internet.

The car will be considered reachable **only while its Android head unit is awake, connected and executing the connector**. A backend cannot wake an offline head unit by itself.

## 4. Technology and packaging

| Layer | Requirement |
|---|---|
| HTTP/WebSocket | FastAPI, Starlette, Uvicorn, asyncio |
| Schemas | Pydantic v2; strict typed request/response validation |
| DB | SQLAlchemy 2.x async and Alembic |
| Small installation | SQLite + aiosqlite; one API process |
| Public hosting | PostgreSQL + asyncpg; one API process initially |
| Cross-worker pub/sub | Optional Redis; necessary before multiple API workers/replicas |
| Credentials | Argon2id for passwords; cryptographically random 256-bit device/refresh credentials; DB stores hashes only |
| Client authentication | Short-lived access tokens; rotating refresh tokens |
| HTTPS/proxy | Caddy, reverse proxy to internal Uvicorn |
| Tests/quality | pytest, pytest-asyncio, HTTPX, Ruff, mypy/pyright |
| Container | Slim Python image, non-root user, pinned dependencies and reproducible lockfile |
| Health/metrics | `/healthz`, `/readyz` and internal Prometheus `/metrics` |

Avoid Firebase, Supabase, Google services and other mandatory SaaS dependencies. API paths and WebSocket schema must remain independent of the database choice.

### Process constraints

- SQLite mode supports one API instance/worker. File-backed SQLite data and backups must live on a persistent Docker volume.
- PostgreSQL can serve the hosted mode; initially use one Uvicorn worker to keep WebSocket routing simple.
- Multiple workers/replicas require a distributed connection ownership registry and Redis pub/sub (or equivalent), with explicitly tested routing. Simply increasing `--workers` is **not** supported in v1.
- Alembic migrations must run once per deployment, not concurrently from multiple API workers.

## 5. Identity and permissions

### Roles

- **Owner:** authenticated phone account authorized to view and command owned vehicles.
- **Vehicle:** independently authenticated head-unit device, scoped to exactly one vehicle.
- **Server admin:** operates one server instance; cannot impersonate a vehicle by using an owner token.

Self-hosted installation must support a **CLI bootstrap command** to create the first owner account, with public registration disabled by default. No email service is necessary for local/private setups.

Hosted mode may allow verified email registration (`REGISTRATION_MODE=open_verified`), with CAPTCHA or equivalent abuse protection and an optional SMTP provider. Password-reset and invitation workflows must be disabled or implemented securely, never simulated with fixed reset codes.

### Credential rules

- Owners log in using email/password. Hash passwords with Argon2id; never store plaintext passwords.
- Phone access tokens expire in approximately 15 minutes and are renewed with rotating, revocable refresh tokens.
- The car receives a distinct random, long-lived **device credential only after completed physical pairing**. Store it in Android encrypted storage/Keystore, and store only its hash on the server.
- Access tokens, refresh tokens and device credentials must never appear in URLs, QR codes, logs or analytics.
- Support per-vehicle revocation, remote disconnect, session revocation and device credential rotation.
- All vehicle operations check server-side ownership on **every** request and event subscription. A guessed vehicle UUID must grant no access.
- TLS certificate validation is mandatory for publicly reachable installations. Self-signed certificates may be supported only when the client explicitly trusts a supplied CA; never offer a "disable TLS validation" toggle.

## 6. QR-based pairing state machine

User flow: install both apps → select server URL on car → create/sign into an account on phone → scan car's QR → confirm phone identity on the head unit → vehicle appears in app.

### Handshake

1. Car calls `POST /v1/pairings` (unauthenticated but rate-limited). The server creates a random `pairing_id`, a high-entropy one-time `qr_secret`, and a separate secret `car_secret` only returned to the car. The session expires after **120 seconds**.
2. The car displays a QR containing a custom `dicar://pair` URI with **the server HTTPS URL, pairing ID, and one-time QR secret only**. Never include a permanent car credential.
3. An authenticated phone calls `POST /v1/pairings/{id}/claim` with the QR secret. Server atomically marks the session claimed by that user.
4. Car polls `GET /v1/pairings/{id}` with `car_secret` (or uses a waiting connection). It displays the claiming account's masked identity and requires a **physical tap to approve**.
5. Car calls `POST /v1/pairings/{id}/confirm` with `car_secret`. In one database transaction the server binds a new vehicle to the claiming owner, marks the pair as consumed, and issues a unique car device credential.
6. Car securely stores the credential, discards pairing secrets and opens `WSS /v1/ws/car`. Phone discovers the paired vehicle via `GET /v1/vehicles`.

### Safety and abuse constraints

- Short expiry; one claim and one confirmation only; atomic state transitions with database uniqueness/transactions.
- Cryptographically strong random secrets (at least 128-bit QR secret, 256-bit car secret and device credential).
- Limit active unpaired sessions, claims, wrong codes and pairing attempts per network source; add server-global rate limits.
- No second owner claim of an already paired vehicle without verified car-side unpair/re-pair authorization.
- Pairing status queries require `car_secret`; phone cannot approve itself.
- Display a human-readable server identity (hostname) in both apps and require an explicit confirmation when changing servers.
- Cancelled/expired pairing cannot be reused.

## 7. Public REST API

All endpoints are prefixed `/v1` except health, metrics and discovery. Generate machine-readable OpenAPI from the FastAPI models and include documented JSON examples.

| Method | Route | Authentication | Function |
|---|---|---|---|
| GET | `/.well-known/dicar` | Public | Server display name, API versions, capabilities and registration mode; no secrets |
| POST | `/v1/auth/register` | Public when enabled | Create phone account; verification requirements apply |
| POST | `/v1/auth/login` | Public, rate-limited | Return access and rotating refresh tokens |
| POST | `/v1/auth/refresh` | Refresh credential | Rotate and return tokens |
| POST | `/v1/auth/logout` | User session | Revoke current refresh session |
| GET | `/v1/me` | User | Account and server policy summary |
| POST | `/v1/pairings` | Unpaired car session | Begin one-time pairing |
| GET | `/v1/pairings/{id}` | Car pairing secret | Read claim/pairing state |
| POST | `/v1/pairings/{id}/claim` | User + QR secret | Claim pairing |
| POST | `/v1/pairings/{id}/confirm` | Car pairing secret | Physical confirmation, issue car credential |
| POST | `/v1/pairings/{id}/cancel` | Car pairing secret | Cancel pairing |
| GET | `/v1/vehicles` | User | Owner's paired vehicles |
| GET | `/v1/vehicles/{id}` | Owner | Connection and capabilities summary |
| GET | `/v1/vehicles/{id}/state` | Owner | Last allowlisted state, timestamps and freshness |
| POST | `/v1/vehicles/{id}/commands` | Owner | Send approved command to **online** car |
| GET | `/v1/vehicles/{id}/commands/{command_id}` | Owner | Check delivery and result |
| DELETE | `/v1/vehicles/{id}` | Owner | Revoke pairing/device access; retain or purge audit according to policy |
| GET | `/healthz` | Public | Liveness only |
| GET | `/readyz` | Operator/network restricted | DB and service readiness |

No route may let a user request arbitrary Kotlin classes, FIDs, shell commands, SDK reflection methods or BYDAuto method names.

Use standard HTTP semantics, including `401` unauthenticated, `403` unauthorized, `404` not found (without leaking other users' vehicle existence), `409` offline/not allowed, `422` invalid request, `429` rate limited and `503` service unavailable. Return a consistent machine-readable error envelope.

## 8. WebSocket transport protocol

### Sessions

- Car: `wss://host/v1/ws/car`, authenticated via an `Authorization: Bearer <device credential>` handshake header. One active authenticated connection per vehicle; replacement disconnects the older connection.
- Phone: `wss://host/v1/ws/mobile` with a short-lived owner access token in an Authorization header, after ownership validation. **REST polling remains a supported fallback.**
- No authentication tokens in WebSocket URLs or query parameters.
- Client implementations must reconnect with bounded exponential backoff and jitter.
- Heartbeat interval: **20 seconds**. Treat connection as offline after missed heartbeats / a **60-second** timeout.
- Maximum input message size: **32 KiB** initially. Validate schema, sequence and authorization on every message.
- WebSocket JSON envelope always includes `version`, `type`, `message_id` and UTC RFC 3339 `timestamp`. Unknown protocol versions should fail cleanly.

### Car → server: status update

```json
{
  "version": 1,
  "type": "vehicle.state",
  "message_id": "d834b9f7-cba5-4b69-a526-e652619c5f15",
  "timestamp": "2026-11-18T18:30:00Z",
  "payload": {
    "observed_at": "2026-11-18T18:29:59Z",
    "speed_kmh": 0.0,
    "gear": "P",
    "soc_percent": 68.0,
    "electric_range_km": 290,
    "charging_status": "not_charging",
    "inside_temp_c": 21.5,
    "climate_on": false,
    "driver_target_c": 22.0,
    "source": "byd_auto",
    "quality": {"speed_kmh": "measured", "gear": "measured"}
  }
}
```

These are **example values**, not actual readings. JSON field names and enumerations are language-neutral; do not transmit localized Chinese/English UI text as protocol states.

Publish an allowlisted snapshot approximately every **10 seconds** while active, plus significant changes (with throttling). Include timestamp and source/quality flags. Send `null` for unavailable measurements; do not convert unknown to zero.

### Server → car: command

```json
{
  "version": 1,
  "type": "command.execute",
  "message_id": "2c464e1e-5cda-4a91-8c5e-418ef658b55d",
  "timestamp": "2026-11-18T18:30:02Z",
  "payload": {
    "command_id": "a2a94d90-06fa-43c3-acaa-30b17a317162",
    "action": "climate.power",
    "parameters": {"enabled": true},
    "expires_at": "2026-11-18T18:30:12Z"
  }
}
```

### Car → server: result

```json
{
  "version": 1,
  "type": "command.result",
  "message_id": "da455dd8-b7a7-4ab7-9072-4f7bc2996650",
  "timestamp": "2026-11-18T18:30:04Z",
  "payload": {
    "command_id": "a2a94d90-06fa-43c3-acaa-30b17a317162",
    "status": "confirmed",
    "detail_code": null
  }
}
```

Allowed final status: `confirmed`, `sent_unconfirmed`, `rejected`, `unsupported`, `failed`, `expired`. `accepted` / `delivered` are intermediate states, **not proof that a physical action happened**.

A server must validate that a result comes from the authenticated vehicle to which the command was delivered. Phone clients cannot submit car results.

### Phone state update

The server may emit `vehicle.state` and `vehicle.connection` messages only to authorized owner subscriptions. Include `observed_at`, server `received_at`, `online` and `stale`. Never present a cached snapshot as live.

## 9. Vehicle state model and retention

Define explicit Pydantic models with JSON schema and contract tests for every field in the [vehicle data and command catalog](remote-vehicle-catalog.md). That catalog defines the v1 field names, units, nested objects, enums and source mappings. Include online and last-seen timestamps, state freshness, protocol version and supported capabilities. Send catalog fields when remote sharing is enabled, using null for unavailable observations. Omit `altitude_m`, `trip_distance_km`, `trip_electric_kwh_per_100km` and `trip_fuel_l_per_100km` unless the owner separately opts in.

Include `source` (`byd_auto`, `di_plus`, or `mock`) and `quality`, mapping field names to `measured`, `derived`, or `unknown`. Clearly identify mock data. A snapshot timestamp does not establish freshness of each observation: the car must track speed and gear freshness independently for safety checks. Preserve provenance of derived readings and never forward unsanitized source errors.

**Default privacy:** no VIN, GPS coordinates, Android ID, SIM details, hardware serial numbers, MACs, arbitrary system properties or per-second raw telemetry upload. Location is a separate **explicit opt-in** feature requiring a client-side permission and a server capability declaration.

Store **only the latest allowlisted snapshot** per vehicle in v1, with source and receive timestamps; no historical location or continuous trip recording. Mark a snapshot stale after **30 seconds**, even if the user remains online. Allow owners to delete stored snapshots when unpairing.

Data retention defaults (configurable by operator): pairing records 24 hours max, command/audit metadata 30 days, revocation/security records according to documented security policy. Do not put sensitive state in access logs.

## 10. Commands and safety

### v1 allowlist

| Action | Server validation | Existing car-side command |
|---|---|---|
| `climate.power` | Boolean `enabled` | `VehicleCommand.AcPower` |
| `climate.temperature` | `zone=driver/passenger`, numeric `celsius` in the car-reported supported range and step | `VehicleCommand.AcTemperature` |
| `climate.auto` | Boolean `enabled` | `VehicleCommand.AcAuto` |

Capabilities must come from the connected car. A command unsupported by the model/firmware is denied or returns `unsupported`, not silently emulated.

Reject `climate.fan`, `climate.wind`, `climate.cycle`, seat heating and ventilation, glass/windows, sunroof, sunshade, defrost, remote lock/unlock and `Quick` commands in v1. The catalog lists their local equivalents; they are not remotely enabled. Adding actions requires a documented safety review and explicit opt-in.

### Mandatory command lifecycle

1. Owner sends `POST /v1/vehicles/{id}/commands` with action, typed parameters and a unique `Idempotency-Key` header (UUID).
2. Server validates the owner, capabilities, rate limits, currently connected car, latest telemetry freshness and action allowlist.
3. Server rejects if the car is offline, its state is stale or movement safety cannot be established. **No offline command queue.**
4. The server creates a command record with a **10-second TTL**, forwards once to the active car connection and responds `202` with command ID.
5. The car independently checks **current** local state immediately before executing: fresh reading, gear `P`, speed below 1 km/h, supported command, and remote access explicitly enabled. Unknown speed/gear is a rejection, not a safe default.
6. The car deduplicates IDs across short reconnects, calls the existing repository adapter and reports the actual result. A sent command without confirmed readback becomes `sent_unconfirmed`; optimistic state updates are not readback.
7. Server records the result and notifies subscribed phone clients. Expired commands are never replayed on reconnect.

Default rate limit: at most **6 remote control requests per minute per vehicle** (configurable downward). Commands with the same idempotency key and payload return the original result; same key with different payload is rejected. Duplicate WebSocket messages must never execute the action twice.

Commands should be serialized per vehicle to avoid conflicts, especially with the existing repository's 350 ms debounce and 3-second readback window.

## 11. Persistence model

Use Alembic migrations from the start. Minimum tables:

| Table | Important fields |
|---|---|
| `users` | UUID, normalized unique email, Argon2id password hash, status, created_at |
| `user_sessions` | UUID, user_id, refresh-token hash, expiry, revocation |
| `vehicles` | UUID, owner_id, display_name, created_at, last_seen_at, pairing status |
| `vehicle_credentials` | UUID, vehicle_id, credential hash, created_at, last_used_at, revoked_at |
| `pairings` | UUID, QR-secret hash, car-secret hash, claimed user, expiry, state, consumed_at |
| `vehicle_state` | vehicle_id (unique), JSON allowlist snapshot, observed_at, received_at |
| `commands` | UUID, vehicle_id, requesting user, action, params, idempotency key, status, timestamps, expiry |
| `audit_events` | UUID, actor type/id, vehicle_id, event type, timestamp, redacted metadata |

Provide indexes for owner vehicle lists, active credential lookup, pairing expiry, command vehicle/idempotency and audit time. Use transactions for pairing claims, ownership, credential issuance and idempotency races.

Credentials must be hashed with appropriate algorithms (password: Argon2id; random bearer credentials: keyed hash or SHA-256 over sufficiently random tokens). Never return stored credential hashes to clients.

## 12. Server configuration and deployment

Create these files:

```text
server/
  pyproject.toml
  uv.lock                  # or equivalent committed lockfile
  src/dicar_server/
    main.py
    config.py
    api/{auth,pairing,vehicles,commands,discovery}.py
    ws/{car,mobile,registry,protocol}.py
    models/
    services/{auth,pairing,relay,commands,state}.py
    db/{base,session,models}.py
    security/
  alembic/
  tests/{unit,integration,contract,security}/
  Dockerfile
deploy/
  docker-compose.yml
  Caddyfile
  .env.example
docs/
  server-specification.md
  self-hosting.md
  protocol-v1.md
```

### Configuration keys

| Environment variable | Default / behavior |
|---|---|
| `DICAR_PUBLIC_URL` | Required absolute HTTPS origin; advertised to QR pairing and mobile |
| `DICAR_DATABASE_URL` | SQLite in self-host mode; PostgreSQL for public |
| `DICAR_REGISTRATION_MODE` | `disabled` by default; `invite` or `open_verified` opt-in |
| `DICAR_SECRET_KEY` | Generated, persistent high-entropy secret for signing/protecting sessions |
| `DICAR_LOG_LEVEL` | `INFO` |
| `DICAR_STATUS_TTL_SECONDS` | 30 |
| `DICAR_COMMAND_TTL_SECONDS` | 10 |
| `DICAR_PAIRING_TTL_SECONDS` | 120 |
| `DICAR_COMMANDS_PER_MINUTE` | 6 |
| `DICAR_REDIS_URL` | Optional; mandatory for multi-process shared routing |
| `DICAR_SMTP_*` | Only when verified email registration/password recovery is enabled |

Validate configuration at startup; fail fast if an Internet-facing installation has an insecure/placeholder signing key, HTTP public URL, or an unavailable required SMTP verification path.

Provide a documented bootstrap:

```bash
cp deploy/.env.example deploy/.env
# Set a real public hostname and generate persistent secrets
docker compose -f deploy/docker-compose.yml up -d
docker compose -f deploy/docker-compose.yml exec api dicar-admin create-owner
```

Docker Compose must start Caddy + API (+ optional Postgres). Only Caddy's 80/443 should be exposed publicly; API/DB/Redis remain on an internal Docker network. Caddy handles certificate issuance/renewal for DNS-resolvable domains. Provide a local-only developer profile without production TLS assumptions.

Document database backup/restore, admin account recovery, configuration upgrades, migrations, log rotation and secret rotation. Do not embed shared API keys or certificates in the public repository.

## 13. Security and privacy requirements

- Enforce ownership checks **at the point of subscription, command creation and command result delivery**; fail closed on database or auth errors.
- Apply rate limits to login, registration, pairing, command submission and WebSocket connection attempts. For hosted/multi-worker deployments, use a shared limiter rather than worker-local counters.
- Verify WebSocket authentication on handshake; close sessions promptly on revocation, unpairing or expired authorization.
- Explicitly bound message size, JSON nesting, number of connections per account/vehicle, concurrent pending commands and idle time.
- Use structured logs with request ID and non-sensitive event codes; never log passwords, tokens, QR secrets, live GPS or whole telemetry payloads.
- Prefer minimal account PII. Support data export and deletion, define server operator privacy responsibilities and document hosted telemetry visibility.
- Refuse all remote operations when the client reports motion or the head unit cannot verify it is parked. Do not rely only on the possibly stale server snapshot.
- Do not expose `/docs`, `/redoc`, `/metrics`, admin operations or debug endpoints publicly by default in production.
- Provide CI checks for dependency advisories, static analysis, tests and migration consistency.
- Keep device-to-server encrypted transport independent of Android/Google Play Services.

## 14. Reliability and operational behavior

- Car socket loss: mark car offline within 60 seconds and reject new commands immediately once disconnection is known.
- App wake/reconnect: publish fresh capabilities and state; **do not replay expired control commands**.
- Server restart: restore user and pairing metadata but not active socket sessions; active commands transition to `failed` or `expired` as appropriate.
- Slow client: bound outgoing queues, drop replaceable old status updates, never silently drop command result messages.
- Reconnection backoff: start around 1 second, cap around 60 seconds, with jitter.
- A car may continue reporting locally while the server is unavailable; normal local DiCar controls must remain independent.
- Server must distinguish `offline`, `online_with_stale_state` and `online_with_fresh_state`.
- Export Prometheus counters/gauges for active sockets, state update age, command outcomes, auth failures and pairing outcomes; do not label metrics by VIN, account email or high-cardinality identifiers.
- Set reasonable timeouts for HTTP, DB, WebSocket and reverse proxy. Use UTC timestamps everywhere.

## 15. Client-facing requirements (integration contract)

### Head-unit Android app

- Add Remote Access screen: disabled by default; configurable server URL; pair/unpair; show connection and error state.
- Add outbound connector using the server's v1 JSON protocol and the existing VehicleRepository.
- Upload **only allowlisted fields**. Update the current app's "no vehicle data uploaded" privacy text when remote access is enabled; accurately state the selected backend receives telemetry.
- No bypass of Android sleep restrictions. If head unit is suspended or powered off, report it offline.
- Keep credentials protected in Android Keystore-backed local storage when possible; provide revocation if encrypted storage is lost or device is sold.

### Mobile Android app

- Offer `Default DiCar server` and `Custom server URL`.
- Support login, QR scan, clear server hostname confirmation, list of vehicles, stale/offline indicators, read-only state, optional climate controls and session revocation.
- Verify TLS for user-supplied servers. Do not use WebView or browser-only auth as a required dependency on a BYD head unit.
- Never promise command success before the car sends a definitive result.

## 16. Minimum deliverables

1. Python FastAPI application, typed OpenAPI, WebSocket protocol implementation and SQLAlchemy/Alembic persistence.
2. Dockerfile, Docker Compose, Caddy example and documented self-hosted first-user bootstrap.
3. Hosted mode with per-user data isolation; registration disabled by default, optional verified registration.
4. Functional car/phone pairing, credential lifecycle, disconnect and unpair.
5. State relay, freshness metadata, climate command relay, expiry/idempotency, audit and safety checks.
6. Python unit/integration/security tests with mocked head-unit and phone WebSocket clients.
7. `docs/protocol-v1.md` and `docs/self-hosting.md`, including concrete request/response examples.
8. GitHub Actions workflow to lint, test, build server image, run migrations and verify startup.

### Recommended development sequence

| Phase | Scope | Completion condition |
|---|---|---|
| P0 | Repo structure, env validation, DB migrations, health, Docker | `docker compose up -d` starts a secure, testable service |
| P1 | Accounts, pairing and single-car WSS | Authenticated mock car pairs to mock phone without cross-user data leaks |
| P2 | Typed state, REST snapshot and phone WSS | Status arrives with correct timestamps and stale/offline indicators |
| P3 | Climate commands and car result relay | Idempotent control, expiry, confirmation and motion checks pass |
| P4 | Android integration and docs | Real head unit connects; phone displays data; supported climate commands verified while parked |
| P5 | Public hosting hardening, monitoring, backup, abuse controls | Public instance supports multiple independent owners and is operationally manageable |

## 17. Acceptance tests (must pass)

- Fresh self-hosted deployment can be provisioned without Firebase, Google account, or central DiCar registration.
- Owner A cannot list, subscribe, control or infer the existence of owner B's vehicle.
- An unpaired car cannot submit state or execute owner commands.
- A QR pairing request expires after 120 seconds, cannot be claimed twice and requires a physical car confirmation.
- Refresh token rotation and device credential revocation prevent reuse after logout/unpair.
- Car offline: command request fails immediately, is **never queued for later execution**.
- A command older than TTL is rejected at both server and car.
- Duplicate idempotency key does not actuate climate twice.
- Unknown gear/speed or non-P gear: car refuses command regardless of server's cached status.
- Server crash/restart and intermittent connectivity do not trigger stale command replay.
- A successful send without readback is reported `sent_unconfirmed`, never `confirmed`.
- Missing telemetry values remain `null` and state is stale after 30 seconds.
- Self-hosted private domains work with valid TLS trust; no requirement for Google Play Services.
- Reuse of remote credential from another server/vehicle is rejected.
- Protocol contract tests cover the exact v1 JSON schema and are reusable by Android clients.

## 18. Scope decisions requiring explicit later implementation

The defaults above define an achievable MVP. Future design decisions should be expressed as separate proposals, not silently added:

- Whether to provide a managed public default server and its account verification/abuse policy.
- Hosting and subscription costs, legal/privacy policy, retention and account recovery.
- Additional features such as location, telemetry history, Home Assistant/MQTT integration, push notifications and more sensitive commands.
- Techniques for reaching a sleeping head unit (possibly T-Box integration), which **cannot be solved by the relay server alone**.
- Production horizontal scaling with PostgreSQL/Redis and shared connection routing.

The Python server must remain usable in independently hosted installations even if the official public service is discontinued.
