import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";
export default defineConfig({
  plugins: [
    vue(),
    Components({
      resolvers: [ElementPlusResolver({ importStyle: "css" })],
      dts: false,
    }),
  ],
  // Prebundle resolver-injected style entry points before navigation; avoid form-reset reloads.
  optimizeDeps: {
    include: [
      "element-plus/es",
      "element-plus/es/components/alert/style/css",
      "element-plus/es/components/base/style/css",
      "element-plus/es/components/button/style/css",
      "element-plus/es/components/checkbox/style/css",
      "element-plus/es/components/checkbox-group/style/css",
      "element-plus/es/components/config-provider/style/css",
      "element-plus/es/components/date-picker/style/css",
      "element-plus/es/components/descriptions/style/css",
      "element-plus/es/components/descriptions-item/style/css",
      "element-plus/es/components/dialog/style/css",
      "element-plus/es/components/empty/style/css",
      "element-plus/es/components/form/style/css",
      "element-plus/es/components/form-item/style/css",
      "element-plus/es/components/icon/style/css",
      "element-plus/es/components/input/style/css",
      "element-plus/es/components/input-number/style/css",
      "element-plus/es/components/loading/style/css",
      "element-plus/es/components/menu/style/css",
      "element-plus/es/components/menu-item/style/css",
      "element-plus/es/components/message/style/css",
      "element-plus/es/components/message-box/style/css",
      "element-plus/es/components/option/style/css",
      "element-plus/es/components/pagination/style/css",
      "element-plus/es/components/progress/style/css",
      "element-plus/es/components/radio/style/css",
      "element-plus/es/components/radio-group/style/css",
      "element-plus/es/components/select/style/css",
      "element-plus/es/components/table/style/css",
      "element-plus/es/components/table-column/style/css",
      "element-plus/es/components/tag/style/css",
    ],
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      "/api": {
        target: process.env.MEDIAFLOW_BACKEND_URL || "http://127.0.0.1:8080",
        changeOrigin: true,
      },
    },
  },
});
