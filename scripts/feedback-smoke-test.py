"""Local Stage 4 real HTTP/MySQL acceptance. Uses documented development seeds or env overrides.
Creates unique temporary users/tasks/assets, removes only returned IDs, never resets existing tables.
Run with MEDIAFLOW_API and MEDIAFLOW_STORAGE_ROOT matching the local backend. Not for production.
"""

import base64, json, os, pathlib, re, secrets, subprocess, urllib.request, urllib.error, urllib.parse, http.cookiejar, concurrent.futures

ROOT = pathlib.Path(__file__).resolve().parents[1]
BASE = os.getenv("MEDIAFLOW_API", "http://127.0.0.1:8080")
PREFIX = "feedback_accept_" + secrets.token_hex(5)
config = dict(
    line.split("=", 1)
    for line in (ROOT / ".env").read_text(encoding="utf-8-sig").splitlines()
    if "=" in line and not line.startswith("#")
)
checks = []
task_ids = []
asset_ids = []
user_ids = []


def seed(name):
    for line in (ROOT / "README.md").read_text(encoding="utf-8").splitlines():
        parts = [p.strip() for p in line.split("|")]
        if len(parts) > 3 and parts[1] == name:
            return parts[1], parts[2]
    raise RuntimeError("Local seed not found; set MEDIAFLOW credential variables")


class Client:
    def __init__(self):
        self.token = None
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.cookies)
        )

    def call(
        self,
        method,
        path,
        expected=200,
        data=None,
        authenticated=True,
        raw=None,
        headers=None,
    ):
        h = {"Content-Type": "application/json"}
        h.update(headers or {})
        if authenticated and self.token:
            h["Authorization"] = "Bearer " + self.token
        payload = (
            raw
            if raw is not None
            else json.dumps(data).encode()
            if data is not None
            else None
        )
        req = urllib.request.Request(
            BASE + path, method=method, data=payload, headers=h
        )
        try:
            r = self.opener.open(req, timeout=20)
        except urllib.error.HTTPError as e:
            r = e
        body = r.read()
        status = r.status
        assert status == expected, (method, path, status, expected)
        checks.append({"method": method, "path": path, "status": status})
        if "application/json" in r.headers.get("Content-Type", ""):
            value = json.loads(body)
            assert value["code"] == (0 if expected < 400 else expected * 100), (
                path,
                value["code"],
            )
            return value["data"]
        return body

    def login(self, name, password):
        self.token = self.call(
            "POST",
            "/api/auth/login",
            data={"username": name, "password": password},
            authenticated=False,
        )["accessToken"]
        return self


def existing(name, prefix):
    user, password = seed(name)
    return Client().login(
        os.getenv(prefix + "_USERNAME", user), os.getenv(prefix + "_PASSWORD", password)
    )


def create_user(admin, kind, roles):
    password = secrets.token_urlsafe(20)
    name = PREFIX + "_" + kind
    value = admin.call(
        "POST",
        "/api/admin/users",
        201,
        {"username": name, "displayName": kind, "password": password, "roles": roles},
    )
    user_ids.append(value["id"])
    return value["id"], Client().login(name, password)


def upload(admin, name, restricted=True):
    boundary = "mf" + secrets.token_hex(12)
    content = base64.b64decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a9XcAAAAASUVORK5CYII="
    )
    raw = (
        f'--{boundary}\r\nContent-Disposition: form-data; name="title"\r\n\r\n{PREFIX}_{name}\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="generated.png"\r\nContent-Type: image/png\r\n\r\n'.encode()
        + content
        + f"\r\n--{boundary}--\r\n".encode()
    )
    value = admin.call(
        "POST",
        "/api/media/assets",
        201,
        raw=raw,
        headers={
            "Content-Type": "multipart/form-data; boundary=" + boundary,
            "Idempotency-Key": secrets.token_hex(16),
        },
    )
    asset_ids.append(value["id"])
    if restricted:
        value = admin.call(
            "PUT",
            f"/api/media/assets/{value['id']}/scope",
            data={"accessScope": "RESTRICTED", "version": 0},
        )
    return value


def request(executor, reviewer, asset, title=None, version=None):
    return {
        "title": title or PREFIX,
        "description": "Local task acceptance requirements",
        "assigneeId": executor,
        "reviewerId": reviewer,
        "priority": "NORMAL",
        "dueAt": None,
        "assetIds": [asset],
        "version": version,
    }


def create(admin, req):
    value = admin.call("POST", "/api/tasks", 201, req)
    task_ids.append(value["id"])
    return value


def action(client, task, op, expected=200, reason=None):
    return client.call(
        "POST",
        f"/api/tasks/{task['id']}/{op}",
        expected,
        {"version": task["version"], "reason": reason, "operatorId": -999},
    )


def query(prefix):
    return urllib.parse.urlencode({"keyword": prefix})


def feedback(client, task, content="Please fix", **fields):
    return client.call(
        "POST",
        f"/api/tasks/{task['id']}/feedback",
        201,
        {"type": "TEXT", "content": content, **fields},
        headers={"Idempotency-Key": secrets.token_hex(16)},
    )


def transition(client, task, f, op, expected=200, reason=None):
    return client.call(
        "POST",
        f"/api/tasks/{task['id']}/feedback/{f['id']}/{op}",
        expected,
        {"version": f["version"], "reason": reason},
    )


def upload_feedback(
    client, task, filename, mime, content, kind, expected=201, **fields
):
    boundary = "mf" + secrets.token_hex(16)
    form = {"type": kind, "content": "Attachment explanation", **fields}
    raw = b"".join(
        f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode()
        for k, v in form.items()
    )
    raw += (
        f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\nContent-Type: {mime}\r\n\r\n'.encode()
        + content
        + f"\r\n--{boundary}--\r\n".encode()
    )
    return client.call(
        "POST",
        f"/api/tasks/{task['id']}/feedback",
        expected,
        raw=raw,
        headers={
            "Content-Type": "multipart/form-data; boundary=" + boundary,
            "Idempotency-Key": secrets.token_hex(16),
        },
    )


try:
    admin = existing("admin", "MEDIAFLOW_ADMIN")
    aid, editor = create_user(admin, "editor_a", ["EDITOR"])
    bid, other = create_user(admin, "editor_b", ["EDITOR"])
    rid, reviewer = create_user(admin, "reviewer_a", ["REVIEWER"])
    sid, stranger = create_user(admin, "reviewer_b", ["REVIEWER"])
    asset = upload(admin, "shared", False)
    asset2 = upload(admin, "private")
    t = create(admin, request(aid, rid, asset["id"]))
    t2 = create(admin, request(bid, sid, asset2["id"]))
    base = f"/api/tasks/{t['id']}/feedback"
    admin.call(
        "POST",
        base,
        409,
        {"type": "TEXT", "content": "Pending"},
        headers={"Idempotency-Key": secrets.token_hex(16)},
    )
    t = action(editor, t, "start")
    f = feedback(reviewer, t, "<script>alert('xss')</script>", assetId=asset["id"])[
        "feedback"
    ]
    assert f["creatorId"] == rid
    for client in (other, stranger):
        for suffix in (
            "",
            "/summary",
            f"/{f['id']}",
            f"/{f['id']}/logs",
            f"/{f['id']}/replies",
        ):
            client.call("GET", base + suffix, 404)
        transition(client, t, f, "resolve", 404)
    admin.call("GET", f"/api/tasks/{t2['id']}/feedback/{f['id']}", 404)
    reviewer.call(
        "POST",
        base,
        404,
        {"type": "TEXT", "content": "wrong asset", "assetId": asset2["id"]},
        headers={"Idempotency-Key": secrets.token_hex(16)},
    )
    reviewer.call(
        "POST",
        base,
        400,
        {
            "type": "TEXT",
            "content": "bad time",
            "assetId": asset["id"],
            "timecodeMs": 3,
        },
        headers={"Idempotency-Key": secrets.token_hex(16)},
    )
    reviewer.call(
        "POST",
        base,
        400,
        {"type": "TEXT", "content": " "},
        headers={"Idempotency-Key": secrets.token_hex(16)},
    )
    reviewer.call(
        "POST",
        base,
        400,
        {"type": "TEXT", "content": "x" * 4001},
        headers={"Idempotency-Key": secrets.token_hex(16)},
    )
    key = secrets.token_hex(16)
    retry = [
        reviewer.call(
            "POST",
            base,
            201,
            {"type": "TEXT", "content": "Retry fixture"},
            headers={"Idempotency-Key": key},
        )
        for _ in range(2)
    ]
    assert retry[0]["feedback"]["id"] == retry[1]["feedback"]["id"]
    g = retry[0]["feedback"]
    wav = bytes.fromhex(
        "524946462800000057415645666d74201000000001000100401f0000803e000002001000646174610400000000000000"
    )
    audio = upload_feedback(reviewer, t, "voice.wav", "audio/wav", wav, "AUDIO")
    # Genuine Chromium recordings are produced by the browser acceptance test first.
    for kind in ("audio", "video"):
        fixture = ROOT / f".local/feedback-recorded-{kind}.webm"
        if not fixture.exists():
            raise RuntimeError(
                "Run feedback Playwright recording test to create actual WebM fixture: "
                + str(fixture)
            )
        upload_feedback(
            reviewer,
            t,
            kind + ".webm",
            kind + "/webm",
            fixture.read_bytes(),
            kind.upper(),
        )
    upload_feedback(reviewer, t, "bad.wav", "audio/wav", b"not audio", "AUDIO", 415)
    upload_feedback(reviewer, t, "bad.mp3", "audio/wav", wav, "AUDIO", 415)
    upload_feedback(reviewer, t, "fake.wav", "audio/wav", wav, "VIDEO", 415)
    upload_feedback(reviewer, t, "big.wav", "audio/wav", bytes(10485761), "AUDIO", 413)
    fid = audio["feedback"]["id"]
    att = audio["attachment"]["id"]
    url = f"{base}/{fid}/attachments/{att}"
    assert "storageKey" not in audio["attachment"]
    ticket = editor.call("POST", url + "/access")
    assert (
        editor.call(
            "GET",
            ticket["contentUrl"],
            206,
            authenticated=False,
            headers={"Range": "bytes=0-3"},
        )
        == b"RIFF"
    )
    editor.call("HEAD", ticket["contentUrl"], authenticated=False)
    editor.call("GET", ticket["downloadUrl"], authenticated=False)
    other.call("GET", ticket["contentUrl"], 404)
    other.call("POST", url + "/access", 404)
    editor.call("GET", f"{base}/{fid}/attachments/999999/content", 404)
    editor.call("GET", f"{base}/{f['id']}/attachments/{att}/content", 404)
    assert len(admin.call("GET", "/api/media/assets?" + query(PREFIX))["records"]) == 2
    # Same task lock and version serialize concurrent feedback operations in real MySQL.
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:

        def race(_):
            c = Client()
            c.token = editor.token
            req = urllib.request.Request(
                BASE + base + f"/{g['id']}/resolve",
                method="POST",
                data=json.dumps({"version": 0}).encode(),
                headers={
                    "Authorization": "Bearer " + c.token,
                    "Content-Type": "application/json",
                },
            )
            try:
                r = c.opener.open(req, timeout=20)
            except urllib.error.HTTPError as e:
                r = e
            with r:
                return r.status

        statuses = sorted(pool.map(race, range(2)))
    assert statuses == [200, 409], statuses
    checks.append({"concurrentStatus": statuses})
    g = editor.call("GET", base + f"/{g['id']}")["feedback"]
    transition(reviewer, t, g, "close")
    editor.call(
        "POST",
        base + f"/{f['id']}/replies",
        data={"version": 0, "content": "Fixed <b>plain text</b>", "operatorId": 1},
    )
    f = editor.call("GET", base + f"/{f['id']}")["feedback"]
    transition(reviewer, t, f, "resolve", 403)
    action(editor, t, "submit", 409)
    action(admin, t, "submit", 400)
    t = action(admin, t, "submit", reason="explicit override acceptance")
    logs = admin.call("GET", f"/api/tasks/{t['id']}/logs")["records"]
    assert any(
        l["operationType"] == "SUBMIT_OVERRIDE" and "explicit override" in l["reason"]
        for l in logs
    )
    feedback(reviewer, t, "Additional review feedback")
    editor.call(
        "POST",
        base,
        403,
        {"type": "TEXT", "content": "wrong review role"},
        headers={"Idempotency-Key": secrets.token_hex(16)},
    )
    action(reviewer, t, "approve", 409)
    t = action(reviewer, t, "reject", reason="revision required")
    assert editor.call("GET", base + f"/{g['id']}")["feedback"]["status"] == "CLOSED"
    items = editor.call("GET", base + "?size=100&direction=ASC")["records"]
    assert items[0]["id"] == f["id"]
    for item in items:
        if item["status"] in ("OPEN", "REOPENED"):
            item = transition(editor, t, item, "resolve")["feedback"]
    # RESOLVED is allowed at submission; approval still requires confirmation.
    t = action(editor, t, "submit")
    action(reviewer, t, "approve", 409)
    items = editor.call("GET", base + "?size=100")["records"]
    for item in items:
        if item["status"] == "RESOLVED":
            item = transition(reviewer, t, item, "close")["feedback"]
    f = editor.call("GET", base + f"/{f['id']}")["feedback"]
    transition(reviewer, t, f, "reopen", 400, reason=" ")
    f = transition(reviewer, t, f, "reopen", reason="one more fix")["feedback"]
    f = transition(editor, t, f, "resolve")["feedback"]
    transition(reviewer, t, f, "close")
    summary = reviewer.call("GET", base + "/summary")
    assert (
        summary["unresolved"] == 0
        and summary["resolved"] == 0
        and summary["closed"] == summary["total"]
    )
    # Reassignment revokes even still-valid native player cookies; shared base media stays readable.
    admin.call(
        "PUT",
        f"/api/tasks/{t['id']}",
        data=request(bid, rid, asset["id"], version=t["version"]),
    )
    t = admin.call("GET", f"/api/tasks/{t['id']}")
    editor.call("GET", ticket["contentUrl"], 404, authenticated=False)
    editor.call("GET", base, 404)
    assert editor.call("GET", f"/api/media/assets/{asset['id']}")["id"] == asset["id"]
    other.call("POST", url + "/access")
    admin.call("PUT", f"/api/admin/users/{bid}/roles", data={"roles": ["REVIEWER"]})
    other.call("GET", ticket["contentUrl"], 404, authenticated=False)
    admin.call("PUT", f"/api/admin/users/{bid}/roles", data={"roles": ["EDITOR"]})
    admin.call(
        "PUT",
        f"/api/admin/users/{bid}",
        data={"displayName": "Disabled fixture", "status": 0},
    )
    other.call("GET", ticket["contentUrl"], 401, authenticated=False)
    admin.call(
        "PUT", f"/api/admin/users/{bid}", data={"displayName": "editor_b", "status": 1}
    )
    t = action(reviewer, t, "approve")
    admin.call(
        "POST",
        base,
        409,
        {"type": "TEXT", "content": "Completed"},
        headers={"Idempotency-Key": secrets.token_hex(16)},
    )
    f = admin.call("GET", base + f"/{f['id']}")["feedback"]
    transition(admin, t, f, "reopen", 409, reason="read only")
    assert (
        admin.call("GET", base + f"/{f['id']}/replies")["records"][0]["creatorId"]
        == aid
    )
    result = {"passed": len(checks), "checks": checks}
    (ROOT / ".local/stage4-http-results.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print("PASS:", len(checks), "real feedback HTTP/MySQL checks")

finally:
    env = os.environ.copy()
    env["MYSQL_PWD"] = config["DB_PASSWORD"]

    def sql(value):
        return subprocess.check_output(
            [
                "mysql",
                "-u" + config.get("DB_USERNAME", "root"),
                "mediaflow",
                "-N",
                "-e",
                value,
            ],
            env=env,
            text=True,
        ).strip()

    if task_ids:
        ids = ",".join(str(int(i)) for i in task_ids)
        rows = sql(
            f"SELECT storage_key FROM task_feedback_attachment WHERE feedback_id IN (SELECT id FROM task_feedback WHERE task_id IN ({ids}))"
        )
        directory = pathlib.Path(
            os.getenv("MEDIAFLOW_STORAGE_ROOT", str(ROOT / ".local/media-storage"))
        )
        for key in rows.splitlines():
            if not re.fullmatch(r"[a-f0-9-]{36}\.bin", key):
                raise ValueError("Invalid generated key")
            (directory / key).unlink(missing_ok=True)
        sql(
            f"START TRANSACTION; DELETE FROM task_feedback_log WHERE feedback_id IN (SELECT id FROM task_feedback WHERE task_id IN ({ids})); DELETE FROM task_feedback_reply WHERE feedback_id IN (SELECT id FROM task_feedback WHERE task_id IN ({ids})); DELETE FROM task_feedback_attachment WHERE feedback_id IN (SELECT id FROM task_feedback WHERE task_id IN ({ids})); DELETE FROM task_feedback WHERE task_id IN ({ids}); COMMIT;"
        )
    if task_ids:
        ids = ",".join(str(int(i)) for i in task_ids)
        sql(
            f"START TRANSACTION; DELETE FROM editing_task_log WHERE task_id IN ({ids}); DELETE FROM editing_task_asset WHERE task_id IN ({ids}); DELETE FROM editing_task WHERE id IN ({ids}); COMMIT;"
        )
    if asset_ids:
        ids = ",".join(str(int(i)) for i in asset_ids)
        rows = sql(
            f"SELECT f.id,f.storage_key FROM media_file f JOIN media_asset a ON a.file_id=f.id WHERE a.id IN ({ids})"
        )
        files = [row.split() for row in rows.splitlines() if row]
        file_ids = ",".join(str(int(row[0])) for row in files)
        sql(
            f"START TRANSACTION; DELETE FROM media_change_log WHERE asset_id IN ({ids}); DELETE FROM media_asset WHERE id IN ({ids}); "
            + (f"DELETE FROM media_file WHERE id IN ({file_ids});" if file_ids else "")
            + "COMMIT;"
        )
        directory = pathlib.Path(
            os.getenv("MEDIAFLOW_STORAGE_ROOT", config.get("STORAGE_ROOT", "uploads"))
        )
        if not directory.is_absolute():
            directory = ROOT / "backend" / directory
        for _, key in files:
            if not re.fullmatch(r"[a-f0-9-]{36}\.bin", key):
                raise ValueError("Invalid generated storage key")
            (directory / key).unlink(missing_ok=True)
    if user_ids:
        ids = ",".join(str(int(i)) for i in user_ids)
        sql(f"DELETE FROM sys_user WHERE id IN ({ids})")
