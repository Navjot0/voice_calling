# extension-calling frontend

A React + TypeScript + Vite admin console for the `extension-calling` Voice
API backend (Spring Boot). It talks to the backend exclusively over REST at
`${VITE_API_BASE_URL}/api/v1/voice` - it never implements FreeSWITCH logic
or connects to ESL itself.

## Stack

React 18, TypeScript, Vite, React Router 6, Axios, plain CSS (no UI
framework, no extra state-management library - `useState`/`useEffect` plus
two small custom hooks are enough for this app's scope).

## Prerequisites

- Node.js 18+ and npm
- The `extension-calling` backend running and reachable over HTTP

## Local development

```bash
cd frontend
npm install
cp .env.example .env
# edit .env if your backend isn't at http://localhost:8080
npm run dev
```

The app runs at `http://localhost:5173` (Vite's default) and talks to the
backend URL configured in `.env` via `VITE_API_BASE_URL`.

## Configuration

The backend URL is never hard-coded - it's read at build time from the
`VITE_API_BASE_URL` environment variable (see `src/api/client.ts`). Copy
`.env.example` to `.env` and set it:

```env
VITE_API_BASE_URL=http://localhost:8080
```

The Axios client appends `/api/v1/voice` automatically, so do not include it
in `VITE_API_BASE_URL`.

An optional second variable, `VITE_PROTECTED_EXTENSIONS`, controls which
extensions the UI treats as protected (hides the Delete button for). It
defaults to `1001,1002`, matching the backend's default
`freeswitch.directory.protected-extensions`. The backend is still the source
of truth - if it rejects a delete with `403 VOICE_USER_PROTECTED`, the UI
surfaces that error regardless of this setting.

Because Vite environment variables are baked in at build time, changing
`VITE_API_BASE_URL` requires rebuilding (`npm run build`), not just editing
a config file after the fact - see the deployment section below for how to
handle this without touching source code.

## Production build

```bash
npm run build
```

Type-checks with `tsc --noEmit` and then builds with Vite; output goes to
`dist/`. Preview the production build locally with:

```bash
npm run preview
```

## Deploying to the Linux server (or any host)

`dist/` is a static site - serve it with any static file server (nginx,
`serve`, a container, etc.) and point it at the backend's real URL:

```bash
# On the target machine, with the backend's real reachable URL:
echo "VITE_API_BASE_URL=http://192.168.1.3:8080" > .env
npm run build
# copy dist/ to wherever your web server serves static files from, e.g.:
# rsync -a dist/ /var/www/extension-calling-frontend/
```

Repeat the `.env` + `npm run build` step per environment - this is what lets
the backend URL change per deployment without touching any source file, as
required. If you'd rather not rebuild per environment, an alternative is to
serve `dist/` behind a reverse proxy that forwards `/api/v1/voice/*` to the
backend and set `VITE_API_BASE_URL=` (empty) so requests are same-origin;
that requires reverse-proxy configuration, not a frontend code change.

## CORS

The frontend (`http://localhost:5173` in dev, or wherever `dist/` is served
from in production) runs on a different origin than the Spring Boot backend
(`http://localhost:8080` / `http://<server>:8080`), so it needs the backend's
permission to call it from a browser. This is now configured in the backend
itself (`backend/src/main/java/.../config/WebConfig.java` +
`CorsProperties.java`), scoped to `/api/v1/voice/**` only - the frontend does
not and should not work around CORS with insecure browser-side hacks.

The allowed origin list is environment-configurable, the same way the
FreeSWITCH connection settings are:

```yaml
app:
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:5173}
```

- Local development needs no changes - it defaults to Vite's dev server
  (`http://localhost:5173`).
- In any other environment, set `CORS_ALLOWED_ORIGINS` (comma-separated) on
  the backend to wherever the built frontend is actually served from, e.g.:

  ```bash
  CORS_ALLOWED_ORIGINS=http://192.168.1.3,http://192.168.1.3:5173 mvn spring-boot:run
  ```

Keep this list as narrow as possible - never widen it to a wildcard in
production.

## Project structure

```text
src/
├── api/           Axios client + typed API modules (callsApi, usersApi)
├── components/    Reusable UI: Layout, Sidebar, Header, StatusBadge, ConfirmDialog, ...
├── pages/         Route-level views (Dashboard, Calls, MakeCall, CallDetails, Users, ...)
├── types/         TypeScript models matching the backend's DTOs exactly
├── hooks/         useUsers (list + refresh), useCalls (polling + local call log)
├── utils/         Formatters (dates, protected-extension lookup)
└── styles/        Global responsive CSS (no UI framework)
```

## Notes on the API contract

Types in `src/types/` mirror the backend's actual Java records
(`CreateCallRequest`, `CreateCallResponse`, `CallResponse`,
`CreateVoiceUserRequest`, `VoiceUserResponse`, `DeleteVoiceUserResponse`,
`ApiError`) field-for-field, read directly from the backend source. Two
things worth knowing:

- **No call-list endpoint exists yet.** `GET /api/v1/voice/calls` is not
  implemented by the backend, so the Calls page shows a client-side log of
  calls made from that browser (`useCallHistory`, backed by `localStorage`,
  storing no secrets) rather than inventing a backend endpoint. `callsApi.listCalls`
  is written and ready to use as soon as the backend adds a real one - swap
  it in for the local history with a one-line change.
- **No explicit "protected" field.** `VoiceUserResponse` doesn't return a
  protected flag - protection is enforced server-side by rejecting deletes
  with `403 VOICE_USER_PROTECTED`. The UI infers likely-protected extensions
  client-side (`VITE_PROTECTED_EXTENSIONS`, defaulting to `1001,1002`) purely
  to decide whether to show a Delete button; the backend's 403 response is
  still handled gracefully regardless, in case the two ever disagree.

## Security

- Extension passwords are held only in local component state during the
  Create Extension form submission, are cleared immediately after the
  request completes, and are never written to `localStorage`, logged, or
  echoed back in the UI.
- No FreeSWITCH credentials or other secrets live in frontend source or are
  bundled into the build - the frontend only ever talks to the Spring Boot
  backend's REST API.
