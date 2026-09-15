# extension-calling

A CPaaS-style Voice API built on Java 21 / Spring Boot 3.3.x, integrating with a
FreeSWITCH server over the Event Socket Library (ESL). It exposes REST APIs to:

1. **Initiate and track outbound calls** between SIP extensions.
2. **Dynamically provision (create/list/get/delete) FreeSWITCH SIP directory
   users/extensions**, without touching FreeSWITCH's config by hand.

Extensions `1001` and `1002` are treated as pre-existing, permanently
protected extensions — they can never be deleted through the API, and neither
feature ever modifies them.

---

## Architecture

```
Controller  -->  Service  -->  Repository  -->  FreeSwitchClient / FreeSwitchDirectoryService  -->  FreeSWITCH (ESL)
```

- Controllers only handle HTTP concerns (validation, status codes) — they
  never talk to FreeSWITCH directly.
- `FreeSwitchClient` is a single persistent, reconnecting ESL connection
  shared by both features (call origination and `reloadxml` after directory
  changes).
- Call state is kept in an in-memory `ConcurrentHashMap`-backed repository,
  updated as FreeSWITCH ESL events arrive (`CHANNEL_CREATE`, `CHANNEL_ANSWER`,
  `CHANNEL_HANGUP_COMPLETE`, etc.).
- Directory users are written as FreeSWITCH XML directly into FreeSWITCH's
  `directory/default/` folder using safe, DOM-based XML generation (no string
  concatenation) and atomic file writes (temp file + atomic move), then
  applied with `reloadxml` over the same ESL connection.

```
backend/src/main/java/com/freeswitch/calling/
├── controller/     REST endpoints (VoiceCallController, VoiceUserController)
├── service/        Business logic (VoiceCallService, VoiceUserService)
├── freeswitch/      ESL integration (FreeSwitchClient, FreeSwitchEventListener,
│                    FreeSwitchDirectoryService)
├── repository/      In-memory / FreeSWITCH-backed repositories
├── model/           Domain models (Call, CallStatus, CallDirection, VoiceUser, ...)
├── dto/             Request/response payloads
├── config/          FreeSwitchProperties (application.yml binding)
└── exception/       GlobalExceptionHandler + typed exceptions
```

---

## Prerequisites

- Java 21
- Maven 3.9+
- A running FreeSWITCH server reachable over ESL, with `1001`/`1002` already
  configured as SIP extensions
- This application **must run on (or have filesystem + ESL access to) the
  same machine as FreeSWITCH**, since the provisioning feature writes files
  directly into FreeSWITCH's local `directory/default/` folder.

---

## Configuration

All configuration lives in `src/main/resources/application.yml` and can be
overridden with environment variables:

| Property | Env var | Default | Purpose |
|---|---|---|---|
| `freeswitch.host` | `FREESWITCH_HOST` | `localhost` | ESL host |
| `freeswitch.port` | `FREESWITCH_PORT` | `8021` | ESL port |
| `freeswitch.password` | `FREESWITCH_PASSWORD` | `ClueCon` | ESL auth password |
| `freeswitch.connection-timeout` | — | `5000` (ms) | ESL connect timeout |
| `freeswitch.command-timeout` | — | `10000` (ms) | ESL command timeout |
| `freeswitch.originate.dial-prefix` | — | `user/` | Dial-string prefix |
| `freeswitch.originate.caller-id-name` | — | `CPaaS` | Outbound caller-ID name |
| `freeswitch.directory.path` | `FREESWITCH_DIRECTORY_PATH` | `/usr/local/freeswitch/conf/directory/default` | Where per-extension XML files are read/written |
| `freeswitch.directory.protected-extensions` | `FREESWITCH_PROTECTED_EXTENSIONS` | `1001,1002` | Comma-separated extensions that can never be deleted |
| `app.cors.allowed-origins` | `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated browser origins allowed to call `/api/v1/voice/**` (the frontend's origin) |

In production, set `FREESWITCH_PASSWORD` (and any other secrets) via
environment variable or a secrets manager — never commit real credentials.
`application-local.yml` / `.env` are already git-ignored for this reason.

---

## Build & Run

```bash
cd extension-calling
mvn clean test
mvn spring-boot:run
```

Or run the packaged jar:

```bash
mvn clean package
java -jar target/extension-calling-*.jar
```

The app starts on port `8080` by default (`server.port`).

---

## API Reference

### 1. Outbound Call Initiation

**`POST /api/v1/voice/calls`** — start an outbound call bridging two extensions.

```bash
curl -X POST http://localhost:8080/api/v1/voice/calls \
  -H "Content-Type: application/json" \
  -d '{"from":"1001","to":"1002"}'
```

Response (`201 Created`):
```json
{
  "callId": "f1a2...-uuid",
  "from": "1001",
  "to": "1002",
  "status": "INITIATED"
}
```

**`GET /api/v1/voice/calls/{callId}`** — poll call status.

```bash
curl http://localhost:8080/api/v1/voice/calls/f1a2...-uuid
```

Status progresses through `INITIATED → RINGING → ANSWERED → COMPLETED`
(or `FAILED` / `BUSY` / `NO_ANSWER`), driven by real FreeSWITCH ESL events.

### 2. Dynamic Extension / User Provisioning

**`POST /api/v1/voice/users`** — create a new SIP extension.

```bash
curl -X POST http://localhost:8080/api/v1/voice/users \
  -H "Content-Type: application/json" \
  -d '{"extension":"1020","password":"1020pass","name":"Test User 1020"}'
```

Response (`201 Created`):
```json
{
  "extension": "1020",
  "name": "Test User 1020",
  "status": "ACTIVE",
  "source": "PROVISIONED_BY_API"
}
```
(Password is never returned, logged, or thrown in an exception message.)

**`GET /api/v1/voice/users`** — list all extensions (API-provisioned and
pre-existing, e.g. `1001`/`1002`).

**`GET /api/v1/voice/users/{extension}`** — get one extension.

**`DELETE /api/v1/voice/users/{extension}`** — delete an extension.
Returns `403 VOICE_USER_PROTECTED` for `1001`/`1002`.

All error responses share one JSON shape:
```json
{
  "timestamp": "2026-09-15T10:00:00Z",
  "status": 409,
  "error": "VOICE_USER_ALREADY_EXISTS",
  "message": "Voice user 1020 already exists",
  "path": "/api/v1/voice/users"
}
```

---

## Testing

```bash
mvn clean test
```

Covers, among others:
- `FreeSwitchDirectoryServiceTest` — XML generation/escaping, atomic writes,
  path-traversal prevention, protected-extension deletion, directory listing
  filtered to real numeric-extension files only.
- `VoiceUserServiceTest`, `VoiceUserControllerTest` — service/controller
  layers with FreeSWITCH mocked out.
- `VoiceUserProvisioningIntegrationTest` — full REST → filesystem round trip
  against a real temp directory, with only the ESL boundary
  (`FreeSwitchClient`) mocked.
- `VoiceUserProvisioningLiveFreeSwitchTest` — opt-in test against a real
  FreeSWITCH server (skipped by default).

---

## Out of scope (by design)

Inbound calling, call transfer, hold/resume, mute/unmute, conferencing, IVR,
recording, TTS/STT, billing, analytics, number provisioning, authentication,
and webhooks beyond call-state tracking are explicitly not part of this
project.
