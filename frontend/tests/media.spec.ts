import { test, expect, type Page } from "@playwright/test";
import { readFileSync, unlinkSync, existsSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
const cfg = Object.fromEntries(
  readFileSync(new URL("../../.env", import.meta.url), "utf8")
    .split(/\r?\n/)
    .filter((l) => /^[A-Z_]+=/.test(l))
    .map((l) => {
      const i = l.indexOf("=");
      return [l.slice(0, i), l.slice(i + 1)];
    }),
);
function sql(text: string) {
  return execFileSync(
    "mysql",
    [
      "-h",
      "localhost",
      "-u",
      cfg.DB_USERNAME || "root",
      "mediaflow",
      "-N",
      "--default-character-set=utf8mb4",
      "-e",
      text,
    ],
    { env: { ...process.env, MYSQL_PWD: cfg.DB_PASSWORD }, encoding: "utf8" },
  ).trim();
}
async function login(page: Page, user: string, password: string) {
  await page.goto("/login");
  await page.getByRole("textbox", { name: "用户名", exact: true }).fill(user);
  await page.getByRole("textbox", { name: "密码", exact: true }).fill(password);
  await page.getByRole("button", { name: "登录工作空间" }).click();
  await expect(
    page.getByRole("heading", { name: "工作台", exact: true }),
  ).toBeVisible();
}
async function headers(page: Page) {
  return {
    Authorization:
      "Bearer " +
      (await page.evaluate(() =>
        sessionStorage.getItem("mediaflow.accessToken"),
      )),
  };
}
async function choose(page: Page, label: string, option: string) {
  await page
    .locator(".el-select")
    .filter({ has: page.getByRole("combobox", { name: label, exact: true }) })
    .click();
  await page.getByRole("option", { name: option, exact: true }).click();
}
test("real MySQL media lifecycle through admin editor and reviewer browsers", async ({
  page,
  browser,
  baseURL,
}) => {
  test.setTimeout(150000);
  page.setDefaultTimeout(10000);
  const prefix = "stage2_" + Date.now();
  const categoryIds: number[] = [];
  const assetIds: number[] = [];
  try {
    await login(page, "admin", "Admin@123456");
    for (const suffix of ["宣传", "活动"]) {
      await page.goto("/media/categories");
      await page.getByRole("button", { name: "新建分类", exact: true }).click();
      const dialog = page.getByRole("dialog");
      await dialog
        .getByRole("textbox", { name: "分类名称", exact: true })
        .fill(prefix + suffix);
      const response = page.waitForResponse(
        (r) =>
          r.url().endsWith("/api/media/categories") &&
          r.request().method() === "POST",
      );
      await dialog
        .getByRole("button", { name: "保存分类", exact: true })
        .click();
      const r = await response;
      expect(r.status()).toBe(201);
      categoryIds.push((await r.json()).data.id);
      await expect(dialog).not.toBeVisible();
    }
    const png = Buffer.from(
      await page.evaluate(async () => {
        const c = document.createElement("canvas");
        c.width = 32;
        c.height = 32;
        const x = c.getContext("2d")!;
        x.fillStyle = "#365feb";
        x.fillRect(0, 0, 32, 32);
        return Array.from(
          new Uint8Array(
            await (
              await new Promise<Blob>((r) =>
                c.toBlob((b) => r(b!), "image/png"),
              )
            ).arrayBuffer(),
          ),
        );
      }),
    );
    const video = Buffer.from(
      await page.evaluate(async () => {
        const c = document.createElement("canvas");
        c.width = 160;
        c.height = 90;
        const x = c.getContext("2d")!;
        x.fillStyle = "#365feb";
        x.fillRect(0, 0, 160, 90);
        const stream = c.captureStream(12);
        const chunks: Blob[] = [];
        const recorder = new MediaRecorder(stream, {
          mimeType: "video/mp4",
        });
        recorder.ondataavailable = (e) => chunks.push(e.data);
        const done = new Promise<void>((r) => (recorder.onstop = () => r()));
        recorder.start();
        await new Promise((r) => setTimeout(r, 400));
        recorder.stop();
        await done;
        stream.getTracks().forEach((t) => t.stop());
        return Array.from(new Uint8Array(await new Blob(chunks).arrayBuffer()));
      }),
    );
    const wav = Buffer.alloc(1644);
    wav.write("RIFF");
    wav.writeUInt32LE(1636, 4);
    wav.write("WAVEfmt ", 8);
    wav.writeUInt32LE(16, 16);
    wav.writeUInt16LE(1, 20);
    wav.writeUInt16LE(1, 22);
    wav.writeUInt32LE(8000, 24);
    wav.writeUInt32LE(16000, 28);
    wav.writeUInt16LE(2, 32);
    wav.writeUInt16LE(16, 34);
    wav.write("data", 36);
    wav.writeUInt32LE(1600, 40);
    async function upload(
      suffix: string,
      name: string,
      mime: string,
      buffer: Buffer,
    ) {
      await page.goto("/media");
      await page.getByRole("button", { name: "上传素材", exact: true }).click();
      const d = page.getByRole("dialog");
      await d
        .locator('input[type="file"]')
        .setInputFiles({ name, mimeType: mime, buffer });
      await d
        .getByRole("textbox", { name: "素材名称", exact: true })
        .fill(prefix + suffix);
      await d
        .getByRole("textbox", { name: "素材描述", exact: true })
        .fill("自动生成的本地验收素材");
      await d.locator(".el-select").click();
      await page
        .getByRole("option", { name: prefix + "宣传", exact: true })
        .click();
      const pending = page.waitForResponse(
        (r) =>
          r.url().endsWith("/api/media/assets") &&
          r.request().method() === "POST",
      );
      await d.getByRole("button", { name: "确认上传", exact: true }).click();
      const r = await pending;
      expect(r.status()).toBe(201);
      const data = (await r.json()).data;
      assetIds.push(data.id);
      await expect(d).not.toBeVisible();
      return data;
    }
    const image = await upload("图片", "generated.png", "image/png", png);
    const audio = await upload("音频", "generated.wav", "audio/wav", wav);
    const movie = await upload("视频", "generated.mp4", "video/mp4", video);
    await page
      .getByRole("textbox", { name: "搜索素材名称", exact: true })
      .fill(prefix);
    await choose(page, "类型筛选", "视频");
    await page.getByRole("button", { name: "筛选", exact: true }).click();
    await expect(
      page.getByRole("link", { name: prefix + "视频", exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("link", { name: prefix + "图片", exact: true }),
    ).toHaveCount(0);
    const h = await headers(page);
    let r = await page.request.get("/api/media/assets", {
      headers: h,
      params: {
        keyword: prefix,
        categoryId: categoryIds[0]!,
        size: 1,
        page: 2,
        sort: "title",
        direction: "asc",
      },
    });
    expect(r.status()).toBe(200);
    expect((await r.json()).data.total).toBe(3);
    expect((await r.json()).data.records).toHaveLength(1);
    r = await page.request.get("/api/media/stats", {
      headers: h,
      params: { keyword: prefix },
    });
    let stats = (await r.json()).data;
    expect([stats.total, stats.images, stats.audios, stats.videos]).toEqual([
      3, 1, 1, 1,
    ]);
    expect(
      stats.categories.find(
        (c: { categoryId: number }) => c.categoryId === categoryIds[0],
      ).count,
    ).toBe(3);
    await page.goto("/media/" + image.id);
    await expect
      .poll(() =>
        page
          .locator(".media-preview img")
          .evaluate((e: HTMLImageElement) => e.naturalWidth),
      )
      .toBe(32);
    const download = page.waitForEvent("download");
    await page.getByRole("link", { name: "下载原文件", exact: true }).click();
    expect((await download).suggestedFilename()).toBe("generated.png");
    await page.getByRole("button", { name: "编辑素材", exact: true }).click();
    let d = page.getByRole("dialog");
    await d
      .getByRole("textbox", { name: "素材名称", exact: true })
      .fill(prefix + "图片已修改");
    await d.locator(".el-select").click();
    await page
      .getByRole("option", { name: prefix + "活动", exact: true })
      .click();
    await d.getByRole("button", { name: "保存修改", exact: true }).click();
    await expect(d).not.toBeVisible();
    await expect(
      page.getByRole("heading", { name: prefix + "图片已修改", exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("cell", { name: prefix + "宣传", exact: false }).first(),
    ).toBeVisible();
    r = await page.request.get("/api/media/changes", {
      headers: h,
      params: { assetId: image.id },
    });
    let logs = (await r.json()).data.records;
    expect(
      logs.filter(
        (l: { operationType: string }) => l.operationType === "UPDATE",
      ),
    ).toHaveLength(2);
    const categoryLog = logs.find(
      (l: { fieldName: string; operationType: string }) =>
        l.fieldName === "category_id" && l.operationType === "UPDATE",
    );
    expect([
      categoryLog.oldValue,
      categoryLog.newValue,
      categoryLog.oldLabel,
      categoryLog.newLabel,
    ]).toEqual([
      String(categoryIds[0]),
      String(categoryIds[1]),
      prefix + "宣传",
      prefix + "活动",
    ]);
    const unchanged = await page.request.get("/api/media/assets/" + image.id, {
      headers: h,
    });
    const current = (await unchanged.json()).data;
    await page.request.put("/api/media/assets/" + image.id, {
      headers: h,
      data: {
        title: current.title,
        description: current.description,
        categoryId: current.categoryId,
        version: current.version,
      },
    });
    r = await page.request.get("/api/media/changes", {
      headers: h,
      params: { assetId: image.id },
    });
    expect((await r.json()).data.records).toHaveLength(logs.length);
    await page.getByRole("button", { name: "退出登录", exact: true }).click();
    await login(page, "editor01", "Editor@123456");
    const own = await upload("剪辑人员素材", "editor.png", "image/png", png);
    const eh = await headers(page);
    r = await page.request.put("/api/media/assets/" + image.id, {
      headers: eh,
      data: { title: "forbidden", description: "", version: current.version },
    });
    expect(r.status()).toBe(403);
    r = await page.request.put("/api/media/assets/" + own.id, {
      headers: eh,
      data: {
        title: prefix + "本人修改",
        description: "editor update",
        categoryId: categoryIds[1],
        version: 0,
      },
    });
    expect(r.status()).toBe(200);
    r = await page.request.post("/api/media/categories", {
      headers: eh,
      data: { categoryName: "forbidden", sortOrder: 0, status: 1 },
    });
    expect(r.status()).toBe(403);
    await page.getByRole("button", { name: "退出登录", exact: true }).click();
    await login(page, "reviewer01", "Reviewer@123456");
    await page.goto("/media/" + movie.id);
    await expect
      .poll(() =>
        page.locator("video").evaluate((v: HTMLVideoElement) => v.readyState),
      )
      .toBeGreaterThanOrEqual(1);
    await expect(
      page.getByRole("button", { name: "编辑素材", exact: true }),
    ).toHaveCount(0);
    r = await page.request.get("/api/media/assets/" + movie.id + "/content", {
      headers: { Range: "bytes=0-15" },
    });
    expect(r.status()).toBe(206);
    expect((await r.body()).length).toBe(16);
    r = await page.request.put("/api/media/assets/" + movie.id, {
      headers: await headers(page),
      data: { title: "forbidden", description: "", version: 0 },
    });
    expect(r.status()).toBe(403);
    await page.goto("/media/" + audio.id);
    await expect
      .poll(() =>
        page.locator("audio").evaluate((a: HTMLAudioElement) => a.readyState),
      )
      .toBeGreaterThanOrEqual(1);
    const anonymous = await browser.newContext({ baseURL });
    try {
      expect(
        (
          await anonymous.request.get(
            "/api/media/assets/" + movie.id + "/content",
          )
        ).status(),
      ).toBe(401);
    } finally {
      await anonymous.close();
    }
    await page.getByRole("button", { name: "退出登录", exact: true }).click();
    await login(page, "admin", "Admin@123456");
    await page.goto("/media/" + movie.id);
    await page.getByRole("button", { name: "归档素材", exact: true }).click();
    await page.getByRole("button", { name: "确定", exact: true }).click();
    await expect(
      page.getByText("已归档", { exact: false }).first(),
    ).toBeVisible();
    r = await page.request.get("/api/media/stats", {
      headers: await headers(page),
      params: { keyword: prefix },
    });
    stats = (await r.json()).data;
    expect([stats.total, stats.videos]).toEqual([3, 0]);
    r = await page.request.delete("/api/media/categories/" + categoryIds[0], {
      headers: await headers(page),
    });
    expect(r.status()).toBe(409);
    await page.goto("/media/stats");
    await page
      .getByRole("textbox", { name: "搜索素材名称", exact: true })
      .fill(prefix);
    await page.getByRole("button", { name: "筛选", exact: true }).click();
    await expect(
      page.locator(".stat-card").first().locator("strong"),
    ).toHaveText("3");
    await page.screenshot({
      path: "../.local/stage2-stats.png",
      fullPage: true,
    });
    await page.goto("/media/changes");
    await expect(
      page.getByRole("heading", { name: "修改记录", exact: true }),
    ).toBeVisible();
    await page.goto("/admin/users");
    await expect(
      page.getByRole("cell", { name: "editor01", exact: true }),
    ).toBeVisible();
  } finally {
    if (assetIds.length) {
      const ids = assetIds.map(Number).join(",");
      const keyRows = sql(
        `SELECT f.storage_key FROM media_file f JOIN media_asset a ON a.file_id=f.id WHERE a.id IN (${ids})`,
      );
      const fileIds = sql(
        `SELECT file_id FROM media_asset WHERE id IN (${ids})`,
      )
        .split(/\s+/)
        .filter(Boolean)
        .map(Number)
        .join(",");
      sql(
        `START TRANSACTION; DELETE FROM media_change_log WHERE asset_id IN (${ids}); DELETE FROM media_asset WHERE id IN (${ids}); ${fileIds ? "DELETE FROM media_file WHERE id IN (" + fileIds + ");" : ""} COMMIT;`,
      );
      for (const key of keyRows.split(/\s+/).filter(Boolean)) {
        if (!/^[a-f0-9-]{36}\.bin$/.test(key))
          throw new Error("Invalid cleanup storage key");
        const path = resolve(
          process.env.MEDIAFLOW_STORAGE_ROOT ||
            resolve(
              fileURLToPath(new URL("../../backend/", import.meta.url)),
              cfg.STORAGE_ROOT || "uploads",
            ),
          key,
        );
        if (existsSync(path)) unlinkSync(path);
      }
    }
    if (categoryIds.length)
      sql(
        `DELETE FROM media_category WHERE id IN (${categoryIds.map(Number).join(",")})`,
      );
  }
});
