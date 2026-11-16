import { test, expect, type Page } from "@playwright/test";
async function login(page: Page, user: string, password: string) {
  await page.goto("/login");
  await page.getByRole("textbox", { name: "用户名", exact: true }).fill(user);
  await page.getByRole("textbox", { name: "密码", exact: true }).fill(password);
  await page.getByRole("button", { name: "登录工作空间" }).click();
  await expect(
    page.getByRole("heading", { name: "工作台", exact: true }),
  ).toBeVisible();
}
test("admin login, reload, user list, profile and logout", async ({ page }) => {
  await login(page, "admin", "Admin@123456");
  await page.screenshot({ path: "../.local/workbench.png", fullPage: true });
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "工作台", exact: true }),
  ).toBeVisible();
  await page.getByRole("menuitem", { name: "用户管理" }).click();
  await expect(
    page.getByRole("heading", { name: "用户管理", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("cell", { name: "editor01", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "新建用户" }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByRole("button", { name: "取消", exact: true }).click();
  await page.getByRole("menuitem", { name: "个人信息" }).click();
  await expect(page.getByText("admin", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "退出登录" }).click();
  await expect(page).toHaveURL(/login/);
  await page.goto("/admin/users");
  await expect(page).toHaveURL(/login/);
});
for (const [user, password] of [
  ["editor01", "Editor@123456"],
  ["reviewer01", "Reviewer@123456"],
])
  test(`${user} denied admin route and HTTP API`, async ({ page }) => {
    await login(page, user!, password!);
    await expect(page.getByRole("menuitem", { name: "用户管理" })).toHaveCount(
      0,
    );
    await page.goto("/admin/users");
    await expect.poll(() => new URL(page.url()).pathname).toBe("/");
    const status = await page.evaluate(async () => {
      const r = await fetch("/api/admin/users", {
        headers: {
          Authorization:
            "Bearer " + sessionStorage.getItem("mediaflow.accessToken"),
        },
      });
      return r.status;
    });
    expect(status).toBe(403);
  });
test("wrong password shows error without login", async ({ page }) => {
  await page.goto("/login");
  await page
    .getByRole("textbox", { name: "用户名", exact: true })
    .fill("admin");
  await page
    .getByRole("textbox", { name: "密码", exact: true })
    .fill("Wrong@123456");
  await page.getByRole("button", { name: "登录工作空间" }).click();
  await expect(
    page.getByRole("alert").filter({ hasText: "登录失败" }),
  ).toBeVisible();
  await expect(page).toHaveURL(/login/);
  await page.screenshot({ path: "../.local/login.png", fullPage: true });
});
test("expired or invalid local token redirects to login", async ({ page }) => {
  await page.goto("/login");
  await page.evaluate(() =>
    sessionStorage.setItem("mediaflow.accessToken", "invalid.jwt.token"),
  );
  await page.goto("/profile");
  await expect(page).toHaveURL(/login/);
  expect(
    await page.evaluate(() => sessionStorage.getItem("mediaflow.accessToken")),
  ).toBeNull();
});
