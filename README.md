# RPL Hub

## Run locally
```
podman compose up -d postgres temporal      # Postgres :5432, Temporal :7234 (UI :8234, unused until workflow phase)
cd backend && ./gradlew bootRun              # API :8080 (reads ../.env; copy .env.example first)
cd frontend && npm install && npm run dev    # UI :5173, proxies /api
```
Sign in with `+919999999999` (bootstrap admin) and OTP `123456` (mocked). Any other phone becomes a WORKER.
Try `samples/qualifications.csv` (mix of valid/invalid rows) and `samples/qualifications.json`.

## Import API (ADMIN)
- `POST /api/imports` (multipart `file`: .csv/.xlsx/.json) → drafts + issues; re-import is idempotent (content hash).
- `GET /api/qualification-versions[?status=]`, `GET/PUT /api/qualification-versions/{id}` (PUT drafts only)
- `POST /api/qualification-versions/{id}/publish` (needs `can_publish`; supersedes previous published version)

Columns: qualification_code, title, trade, nsqf_level, source, competency_code, competency_title, mandatory,
max_score, rubric, evidence_requirements (`|` separated). One row per competency.

## Case flow (all implemented)
Worker declares (offline-capable) → Temporal `CaseWorkflow` matches published qualifications (Anthropic if
`ANTHROPIC_API_KEY` is set in `.env`, otherwise a labelled keyword fallback) → admin confirms qualification →
admin assigns an eligible assessor (trade authorisation, see Admin → Assessors) → assessor scores / requests evidence /
submits → workflow generates the report + advisory AI summary → authorised decision-maker (`can_decide`) records the
outcome → worker sees result. Report: HTML/PDF from `backend/src/main/resources/templates/report.hbs`.

Roles: add assessors in the Admin UI (phone + trades); they sign in with the mock OTP. Any unknown phone is a worker.

## Design notes
- The DB case status is the source of truth; the workflow only drives automated steps and sleeps for humans. Signals
  just wake it (plus an hourly wake-up), so lost/duplicate signals are harmless and a failed workflow is restarted
  from DB state via Admin → case → *Retry / resume*.
- Idempotency: client-generated UUIDs for cases and evidence (offline sync), unique constraints (one decision and one
  submission per case, content hash for imports), guarded status transitions under row locks.
- Published qualification versions are immutable and pinned to the case.
- Evidence files: local folder `data/evidence` (`EVIDENCE_DIR`), served only via authorised endpoints.

## Tests
```
cd backend && ./gradlew test                 # parsing, matching, Temporal workflow (in-memory test server)
python3 scripts/e2e.py                       # full flow against running backend + Postgres + Temporal
```
