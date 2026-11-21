import { test, expect, type Page, type BrowserContext } from "@playwright/test";
import {
  readFileSync,
  unlinkSync,
  existsSync,
  writeFileSync,
  mkdirSync,
} from "node:fs";
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

async function feedbackButton(page: Page, name: string, op: string) {
  const [response] = await Promise.all([
    page.waitForResponse(
      (r) => r.request().method() === "POST" && r.url().endsWith("/" + op),
    ),
    page.getByRole("button", { name, exact: true }).click(),
  ]);
  expect(response.status()).toBe(200);
}
test("actual Chromium microphone and camera MediaRecorder produce playable audio/video WebM", async ({
  browser,
  baseURL,
}) => {
  const context = await browser.newContext({
    permissions: ["microphone", "camera"],
  });
  const page = await context.newPage();
  try {
    await page.goto(baseURL! + "/login");
    mkdirSync(resolve(root, ".local"), { recursive: true });
    for (const kind of ["audio", "video"]) {
      const result = await page.evaluate(async (kind) => {
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: true,
          video: kind === "video",
        });
        const type =
          kind === "audio"
            ? "audio/webm;codecs=opus"
            : "video/webm;codecs=vp8,opus";
        const recorder = new MediaRecorder(stream, { mimeType: type });
        const chunks: BlobPart[] = [];
        return await new Promise<{ bytes: number[]; mime: string }>(
          (resolve) => {
            recorder.ondataavailable = (e) => chunks.push(e.data);
            recorder.onstop = async () => {
              stream.getTracks().forEach((t) => t.stop());
              const blob = new Blob(chunks, { type: type.split(";")[0] });
              resolve({
                bytes: Array.from(new Uint8Array(await blob.arrayBuffer())),
                mime: blob.type,
              });
            };
            recorder.start(200);
            setTimeout(() => recorder.stop(), 1200);
          },
        );
      }, kind);
      expect(result.bytes.length).toBeGreaterThan(100);
      writeFileSync(
        resolve(root, `.local/feedback-recorded-${kind}.webm`),
        Buffer.from(result.bytes),
      );
      const playable = await page.evaluate(async (value) => {
        const url = URL.createObjectURL(
          new Blob([new Uint8Array(value.bytes)], { type: value.mime }),
        );
        const media = document.createElement(
          value.mime.startsWith("audio") ? "audio" : "video",
        );
        return await new Promise<boolean>((resolve) => {
          media.onloadeddata = () => {
            URL.revokeObjectURL(url);
            resolve(true);
          };
          media.onerror = () => {
            URL.revokeObjectURL(url);
            resolve(false);
          };
          media.src = url;
        });
      }, result);
      expect(playable).toBe(true);
    }
  } finally {
    await context.close();
  }
});

test("feedback UI: text and real recording, replies, resolution/reopen, gated review, readonly completion", async ({
  page,
  browser,
}) => {
  test.setTimeout(180000);
  page.setDefaultTimeout(12000);
  const taskIds: number[] = [],
    assetIds: number[] = [],
    contexts: BrowserContext[] = [];
  let taskId = 0;
  try {
    await login(page, seed("admin"));
    const admin = await header(page);
    const person = async (name: string) => {
      const response = await page.request.get("/api/admin/users", {
        headers: admin,
        params: { keyword: name },
      });
      return (await response.json()).data.records.find(
        (u: any) => u.username === name,
      );
    };
    const editor = await person("editor01"),
      reviewer = await person("reviewer01");
    const response = await page.request.post("/api/media/assets", {
      headers: { ...admin, "Idempotency-Key": randomBytes(16).toString("hex") },
      multipart: {
        title: "feedback_ui_" + Date.now(),
        file: {
          name: "fixture.png",
          mimeType: "image/png",
          buffer: Buffer.from(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a9XcAAAAASUVORK5CYII=",
            "base64",
          ),
        },
      },
    });
    expect(response.status()).toBe(201);
    const asset = (await response.json()).data;
    assetIds.push(asset.id);
    const t = await page.request.post("/api/tasks", {
      headers: admin,
      data: {
        title: "协作反馈验收 " + Date.now(),
        description: "反馈闭环",
        assigneeId: editor.id,
        reviewerId: reviewer.id,
        priority: "NORMAL",
        assetIds: [asset.id],
      },
    });
    expect(t.status()).toBe(201);
    taskId = (await t.json()).data.id;
    taskIds.push(taskId);
    expect(
      (
        await page.request.post(`/api/tasks/${taskId}/start`, {
          headers: admin,
          data: { version: 0 },
        })
      ).status(),
    ).toBe(200);
    const rc = await browser.newContext({
        permissions: ["microphone", "camera"],
      }),
      ec = await browser.newContext();
    contexts.push(rc, ec);
    const rp = await rc.newPage(),
      ep = await ec.newPage();
    rp.setDefaultTimeout(12000);
    ep.setDefaultTimeout(12000);
    await login(rp, seed("reviewer01"));
    await login(ep, seed("editor01"));
    await rp.goto(`/tasks/${taskId}`);
    await rp.getByRole("button", { name: "提出反馈", exact: true }).click();
    let dialog = rp.getByRole("dialog");
    await dialog
      .getByRole("textbox", { name: "反馈内容", exact: true })
      .fill("<img src=x onerror=alert(1)> 请调整片头");
    await dialog.getByRole("button", { name: "提交反馈", exact: true }).click();
    await expect(dialog).not.toBeVisible();
    await expect(
      rp.getByRole("link", {
        name: "<img src=x onerror=alert(1)> 请调整片头",
        exact: true,
      }),
    ).toBeVisible();
    for (const kind of ["AUDIO", "VIDEO"]) {
      await rp.getByRole("button", { name: "提出反馈", exact: true }).click();
      dialog = rp.getByRole("dialog");
      await select(rp, "反馈类型", kind === "AUDIO" ? "语音" : "视频");
      await dialog
        .getByRole("textbox", { name: "反馈内容", exact: true })
        .fill(kind === "AUDIO" ? "真实麦克风反馈" : "真实摄像头反馈");
      await dialog
        .getByRole("button", {
          name: kind === "AUDIO" ? "开始录音" : "开始录像",
          exact: true,
        })
        .click();
      await expect(
        dialog.getByRole("button", { name: /停止录制/ }),
      ).toBeVisible();
      await rp.waitForTimeout(1300);
      await dialog.getByRole("button", { name: /停止录制/ }).click();
      await expect(dialog.getByText(/已选择：feedback-/)).toBeVisible();
      await dialog
        .getByRole("button", { name: "提交反馈", exact: true })
        .click();
      await expect(dialog).not.toBeVisible();
    }
    // Deterministic denial/error handling; positive recording above uses the real MediaRecorder.
    await rp.getByRole("button", { name: "提出反馈", exact: true }).click();
    dialog = rp.getByRole("dialog");
    await select(rp, "反馈类型", "语音");
    await rp.evaluate(() => {
      const devices = navigator.mediaDevices;
      const original = devices.getUserMedia.bind(devices);
      (window as any).__restoreMic = () => {
        devices.getUserMedia = original;
      };
      devices.getUserMedia = async () => {
        throw new DOMException("denied for test", "NotAllowedError");
      };
    });
    await dialog.getByRole("button", { name: "开始录音", exact: true }).click();
    await expect(dialog.getByRole("alert")).toContainText("权限被拒绝");
    await rp.evaluate(() => {
      (window as any).__restoreMic();
    });
    await dialog.locator(".el-dialog__headerbtn").click();
    // Unsupported browser path: fallback remains operational, with a real local WAV upload.
    await rp.addInitScript(() => {
      Object.defineProperty(window, "MediaRecorder", {
        value: undefined,
        configurable: true,
      });
    });
    await rp.reload();
    await rp.getByRole("button", { name: "提出反馈", exact: true }).click();
    dialog = rp.getByRole("dialog");
    await select(rp, "反馈类型", "语音");
    await dialog.getByRole("button", { name: "开始录音", exact: true }).click();
    await expect(dialog.getByRole("alert")).toContainText("浏览器不支持录制");
    await dialog
      .getByRole("textbox", { name: "反馈内容", exact: true })
      .fill("文件上传备选");
    await dialog.getByLabel("反馈附件", { exact: true }).setInputFiles({
      name: "fallback.wav",
      mimeType: "audio/wav",
      buffer: Buffer.from(
        "524946462800000057415645666d74201000000001000100401f0000803e000002001000646174610400000000000000",
        "hex",
      ),
    });
    await rp.route(
      `**/api/tasks/${taskId}/feedback`,
      async (route) => {
        if (route.request().method() === "POST")
          await route.abort("connectionreset");
        else await route.continue();
      },
      { times: 1 },
    );
    await dialog.getByRole("button", { name: "提交反馈", exact: true }).click();
    await expect(dialog.getByRole("alert")).toContainText("请求失败");
    await dialog.getByRole("button", { name: "提交反馈", exact: true }).click();
    await expect(dialog).not.toBeVisible();
    await ep.goto(`/tasks/${taskId}`);
    await ep.getByRole("button", { name: "提交审核", exact: true }).click();
    await expect(
      ep.getByRole("alert").filter({ hasText: "未解决反馈" }).first(),
    ).toContainText("4");
    const eh = await header(ep);
    const list = await ep.request.get(`/api/tasks/${taskId}/feedback`, {
      headers: eh,
    });
    const feedbacks = (await list.json()).data.records;
    const text = feedbacks.find((f: any) => f.type === "TEXT");
    await ep.goto(`/tasks/${taskId}/feedback/${text.id}`);
    await ep
      .getByRole("textbox", { name: "回复内容", exact: true })
      .fill("已经修改，麻烦复核");
    await ep.getByRole("button", { name: "发送回复", exact: true }).click();
    await expect(
      ep.getByText("已经修改，麻烦复核", { exact: true }),
    ).toBeVisible();
    await feedbackButton(ep, "标记已处理", "resolve");
    await expect(
      ep.getByText("已处理待确认", { exact: true }).first(),
    ).toBeVisible();
    await rp.goto(`/tasks/${taskId}/feedback/${text.id}`);
    await feedbackButton(rp, "确认解决", "close");
    await rp.getByRole("button", { name: "重新打开", exact: true }).click();
    dialog = rp.getByRole("dialog");
    await dialog
      .getByRole("textbox", { name: "重新打开原因", exact: true })
      .fill("还需调整片尾");
    await dialog
      .getByRole("button", { name: "确认重新打开", exact: true })
      .click();
    await expect(dialog).not.toBeVisible();
    await expect(
      rp.getByText("重新打开", { exact: true }).first(),
    ).toBeVisible();
    for (const f of feedbacks) {
      await ep.goto(`/tasks/${taskId}/feedback/${f.id}`);
      if (f.type === "AUDIO" || f.type === "VIDEO") {
        const el = ep.locator(f.type === "AUDIO" ? "audio" : "video");
        await expect(el).toBeVisible();
        await expect
          .poll(() => el.evaluate((m: any) => m.readyState))
          .toBeGreaterThan(0);
        expect(
          await ep
            .getByRole("link", { name: "下载反馈附件", exact: true })
            .getAttribute("href"),
        ).toContain("/attachments/");
      }
      await feedbackButton(ep, "标记已处理", "resolve");
      await expect(
        ep.getByText("已处理待确认", { exact: true }).first(),
      ).toBeVisible();
    }
    await ep.goto(`/tasks/${taskId}`);
    await ep.getByRole("button", { name: "提交审核", exact: true }).click();
    await expect(ep.getByText("待审核", { exact: true }).first()).toBeVisible();
    await rp.goto(`/tasks/${taskId}`);
    await rp.getByRole("button", { name: "审核通过", exact: true }).click();
    await expect(
      rp.getByRole("alert").filter({ hasText: "尚未确认解决" }).first(),
    ).toContainText("4");
    for (const f of feedbacks) {
      await rp.goto(`/tasks/${taskId}/feedback/${f.id}`);
      await feedbackButton(rp, "确认解决", "close");
      await expect(
        rp.getByText("已确认解决", { exact: true }).first(),
      ).toBeVisible();
    }
    await rp.goto(`/tasks/${taskId}`);
    await rp.getByRole("button", { name: "审核通过", exact: true }).click();
    await expect(
      rp.getByText("任务已完成，不能继续修改或重开。", { exact: true }),
    ).toBeVisible();
    await rp.reload();
    await expect(
      rp.getByRole("button", { name: "提出反馈", exact: true }),
    ).toHaveCount(0);
    await ep.goto(`/tasks/${taskId}/feedback/${text.id}`);
    await expect(
      ep.getByText("当前任务阶段反馈只读。", { exact: true }),
    ).toBeVisible();
    await expect(
      ep.getByRole("textbox", { name: "回复内容", exact: true }),
    ).toHaveCount(0);
    await expect(
      ep.getByText("已经修改，麻烦复核", { exact: true }),
    ).toBeVisible();
  } finally {
    for (const c of contexts) await c.close().catch(() => {});
    if (taskIds.length) {
      const ids = taskIds.join(",");
      const keys = sql(
        `SELECT storage_key FROM task_feedback_attachment WHERE feedback_id IN (SELECT id FROM task_feedback WHERE task_id IN (${ids}))`,
      )
        .split(/\r?\n/)
        .filter(Boolean);
      sql(
        `START TRANSACTION; DELETE FROM task_feedback_log WHERE feedback_id IN (SELECT id FROM task_feedback WHERE task_id IN (${ids})); DELETE FROM task_feedback_reply WHERE feedback_id IN (SELECT id FROM task_feedback WHERE task_id IN (${ids})); DELETE FROM task_feedback_attachment WHERE feedback_id IN (SELECT id FROM task_feedback WHERE task_id IN (${ids})); DELETE FROM task_feedback WHERE task_id IN (${ids}); DELETE FROM editing_task_log WHERE task_id IN (${ids}); DELETE FROM editing_task_asset WHERE task_id IN (${ids}); DELETE FROM editing_task WHERE id IN (${ids}); COMMIT;`,
      );
      for (const key of keys) {
        if (!/^[a-f0-9-]{36}\.bin$/.test(key)) throw new Error("Invalid key");
        const file = resolve(
          process.env.MEDIAFLOW_STORAGE_ROOT ||
            resolve(root, ".local/media-storage"),
          key,
        );
        if (existsSync(file)) unlinkSync(file);
      }
    }
    if (assetIds.length) {
      const ids = assetIds.join(",");
      const rows = sql(
        `SELECT f.id,f.storage_key FROM media_file f JOIN media_asset a ON a.file_id=f.id WHERE a.id IN (${ids})`,
      )
        .split(/\r?\n/)
        .filter(Boolean)
        .map((l) => l.split(/\s+/));
      sql(
        `START TRANSACTION; DELETE FROM media_change_log WHERE asset_id IN (${ids}); DELETE FROM media_asset WHERE id IN (${ids}); DELETE FROM media_file WHERE id IN (${rows.map((r) => r[0]).join(",")}); COMMIT;`,
      );
      for (const row of rows) {
        const key = row[1]!;
        if (!/^[a-f0-9-]{36}\.bin$/.test(key)) throw new Error("Invalid key");
        const file = resolve(
          process.env.MEDIAFLOW_STORAGE_ROOT ||
            resolve(root, ".local/media-storage"),
          key,
        );
        if (existsSync(file)) unlinkSync(file);
      }
    }
  }
});
