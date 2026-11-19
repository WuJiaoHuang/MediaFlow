"""Local Stage 3 real HTTP/MySQL acceptance. Uses documented development seeds or env overrides.
Creates unique temporary users/tasks/assets, removes only returned IDs, never resets existing tables.
Run with MEDIAFLOW_API and MEDIAFLOW_STORAGE_ROOT matching the local backend. Not for production.
"""

import base64, json, os, pathlib, re, secrets, subprocess, urllib.request, urllib.error, urllib.parse, http.cookiejar, concurrent.futures

ROOT = pathlib.Path(__file__).resolve().parents[1]
BASE = os.getenv("MEDIAFLOW_API", "http://127.0.0.1:8080")
PREFIX = "task_accept_" + secrets.token_hex(5)
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


try:
    admin = existing("admin", "MEDIAFLOW_ADMIN")
    editor = existing("editor01", "MEDIAFLOW_EDITOR")
    reviewer = existing("reviewer01", "MEDIAFLOW_REVIEWER")
    aid = editor.call("GET", "/api/auth/me")["id"]
    rid = reviewer.call("GET", "/api/auth/me")["id"]
    bid, other = create_user(admin, "editor_b", ["EDITOR"])
    sid, other_reviewer = create_user(admin, "reviewer_b", ["REVIEWER"])
    did, dual = create_user(admin, "dual", ["EDITOR", "REVIEWER"])
    private = upload(admin, "private")
    shared = upload(admin, "shared", False)
    pid = private["id"]
    pub = shared["id"]
    ta = create(admin, request(aid, rid, pid, PREFIX + "_A"))
    tb = create(admin, request(bid, sid, pid, PREFIX + "_B"))
    assert editor.call("GET", "/api/tasks?" + query(PREFIX))["total"] == 1
    assert other.call("GET", "/api/tasks?" + query(PREFIX))["total"] == 1
    for suffix in ["", "/assets", "/logs"]:
        other.call("GET", f"/api/tasks/{ta['id']}" + suffix, 404)
        other_reviewer.call("GET", f"/api/tasks/{ta['id']}" + suffix, 404)
    other.call("POST", f"/api/tasks/{ta['id']}/start", 404, {"version": 0})
    reviewer.call("POST", f"/api/tasks/{ta['id']}/start", 403, {"version": 0})
    editor.call("PUT", f"/api/tasks/{ta['id']}", 403, request(bid, sid, pid, version=0))
    editor.call("POST", "/api/tasks", 403, request(aid, rid, pid))
    editor.call("POST", f"/api/media/assets/{pid}/access")
    access = editor.call("POST", f"/api/media/assets/{pid}/access?taskId={ta['id']}")
    data = editor.call(
        "GET",
        access["contentUrl"],
        206,
        authenticated=False,
        headers={"Range": "bytes=0-7"},
    )
    assert len(data) == 8
    editor.call("GET", f"/api/media/assets/{pub}?taskId={ta['id']}", 404)
    ta = action(editor, ta, "start")
    ta = action(editor, ta, "submit")
    action(reviewer, ta, "reject", 400, " ")
    ta = action(reviewer, ta, "reject", reason="Opening needs to be shorter")
    ta = action(editor, ta, "submit")
    revision = request(bid, sid, pid, PREFIX + "_A_reassigned", ta["version"])
    revision["priority"] = "HIGH"
    revision["dueAt"] = "2027-01-01T12:00:00"
    revision["description"] = "Updated task requirements"
    ta = admin.call("PUT", f"/api/tasks/{ta['id']}", data=revision)
    revision["dueAt"] = None
    revision["version"] = ta["version"]
    ta = admin.call("PUT", f"/api/tasks/{ta['id']}", data=revision)
    assert ta["dueAt"] is None
    for client in [editor, reviewer]:
        for suffix in ["", "/assets", "/logs"]:
            client.call("GET", f"/api/tasks/{ta['id']}" + suffix, 404)
        client.call("GET", f"/api/media/assets/{pid}", 404)
        assert (
            client.call("GET", "/api/media/assets?" + query(PREFIX + "_private"))[
                "total"
            ]
            == 0
        )
        assert (
            client.call("GET", "/api/media/stats?" + query(PREFIX + "_private"))[
                "total"
            ]
            == 0
        )
    editor.call("GET", "/api/media/changes?assetId=" + str(pid), 404)
    editor.call("POST", f"/api/media/assets/{pid}/access", 404)
    editor.call("POST", f"/api/media/assets/{pid}/access?taskId={ta['id']}", 404)
    editor.call("GET", access["contentUrl"], 404, authenticated=False)
    editor.call("GET", f"/api/media/assets/{pid}/download", 404, authenticated=False)
    old = editor.call(
        "GET", f"/api/media/assets/{pid}/content", 404, authenticated=False
    )
    assert other.call("GET", "/api/media/assets/" + str(pid))["id"] == pid
    ta = action(other_reviewer, ta, "approve")
    assert ta["status"] == "COMPLETED"
    action(admin, ta, "reject", 409, "Cannot reopen")
    admin.call(
        "PUT",
        f"/api/tasks/{ta['id']}",
        409,
        request(bid, sid, pid, version=ta["version"]),
    )
    logs = other.call("GET", f"/api/tasks/{ta['id']}/logs")["records"]
    start = next(l for l in logs if l["operationType"] == "START")
    assert start["operatorId"] == aid
    assert any(l["reason"] == "Opening needs to be shorter" for l in logs)
    assert any(
        l["operationType"] == "ASSIGNEE_CHANGED"
        and l["oldValue"] == str(aid)
        and l["newValue"] == str(bid)
        for l in logs
    )
    # Real MySQL row locks and version guard: two independent clients race the same transition.
    race_a = Client()
    race_a.token = other.token
    race_b = Client()
    race_b.token = other.token

    def race(c):
        req = urllib.request.Request(
            BASE + f"/api/tasks/{tb['id']}/start",
            data=json.dumps({"version": tb["version"]}).encode(),
            headers={
                "Authorization": "Bearer " + c.token,
                "Content-Type": "application/json",
            },
        )
        try:
            r = c.opener.open(req, timeout=20)
        except urllib.error.HTTPError as e:
            r = e
        status = r.status
        r.read()
        return status

    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        outcomes = sorted(pool.map(race, [race_a, race_b]))
    assert outcomes == [200, 409], outcomes
    checks.append({"case": "concurrent MySQL same-version start", "statuses": outcomes})
    tb = other.call("GET", f"/api/tasks/{tb['id']}")
    tb = action(other, tb, "submit")
    tb = action(other_reviewer, tb, "approve")
    assert tb["status"] == "COMPLETED"
    page = other.call(
        "GET",
        "/api/tasks?"
        + urllib.parse.urlencode(
            {
                "keyword": PREFIX,
                "status": "COMPLETED",
                "assigneeId": bid,
                "page": 1,
                "size": 1,
                "sort": "dueAt",
                "direction": "asc",
            }
        ),
    )
    assert page["total"] == 2 and len(page["records"]) == 1
    filtered = admin.call(
        "GET",
        "/api/tasks?"
        + urllib.parse.urlencode(
            {
                "keyword": PREFIX,
                "status": "COMPLETED",
                "priority": "HIGH",
                "reviewerId": sid,
            }
        ),
    )
    assert filtered["total"] == 1
    # Shared base access is intentionally independent; a revoked task cookie still fails.
    ts = create(admin, request(aid, rid, pub, PREFIX + "_shared"))
    public_access = editor.call(
        "POST", f"/api/media/assets/{pub}/access?taskId={ts['id']}"
    )
    admin.call(
        "PUT",
        f"/api/tasks/{ts['id']}",
        data=request(bid, rid, pub, PREFIX + "_shared", ts["version"]),
    )
    assert editor.call("GET", "/api/media/assets/" + str(pub))["id"] == pub
    editor.call("GET", public_access["contentUrl"], 404, authenticated=False)
    multiple = request(did, rid, pid, PREFIX + "_dual_exec")
    multiple["assetIds"] = [pid, pub]
    td = create(admin, multiple)
    assert len(dual.call("GET", f"/api/tasks/{td['id']}/assets")) == 2
    te = create(admin, request(bid, did, pid, PREFIX + "_dual_review"))
    tf = create(admin, request(did, did, pid, PREFIX + "_dual_both"))
    assert dual.call("GET", "/api/tasks?" + query(PREFIX + "_dual"))["total"] == 3
    action(dual, te, "start", 403)
    assert (
        dual.call("GET", "/api/media/assets?" + query(PREFIX + "_private"))["total"]
        == 1
    )
    d_access = dual.call("POST", f"/api/media/assets/{pid}/access?taskId={td['id']}")
    admin.call("PUT", f"/api/admin/users/{did}/roles", data={"roles": ["REVIEWER"]})
    dual.call("GET", f"/api/tasks/{td['id']}", 404)
    dual.call("GET", d_access["contentUrl"], 404, authenticated=False)
    assert dual.call("GET", f"/api/tasks/{te['id']}")["id"] == te["id"]
    bad = request(bid, sid, pid)
    bad["assetIds"] = [pid, pid]
    admin.call("POST", "/api/tasks", 400, bad)
    bad = request(bid, aid, pid)
    admin.call("POST", "/api/tasks", 400, bad)
    bad = request(bid, sid, pid)
    bad["title"] = " "
    admin.call("POST", "/api/tasks", 400, bad)
    admin.call(
        "PUT",
        f"/api/admin/users/{bid}",
        data={"displayName": "disabled fixture", "status": 0},
    )
    admin.call("POST", "/api/tasks", 400, request(bid, sid, pid))
    admin.call(
        "PUT", f"/api/admin/users/{bid}", data={"displayName": "editor_b", "status": 1}
    )
    archived = upload(admin, "archived")
    admin.call("POST", f"/api/media/assets/{archived['id']}/archive")
    admin.call("POST", "/api/tasks", 400, request(bid, sid, archived["id"]))
    anonymous = Client()
    anonymous.call("GET", "/api/tasks", 401)
    anonymous.call("GET", f"/api/media/assets/{pid}/content", 401)
    result = {"passed": len(checks), "checks": checks}
    (ROOT / ".local").mkdir(exist_ok=True)
    (ROOT / ".local/stage3-http-results.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(
        "PASS:",
        len(checks),
        "real task HTTP/MySQL checks, including concurrent transitions",
    )
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
