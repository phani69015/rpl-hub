#!/usr/bin/env python3
"""End-to-end happy path + guard checks against a running backend (needs Postgres + Temporal up).
Usage: python3 scripts/e2e.py   (BASE env overrides http://localhost:8080)"""
import json, os, sys, time, uuid, urllib.request, urllib.error

BASE = os.environ.get("BASE", "http://localhost:8080") + "/api"
RUN = str(int(time.time()))[-6:]
ADMIN = "+919999999999"
WORKER = "+9187" + RUN + "01"
ASSESSOR = "+9186" + RUN + "02"
OTHER_WORKER = "+9185" + RUN + "03"


def call(method, path, token=None, body=None, files=None, expect=None):
    headers = {}
    data = None
    if token: headers["Authorization"] = "Bearer " + token
    if files is not None:
        boundary = uuid.uuid4().hex
        parts = b"".join(
            ("--%s\r\nContent-Disposition: form-data; name=\"file\"; filename=\"%s\"\r\nContent-Type: %s\r\n\r\n" % (boundary, n, t)).encode() + c + b"\r\n"
            for n, t, c in files) + ("--%s--\r\n" % boundary).encode()
        data = parts
        headers["Content-Type"] = "multipart/form-data; boundary=" + boundary
    elif body is not None:
        data = json.dumps(body).encode(); headers["Content-Type"] = "application/json"
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req) as r: code, raw = r.status, r.read()
    except urllib.error.HTTPError as e: code, raw = e.code, e.read()
    if expect is not None and code != expect:
        sys.exit("FAIL %s %s -> %s (expected %s): %s" % (method, path, code, expect, raw[:300]))
    try: return code, json.loads(raw)
    except Exception: return code, raw


def login(phone):
    return call("POST", "/auth/otp/verify", body={"phone": phone, "otp": "123456"}, expect=200)[1]["token"]


def wait_status(token, cid, status, timeout=60):
    for _ in range(timeout):
        c = call("GET", "/cases/" + cid, token, expect=200)[1]
        if c["status"] == status: return c
        time.sleep(1)
    sys.exit("FAIL: case never reached " + status + " (is at " + c["status"] + ")")


def ok(msg): print("ok -", msg)


admin = login(ADMIN)

# 1. qualification: import + publish (unique code per run)
csv = ("qualification_code,title,trade,nsqf_level,source,competency_code,competency_title,mandatory,max_score,rubric,evidence_requirements\n"
       "E2E-%s,Electrician E2E,Electrical,4,e2e,C1,Install domestic wiring,yes,10,rubric,Photo\n"
       "E2E-%s,Electrician E2E,Electrical,4,e2e,C2,Test circuits,no,5,rubric,Log\n") % (RUN, RUN)
imp = call("POST", "/imports", admin, files=[("e2e.csv", "text/csv", csv.encode())], expect=200)[1]
assert imp["versionsCreated"] == 1
vid = [v for v in call("GET", "/qualification-versions?status=DRAFT", admin)[1] if v["code"] == "E2E-" + RUN][0]["id"]
call("POST", "/qualification-versions/%s/publish" % vid, admin, expect=200)
ok("qualification imported and published")

# 2. worker declaration (idempotent upsert + submit)
w = login(WORKER)
cid = str(uuid.uuid4())
decl = {"trade": "Electrical", "experienceYears": 6, "skills": "wiring, testing circuits, domestic installations", "details": "site work", "baseVersion": 0, "submit": False}
c = call("PUT", "/cases/" + cid, w, decl, expect=200)[1]
assert c["status"] == "DRAFT"
call("PUT", "/cases/" + cid, w, decl, expect=200)  # replayed sync: no-op
stale = dict(decl, skills="changed", baseVersion=99)
code, body = call("PUT", "/cases/" + cid, w, stale)
assert code == 409 and "server" in body, "stale update must conflict"
call("PUT", "/cases/" + cid, w, dict(decl, skills="wiring, testing circuits", baseVersion=c["version"]), expect=200)
sub = dict(decl, skills="wiring, testing circuits", baseVersion=c["version"] + 1, submit=True)
call("PUT", "/cases/" + cid, w, sub, expect=200)
call("PUT", "/cases/" + cid, w, sub, expect=200)  # repeated submit: still one case
assert len([x for x in call("GET", "/cases", w)[1]]) == 1
ok("declaration saved, replay idempotent, stale edit conflicts, submit idempotent")

# 3. workflow matches; admin confirms
c = wait_status(w, cid, "AWAITING_QUALIFICATION")
assert any(s["code"] == "E2E-" + RUN for s in c["suggestions"]), c["suggestions"]
ok("workflow produced suggestions (%s)" % c["suggestions"][0]["source"])
o = login(OTHER_WORKER)
assert call("GET", "/cases/" + cid, o)[0] == 404
assert call("GET", "/admin/cases", w)[0] == 403
ok("other worker / non-admin blocked")
call("POST", "/admin/cases/%s/confirm-qualification" % cid, admin, {"versionId": vid}, expect=200)
call("POST", "/admin/cases/%s/confirm-qualification" % cid, admin, {"versionId": vid}, expect=200)

# 4. assessor
call("POST", "/admin/assessors", admin, {"phone": ASSESSOR, "trades": ["electrical"], "canDecide": False}, expect=200)
a = login(ASSESSOR)
cands = call("GET", "/admin/cases/%s/assessor-candidates" % cid, admin, expect=200)[1]
assert any(x["phone"] == ASSESSOR for x in cands)
aid = [x["id"] for x in cands if x["phone"] == ASSESSOR][0]
call("POST", "/admin/cases/%s/assign" % cid, admin, {"assessorId": aid}, expect=200)
assert call("GET", "/cases/" + cid, a)[0] == 200
a2 = login("+9184" + RUN + "04"); call("POST", "/admin/assessors", admin, {"phone": "+9184" + RUN + "04", "trades": ["Plumbing"], "canDecide": False}, expect=200)
assert call("GET", "/cases/" + cid, login("+9184" + RUN + "04"))[0] == 404
assert call("POST", "/admin/cases/%s/assign" % cid, admin, {"assessorId": aid}, expect=200)
ok("assessor assigned; unassigned assessor blocked")

# 5. scoring, validation, evidence request loop
case = call("GET", "/cases/" + cid, a, expect=200)[1]
comps = {x["code"]: x for x in case["qualification"]["competencies"]}
c1, c2 = comps["C1"]["id"], comps["C2"]["id"]
assert call("PUT", "/cases/%s/scores/%s" % (cid, c1), a, {"score": 11, "comment": "x"})[0] == 400
code, body = call("POST", "/cases/%s/submit" % cid, a, {"recommendation": "COMPETENT"})
assert code == 400 and "C1" in body["error"], "mandatory gap must block"
call("PUT", "/cases/%s/scores/%s" % (cid, c1), a, {"score": 8, "comment": "good"}, expect=200)
call("PUT", "/cases/%s/scores/%s" % (cid, c1), a, {"score": 9, "comment": "good, corrected"}, expect=200)
call("POST", "/cases/%s/request-evidence" % cid, a, {"message": "Please upload a photo of the wiring"}, expect=200)
assert call("POST", "/cases/%s/evidence/submit" % cid, w)[0] == 400
eid = str(uuid.uuid4())
for _ in range(2):  # repeated upload = one record
    call("PUT", "/cases/%s/evidence/%s" % (cid, eid), w, files=[("wiring.txt", "text/plain", b"photo placeholder")], expect=200)
assert len(call("GET", "/cases/" + cid, w)[1]["evidence"]) == 1
assert call("PUT", "/cases/%s/evidence/%s" % (cid, str(uuid.uuid4())), w, files=[("x.exe", "application/x-msdownload", b"x")])[0] == 400
call("POST", "/cases/%s/evidence/submit" % cid, w, expect=200)
case = wait_status(a, cid, "IN_ASSESSMENT")
assert case["scores"], "previous progress preserved"
ok("evidence requested, uploaded once, assessment resumed with scores kept")

# 6. submit -> report -> decision
call("POST", "/cases/%s/submit" % cid, a, {"recommendation": "COMPETENT", "notes": "solid"}, expect=200)
call("POST", "/cases/%s/submit" % cid, a, {"recommendation": "COMPETENT", "notes": "solid"}, expect=200)
wait_status(a, cid, "AWAITING_DECISION")
assert call("POST", "/cases/%s/decision" % cid, a, {"outcome": "COMPETENT"})[0] == 403, "assessor without can_decide"
assert call("GET", "/cases/%s/report" % cid, w)[0] == 409, "worker cannot see report before decision"
html = call("GET", "/cases/%s/report" % cid, a, expect=200)[1].decode()
assert "AI-generated summary" in html and "Assessor findings" in html and "C2" in html
code, pdf = call("GET", "/cases/%s/report?format=pdf" % cid, a, expect=200)
assert pdf[:4] == b"%PDF"
call("POST", "/cases/%s/decision" % cid, admin, {"outcome": "COMPETENT", "rationale": "meets criteria"}, expect=200)
call("POST", "/cases/%s/decision" % cid, admin, {"outcome": "COMPETENT", "rationale": "meets criteria"}, expect=200)
assert call("POST", "/cases/%s/decision" % cid, admin, {"outcome": "NOT_YET_COMPETENT"})[0] == 409
final = wait_status(w, cid, "DECIDED")
assert final["decision"]["outcome"] == "COMPETENT" and final["scores"]
assert call("GET", "/cases/%s/report" % cid, w, expect=200)
ok("report (html+pdf), single immutable decision, worker sees result after decision")

# 7. workflow + audit
for _ in range(20):
    st = call("GET", "/admin/cases/%s/workflow" % cid, admin, expect=200)[1]
    if st["state"] == "COMPLETED": break
    time.sleep(1)
assert st["state"] == "COMPLETED", st
actions = [e["action"] for e in call("GET", "/admin/cases/%s/audit" % cid, admin, expect=200)[1]]
for need in ["CASE_SUBMITTED", "MATCH_SUGGESTED", "QUALIFICATION_CONFIRMED", "ASSESSOR_ASSIGNED", "SCORE_RECORDED", "SCORE_CHANGED", "ASSESSMENT_SUBMITTED", "FINAL_DECISION"]:
    assert need in actions, need
ok("workflow COMPLETED; audit trail complete")
print("ALL PASSED")
