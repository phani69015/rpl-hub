# RPL Hub

## Run locally
```
cd backend && podman compose up -d postgres temporal   # Postgres :5432, Temporal :7234 (UI :8234)
cd backend && ./gradlew bootRun              # API :8080 (reads backend/.env; copy .env.example first)
cd frontend && npm install && npm run dev    # UI :5173, proxies /api to :8080
```
Sign in with `+919999999999` (bootstrap admin) and OTP `123456` (mocked). Any other phone becomes a WORKER.
Try `backend/samples/qualifications.csv` (mix of valid/invalid rows) and `backend/samples/qualifications.json`.

## Separate deployment
`backend/` and `frontend/` share nothing at build or run time (each has its own env, tooling and data).
- Backend: set `CORS_ALLOWED_ORIGINS` to the frontend origin(s). Evidence/data live in `backend/data`.
- Frontend (e.g. Netlify): build with `VITE_API_URL=https://your-api-host` (see `frontend/.env.example`).
- AI switch: `app.ai.enabled` in `backend/src/main/resources/application.yml` (env `AI_ENABLED`, default true).
  When off, no Anthropic calls are made, no AI summary is generated, matching uses keywords, and the UI shows
  "AI feature is turned off".

## Import API (ADMIN)
- `POST /api/imports` (multipart `file`: .csv/.xlsx/.json) → drafts + issues; re-import is idempotent (content hash).
- `GET /api/qualification-versions[?status=]`, `GET/PUT /api/qualification-versions/{id}` (PUT drafts only)
- `POST /api/qualification-versions/{id}/publish` (needs `can_publish`; supersedes previous published version)

Columns: qualification_code, title, trade, nsqf_level, source, competency_code, competency_title, mandatory,
max_score, rubric, evidence_requirements (`|` separated). One row per competency.

## Case flow (all implemented)
Worker declares (offline-capable) → Temporal `CaseWorkflow` matches published qualifications (Anthropic if
`ANTHROPIC_API_KEY` is set in `backend/.env`, otherwise a labelled keyword fallback) → admin confirms qualification →
admin assigns an eligible assessor (trade authorisation, see Admin → Assessors) → assessor scores / requests evidence /
submits → workflow generates the report + advisory AI summary → authorised decision-maker (`can_decide`) records the
outcome → worker sees result. Report: HTML/PDF from `backend/src/main/resources/templates/report.hbs`.

Roles: add assessors in the Admin UI (phone + trades); they sign in with the mock OTP. Any unknown phone is a worker.

## Backend structure (`backend/src/main/java/com/rplhub`)
- `controller/` HTTP endpoints only (no SQL, no business rules)
- `service/` business rules, transactions, audit calls
- `repository/` all SQL (JdbcClient)
- `model/` records: domain views, request bodies, constants (`CaseStatus`, `Outcome`)
- `exception/` domain exceptions and the single `@RestControllerAdvice`
- `config/` security/CORS and Temporal wiring; `workflow/` Temporal workflow, activities, gateway; `ai/` Anthropic client

## Design notes
- The DB case status is the source of truth; the workflow only drives automated steps and sleeps for humans. Signals
  just wake it (plus an hourly wake-up), so lost/duplicate signals are harmless and a failed workflow is restarted
  from DB state via Admin → case → *Retry / resume*.
- Idempotency: client-generated UUIDs for cases and evidence (offline sync), unique constraints (one decision and one
  submission per case, content hash for imports), guarded status transitions under row locks.
- Published qualification versions are immutable and pinned to the case.
- Evidence files: local folder `backend/data/evidence` (`EVIDENCE_DIR`), served only via authorised endpoints.

## Tests
```
cd backend && ./gradlew test                 # parsing, matching, Temporal workflow (in-memory test server)
python3 backend/scripts/e2e.py                       # full flow against running backend + Postgres + Temporal
```
