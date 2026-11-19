"""Local real-MySQL HTTP checks. Set MEDIAFLOW_API and MEDIAFLOW_STORAGE_ROOT for custom servers.
Creates one unique temporary media asset, checks rollback / limits, and cleans only that asset.
Requires Python 3, mysql CLI and the root .env; never run against production.
"""

import urllib.request, urllib.error, urllib.parse, json, uuid, base64, http.client, os, pathlib, subprocess, re

ROOT = pathlib.Path(__file__).resolve().parents[1]
base = os.getenv("MEDIAFLOW_API", "http://127.0.0.1:8080")
checks = []
asset = None
config = {
    l.split("=", 1)[0]: l.split("=", 1)[1]
    for l in (ROOT / ".env").read_text(encoding="utf-8-sig").splitlines()
    if "=" in l and not l.startswith("#")
}


def call(method, path, expected=200, data=None, token=None, headers=None):
    h = {"Content-Type": "application/json"}
    h.update(headers or {})
    if token:
        h["Authorization"] = "Bearer " + token
    payload = json.dumps(data).encode() if data is not None else None
    try:
        r = urllib.request.urlopen(
            urllib.request.Request(base + path, method=method, data=payload, headers=h)
        )
        status = r.status
        d = json.load(r)
    except urllib.error.HTTPError as e:
        status = e.code
        d = json.load(e)
    assert status == expected, (path, status, expected)
    checks.append((method, path, status))
    return d["data"]


def upload(
    token, key, name="generated.png", mime="image/png", content=None, expected=201
):
    content = (
        content
        if content is not None
        else base64.b64decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a9XcAAAAASUVORK5CYII="
        )
    )
    boundary = "mf" + uuid.uuid4().hex
    body = (
        f'--{boundary}\r\nContent-Disposition: form-data; name="title"\r\n\r\nedge_{key}\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{name}"\r\nContent-Type: {mime}\r\n\r\n'.encode()
        + content
        + f"\r\n--{boundary}--\r\n".encode()
    )
    try:
        r = urllib.request.urlopen(
            urllib.request.Request(
                base + "/api/media/assets",
                data=body,
                headers={
                    "Authorization": "Bearer " + token,
                    "Idempotency-Key": key,
                    "Content-Type": "multipart/form-data; boundary=" + boundary,
                },
            )
        )
        status = r.status
        d = json.load(r)
    except urllib.error.HTTPError as e:
        status = e.code
        d = json.load(e)
    assert status == expected, (name, status, expected)
    checks.append(("upload", name, status))
    return d["data"]


try:
    admin = call(
        "POST",
        "/api/auth/login",
        data={"username": "admin", "password": "Admin@123456"},
    )["accessToken"]
    key = uuid.uuid4().hex
    asset = upload(admin, key)
    aid = asset["id"]
    initial = call("GET", f"/api/media/changes?assetId={aid}", token=admin)["total"]
    upload(admin, key, expected=409)
    upload(admin, uuid.uuid4().hex, name="../escape.png", expected=400)
    upload(admin, uuid.uuid4().hex, content=b"<script>not image</script>", expected=415)
    call(
        "PUT",
        f"/api/media/assets/{aid}",
        400,
        {
            "title": "must rollback",
            "description": "changed",
            "categoryId": 99999999,
            "version": 0,
        },
        admin,
    )
    assert (
        call("GET", f"/api/media/assets/{aid}", token=admin)["title"] == asset["title"]
    )
    assert (
        call("GET", f"/api/media/changes?assetId={aid}", token=admin)["total"]
        == initial
    )
    limit_text = os.getenv(
        "MAX_REQUEST_SIZE", config.get("MAX_REQUEST_SIZE", "101MB")
    ).upper()
    match = re.fullmatch(r"(\d+)(B|KB|MB|GB)?", limit_text)
    if not match:
        raise ValueError("Use an integer B/KB/MB/GB MAX_REQUEST_SIZE")
    request_limit = (
        int(match.group(1))
        * {"B": 1, "KB": 1024, "MB": 1048576, "GB": 1073741824, None: 1}[match.group(2)]
    )
    conn = http.client.HTTPConnection(
        urllib.parse.urlparse(base).hostname,
        urllib.parse.urlparse(base).port or 80,
        timeout=15,
    )
    conn.request(
        "POST",
        "/api/media/assets",
        body=b"--test\r\n",
        headers={
            "Authorization": "Bearer " + admin,
            "Idempotency-Key": uuid.uuid4().hex,
            "Content-Type": "multipart/form-data; boundary=test",
            "Content-Length": str(request_limit + 1),
        },
    )
    response = conn.getresponse()
    assert response.status == 413, response.status
    response.read()
    conn.close()
    checks.append(("upload", "declared oversize", 413))
    call("GET", "/api/media/assets?sort=invalid", 400, token=admin)
    call(
        "GET",
        "/api/media/stats?from=2030-01-01T00:00:00&to=2020-01-01T00:00:00",
        400,
        token=admin,
    )
    print("PASS:", len(checks), "real MySQL media edge checks")
    (ROOT / ".local").mkdir(exist_ok=True)
    (ROOT / ".local/stage2-edge-results.json").write_text(
        json.dumps({"passed": len(checks), "checks": checks}, indent=2),
        encoding="utf-8",
    )
finally:
    if asset:
        env = os.environ.copy()
        env["MYSQL_PWD"] = config["DB_PASSWORD"]
        ids = str(int(asset["id"]))
        row = subprocess.check_output(
            [
                "mysql",
                "-u" + config.get("DB_USERNAME", "root"),
                "mediaflow",
                "-N",
                "-e",
                f"SELECT f.id,f.storage_key FROM media_file f JOIN media_asset a ON a.file_id=f.id WHERE a.id={ids}",
            ],
            env=env,
            text=True,
        ).split()
        subprocess.run(
            [
                "mysql",
                "-u" + config.get("DB_USERNAME", "root"),
                "mediaflow",
                "-e",
                f"START TRANSACTION; DELETE FROM media_change_log WHERE asset_id={ids}; DELETE FROM media_asset WHERE id={ids}; DELETE FROM media_file WHERE id={int(row[0])}; COMMIT;",
            ],
            env=env,
            check=True,
        )
        storage = pathlib.Path(
            os.getenv("MEDIAFLOW_STORAGE_ROOT", config.get("STORAGE_ROOT", "uploads"))
        )
        if not storage.is_absolute():
            storage = ROOT / "backend" / storage
        if not re.fullmatch(r"[a-f0-9-]{36}\.bin", row[1]):
            raise ValueError("Invalid cleanup key")
        (storage / row[1]).unlink(missing_ok=True)
