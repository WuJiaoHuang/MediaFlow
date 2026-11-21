import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests",
  timeout: 30000,
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || "http://127.0.0.1:5173",
    headless: true,
    launchOptions: {
      args: [
        "--use-fake-device-for-media-stream",
        "--use-fake-ui-for-media-stream",
      ],
    },
    screenshot: "only-on-failure",
  },
  outputDir: "../.local/playwright-results",
  reporter: [
    ["list"],
    ["json", { outputFile: "../.local/playwright-report.json" }],
  ],
  workers: 1,
});
