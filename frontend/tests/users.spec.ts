import { test, expect } from "@playwright/test";
import { readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";

test("admin creates, edits, assigns roles and disables a user through UI", async ({
  page,
}) => {
  const username = "ui_" + Date.now();
  const config = Object.fromEntries(
    readFileSync(new URL("../../.env", import.meta.url), "utf8")
      .split(/\r?\n/)
      .filter((line) => /^[A-Z_]+=/.test(line))
      .map((line) => {
        const i = line.indexOf("=");
        return [line.slice(0, i), line.slice(i + 1)];
      }),
  );
  try {
    await page.goto("/login");
    await page
      .getByRole("textbox", { name: "用户名", exact: true })
      .fill("admin");
    await page
      .getByRole("textbox", { name: "密码", exact: true })
      .fill("Admin@123456");
    await page.getByRole("button", { name: "登录工作空间" }).click();
    await page.getByRole("menuitem", { name: "用户管理" }).click();
    await page.getByRole("button", { name: "新建用户" }).click();
    let dialog = page.getByRole("dialog");
    await dialog
      .getByRole("textbox", { name: "用户名", exact: true })
      .fill(username);
    await dialog
      .getByRole("textbox", { name: "初始密码" })
      .fill("Browser@Test123456");
    await dialog
      .getByRole("textbox", { name: "显示名称" })
      .fill("浏览器验收用户");
    await dialog.getByText("审核人员", { exact: true }).click();
    await dialog.getByRole("button", { name: "保存", exact: true }).click();
    await expect(dialog).not.toBeVisible();
    await page.getByRole("textbox", { name: "搜索用户" }).fill(username);
    await page.getByRole("button", { name: "搜索", exact: true }).click();
    const row = page.getByRole("row").filter({ hasText: username });
    await expect(row).toContainText("剪辑人员");
    await expect(row).toContainText("审核人员");
    await row.getByRole("button", { name: "编辑", exact: true }).click();
    dialog = page.getByRole("dialog");
    await dialog
      .getByRole("textbox", { name: "显示名称" })
      .fill("浏览器验收已更新");
    await dialog.getByText("禁用", { exact: true }).click();
    await dialog.getByRole("button", { name: "保存", exact: true }).click();
    await expect(dialog).not.toBeVisible();
    await expect(row).toContainText("浏览器验收已更新");
    await expect(row).toContainText("禁用");
    await row.getByRole("button", { name: "分配角色", exact: true }).click();
    dialog = page.getByRole("dialog");
    await dialog.getByText("剪辑人员", { exact: true }).click();
    await dialog.getByRole("button", { name: "保存", exact: true }).click();
    await expect(dialog).not.toBeVisible();
    await expect(row).not.toContainText("剪辑人员");
    await expect(row).toContainText("审核人员");
    await page.screenshot({
      path: "../.local/user-management.png",
      fullPage: true,
    });
  } finally {
    execFileSync(
      "mysql",
      [
        "-h",
        "localhost",
        "-u",
        config.DB_USERNAME || "root",
        "mediaflow",
        "-e",
        `DELETE FROM sys_user WHERE username='${username}';`,
      ],
      { env: { ...process.env, MYSQL_PWD: config.DB_PASSWORD }, stdio: "pipe" },
    );
  }
});
