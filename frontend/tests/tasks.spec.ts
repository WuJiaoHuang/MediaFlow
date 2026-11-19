import { test, expect, type Page, type BrowserContext } from "@playwright/test";
import { readFileSync, unlinkSync, existsSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { randomBytes } from "node:crypto";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
const root = fileURLToPath(new URL("../../", import.meta.url));
const cfg = Object.fromEntries(
  readFileSync(resolve(root, ".env"), "utf8")
    .split(/\r?\n/)
    .filter((l) => /^[A-Z_]+=/.test(l))
    .map((l) => {
      const i = l.indexOf("=");
      return [l.slice(0, i), l.slice(i + 1)];
    }),
);
function seed(name: string) {
  for (const line of readFileSync(resolve(root, "README.md"), "utf8").split(
    /\r?\n/,
  )) {
    const p = line.split("|").map((s) => s.trim());
    if (p[1] === name) return { username: p[1]!, password: p[2]! };
  }
  throw new Error("Documented local seed missing");
}
function sql(query: string) {
  return execFileSync(
    "mysql",
    [
      "-u" + (cfg.DB_USERNAME || "root"),
      "mediaflow",
      "-N",
      "--default-character-set=utf8mb4",
      "-e",
      query,
    ],
    { env: { ...process.env, MYSQL_PWD: cfg.DB_PASSWORD }, encoding: "utf8" },
  ).trim();
}
async function login(
  page: Page,
  credentials: { username: string; password: string },
) {
  await page.goto("/login");
  await page
    .getByRole("textbox", { name: "用户名", exact: true })
    .fill(credentials.username);
  await page
    .getByRole("textbox", { name: "密码", exact: true })
    .fill(credentials.password);
  await page.getByRole("button", { name: "登录工作空间", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "工作台", exact: true }),
  ).toBeVisible();
}
async function header(page: Page) {
  return {
    Authorization:
      "Bearer " +
      (await page.evaluate(() =>
        sessionStorage.getItem("mediaflow.accessToken"),
      )),
  };
}
async function select(page: Page, label: string, text: string) {
  await page
    .locator(".el-select")
    .filter({ has: page.getByRole("combobox", { name: label, exact: true }) })
    .click();
  await page.getByRole("option", { name: text, exact: true }).click();
}
test("task UI lifecycle and reassignment revoke valid scoped preview without breaking media base policy", async ({
  page,
  browser,
  baseURL,
}) => {
  test.setTimeout(150000);
  page.setDefaultTimeout(10000);
  const prefix = "task_ui_" + Date.now();
  const taskIds: number[] = [];
  const assetIds: number[] = [];
  const users: number[] = [];
  const contexts: BrowserContext[] = [];
  try {
    await login(page, seed("admin"));
    const adminHeaders = await header(page);
    const info = async (username: string) => {
      const r = await page.request.get("/api/admin/users", {
        headers: adminHeaders,
        params: { keyword: username },
      });
      return (await r.json()).data.records.find(
        (u: { username: string }) => u.username === username,
      );
    };
    const executor = await info("editor01");
    const reviewer = await info("reviewer01");
    async function person(suffix: string, role: string) {
      const credentials = {
        username: prefix + "_" + suffix,
        password: randomBytes(18).toString("base64url"),
      };
      const displayName = "验收" + suffix;
      const r = await page.request.post("/api/admin/users", {
        headers: adminHeaders,
        data: { ...credentials, displayName, roles: [role] },
      });
      expect(r.status()).toBe(201);
      const user = (await r.json()).data;
      users.push(user.id);
      return { ...credentials, ...user };
    }
    const editorB = await person("editorB", "EDITOR");
    const reviewerB = await person("reviewerB", "REVIEWER");
    const picture = Buffer.from(
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a9XcAAAAASUVORK5CYII=",
      "base64",
    );
    async function asset(suffix: string) {
      const r = await page.request.post("/api/media/assets", {
        headers: {
          ...adminHeaders,
          "Idempotency-Key": randomBytes(16).toString("hex"),
        },
        multipart: {
          title: prefix + suffix,
          file: {
            name: "generated.png",
            mimeType: "image/png",
            buffer: picture,
          },
        },
      });
      expect(r.status()).toBe(201);
      const value = (await r.json()).data;
      assetIds.push(value.id);
      return value;
    }
    const imageA = await asset("_privateA");
    const imageB = await asset("_privateB");
    await page.goto("/media/" + imageA.id);
    await page
      .getByRole("button", { name: "设置访问范围", exact: true })
      .click();
    let d = page.getByRole("dialog");
    await d.getByText("受限", { exact: true }).click();
    await d.getByRole("button", { name: "保存访问范围", exact: true }).click();
    await expect(d).not.toBeVisible();
    await expect(page.getByText("受限素材", { exact: true })).toBeVisible();
    expect(
      (
        await page.request.put("/api/media/assets/" + imageB.id + "/scope", {
          headers: adminHeaders,
          data: { accessScope: "RESTRICTED", version: 0 },
        })
      ).status(),
    ).toBe(200);
    await page.goto("/tasks/new");
    await page
      .getByRole("textbox", { name: "任务标题", exact: true })
      .fill(prefix + "_A");
    await page
      .getByRole("textbox", { name: "任务要求", exact: true })
      .fill("剪辑开场与片尾，保留原始素材");
    await select(
      page,
      "指定执行人",
      executor.displayName + " (@" + executor.username + ")",
    );
    await select(
      page,
      "指定审核人",
      reviewer.displayName + " (@" + reviewer.username + ")",
    );
    await select(page, "优先级", "高");
    await select(
      page,
      "关联素材",
      "#" + imageA.id + " " + imageA.title + " · 受限",
    );
    await page.getByRole("heading", { name: "创建任务", exact: true }).click();
    const create = page.waitForResponse(
      (r) => r.url().endsWith("/api/tasks") && r.request().method() === "POST",
    );
    await page.getByRole("button", { name: "保存任务", exact: true }).click();
    const created = await create;
    expect(created.status()).toBe(201);
    const taskA = (await created.json()).data;
    taskIds.push(taskA.id);
    await expect(
      page.getByRole("heading", { name: prefix + "_A", exact: true }),
    ).toBeVisible();
    const rb = await page.request.post("/api/tasks", {
      headers: adminHeaders,
      data: {
        title: prefix + "_B",
        description: "另一执行人的独立任务",
        assigneeId: editorB.id,
        reviewerId: reviewerB.id,
        priority: "NORMAL",
        assetIds: [imageB.id],
      },
    });
    expect(rb.status()).toBe(201);
    const taskB = (await rb.json()).data;
    taskIds.push(taskB.id);
    async function session(credentials: {
      username: string;
      password: string;
    }) {
      const c = await browser.newContext({ baseURL });
      contexts.push(c);
      const p = await c.newPage();
      p.setDefaultTimeout(10000);
      await login(p, credentials);
      return p;
    }
    const a = await session(seed("editor01"));
    const b = await session(editorB);
    const ra = await session(seed("reviewer01"));
    const rpage = await session(reviewerB);
    await a.goto("/tasks");
    await a
      .getByRole("textbox", { name: "搜索任务标题", exact: true })
      .fill(prefix);
    await a.getByRole("button", { name: "筛选任务", exact: true }).click();
    await expect(
      a.getByRole("link", {
        name: "#" + taskA.id + " " + prefix + "_A",
        exact: true,
      }),
    ).toBeVisible();
    await expect(
      a.getByRole("link", {
        name: "#" + taskB.id + " " + prefix + "_B",
        exact: true,
      }),
    ).toHaveCount(0);
    await b.goto("/tasks/" + taskA.id);
    await expect(
      b.getByRole("heading", { name: "任务不存在或无权访问", exact: true }),
    ).toBeVisible();
    expect(
      (
        await b.request.post("/api/tasks/" + taskA.id + "/start", {
          headers: await header(b),
          data: { version: 0 },
        })
      ).status(),
    ).toBe(404);
    expect(
      (
        await rpage.request.get("/api/tasks/" + taskA.id, {
          headers: await header(rpage),
        })
      ).status(),
    ).toBe(404);
    await a.goto("/tasks/" + taskA.id);
    await a.getByRole("link", { name: "查看素材 / 下载", exact: true }).click();
    await expect
      .poll(() =>
        a
          .locator(".media-preview img")
          .evaluate((img: HTMLImageElement) => img.naturalWidth),
      )
      .toBe(1);
    const url = new URL(
      (await a.locator(".media-preview img").getAttribute("src")) as string,
      baseURL,
    );
    const contentPath = url.pathname + url.search;
    const cookie = (await a.context().cookies()).find(
      (c) => c.name === `mf_preview_${imageA.id}_task_${taskA.id}`,
    )!;
    expect(cookie.httpOnly).toBe(true);
    expect(cookie.expires).toBeGreaterThan(Date.now() / 1000);
    const range = await a.request.get(contentPath, {
      headers: { Range: "bytes=0-7" },
    });
    expect(range.status()).toBe(206);
    expect((await range.body()).length).toBe(8);
    await a.goto("/tasks/" + taskA.id);
    await a.getByRole("button", { name: "开始处理", exact: true }).click();
    await expect(
      a.getByRole("button", { name: "提交审核", exact: true }),
    ).toBeVisible();
    await a.getByRole("button", { name: "提交审核", exact: true }).click();
    await ra.goto("/tasks/" + taskA.id);
    await ra.getByRole("button", { name: "审核退回", exact: true }).click();
    d = ra.getByRole("dialog");
    await d.getByRole("button", { name: "确认退回", exact: true }).click();
    await expect(
      d
        .getByRole("alert")
        .or(d.getByText("审核退回必须填写原因", { exact: true })),
    ).toBeVisible();
    await d
      .getByRole("textbox", { name: "退回原因", exact: true })
      .fill("请缩短开场片段");
    await d.getByRole("button", { name: "确认退回", exact: true }).click();
    await expect(d).not.toBeVisible();
    await a.reload();
    await expect(
      a.getByRole("button", { name: "提交审核", exact: true }),
    ).toBeVisible();
    await a.getByRole("button", { name: "提交审核", exact: true }).click();
    await page.goto("/tasks/" + taskA.id);
    await page.getByRole("button", { name: "编辑任务", exact: true }).click();
    await select(
      page,
      "指定执行人",
      editorB.displayName + " (@" + editorB.username + ")",
    );
    await select(
      page,
      "指定审核人",
      reviewerB.displayName + " (@" + reviewerB.username + ")",
    );
    await page
      .getByRole("textbox", { name: "任务要求", exact: true })
      .fill("重新分配后的剪辑要求");
    await page.getByRole("button", { name: "保存任务", exact: true }).click();
    await expect(
      page.getByRole("heading", { name: prefix + "_A", exact: true }),
    ).toBeVisible();
    const ah = await header(a);
    expect(
      (await a.request.get("/api/tasks/" + taskA.id, { headers: ah })).status(),
    ).toBe(404);
    expect(
      (
        await a.request.get("/api/media/assets/" + imageA.id, { headers: ah })
      ).status(),
    ).toBe(404);
    expect(
      (
        await a.request.get("/api/media/changes", {
          headers: ah,
          params: { assetId: imageA.id },
        })
      ).status(),
    ).toBe(404);
    expect(
      (
        await a.request.post("/api/media/assets/" + imageA.id + "/access", {
          headers: ah,
          params: { taskId: taskA.id },
        })
      ).status(),
    ).toBe(404);
    const currentCookie = (await a.context().cookies()).find(
      (c) => c.name === cookie.name,
    )!;
    expect(currentCookie.value).toBe(cookie.value);
    expect(currentCookie.expires).toBeGreaterThan(Date.now() / 1000);
    expect((await a.request.get(contentPath)).status()).toBe(404);
    const stats = await a.request.get("/api/media/stats", {
      headers: ah,
      params: { keyword: prefix },
    });
    expect((await stats.json()).data.total).toBe(0);
    await a.goto("/tasks/" + taskA.id);
    await expect(
      a.getByRole("heading", { name: "任务不存在或无权访问", exact: true }),
    ).toBeVisible();
    await b.goto("/tasks/" + taskA.id);
    await expect(
      b.getByRole("heading", { name: prefix + "_A", exact: true }),
    ).toBeVisible();
    await expect(
      b.getByRole("button", { name: "审核通过", exact: true }),
    ).toHaveCount(0);
    await rpage.goto("/tasks/" + taskA.id);
    await rpage.getByRole("button", { name: "审核通过", exact: true }).click();
    await expect(
      rpage.getByText("任务已完成，不能继续修改或重开。", { exact: true }),
    ).toBeVisible();
    await expect(
      rpage.getByText("退回原因：请缩短开场片段", { exact: true }),
    ).toBeVisible();
    await page.goto("/tasks/" + taskA.id);
    await expect(
      page.getByRole("button", { name: "编辑任务", exact: true }),
    ).toHaveCount(0);
    await expect(page.getByText("退回原因：请缩短开场片段", {exact:true})).toBeVisible();
    await page.screenshot({
      path: "../.local/stage3-task-completed.png",
      fullPage: true,
    });
    const detail = await page.request.get("/api/tasks/" + taskA.id, {
      headers: adminHeaders,
    });
    const completed = (await detail.json()).data;
    expect(completed.status).toBe("COMPLETED");
    expect(
      (
        await page.request.put("/api/tasks/" + taskA.id, {
          headers: adminHeaders,
          data: { ...completed, assetIds: [imageA.id] },
        })
      ).status(),
    ).toBe(409);
    const history = await page.request.get("/api/tasks/" + taskA.id + "/logs", {
      headers: adminHeaders,
    });
    const rows = (await history.json()).data.records;
    expect(
      rows.find((l: { operationType: string }) => l.operationType === "START")
        .operatorId,
    ).toBe(executor.id);
    expect(
      rows.some(
        (l: { operationType: string }) =>
          l.operationType === "ASSIGNEE_CHANGED",
      ),
    ).toBe(true);
    expect(
      rows.some((l: { reason: string }) => l.reason === "请缩短开场片段"),
    ).toBe(true);
  } finally {
    await Promise.all(contexts.map((c) => c.close()));
    if (taskIds.length) {
      const ids = taskIds.map(Number).join(",");
      sql(
        `START TRANSACTION; DELETE FROM editing_task_log WHERE task_id IN (${ids}); DELETE FROM editing_task_asset WHERE task_id IN (${ids}); DELETE FROM editing_task WHERE id IN (${ids}); COMMIT;`,
      );
    }
    if (assetIds.length) {
      const ids = assetIds.map(Number).join(",");
      const rows = sql(
        `SELECT f.id,f.storage_key FROM media_file f JOIN media_asset a ON a.file_id=f.id WHERE a.id IN (${ids})`,
      )
        .split(/\r?\n/)
        .filter(Boolean)
        .map((l) => l.split(/\s+/));
      const fileIds = rows.map((r) => Number(r[0])).join(",");
      sql(
        `START TRANSACTION; DELETE FROM media_change_log WHERE asset_id IN (${ids}); DELETE FROM media_asset WHERE id IN (${ids}); ${fileIds ? "DELETE FROM media_file WHERE id IN (" + fileIds + ");" : ""} COMMIT;`,
      );
      const directory =
        process.env.MEDIAFLOW_STORAGE_ROOT ||
        resolve(root, "backend", cfg.STORAGE_ROOT || "uploads");
      for (const row of rows) {
        const key = row[1]!;
        if (!/^[a-f0-9-]{36}\.bin$/.test(key))
          throw new Error("Invalid cleanup key");
        const path = resolve(directory, key);
        if (existsSync(path)) unlinkSync(path);
      }
    }
    if (users.length)
      sql(`DELETE FROM sys_user WHERE id IN (${users.map(Number).join(",")})`);
  }
});
