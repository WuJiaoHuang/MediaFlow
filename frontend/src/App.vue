<script setup lang="ts">
import { onMounted, onUnmounted } from "vue";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import { useAuthStore } from "./stores/auth";
import { router } from "./router";
const auth = useAuthStore();
const onUnauthorized = () => {
  auth.logout();
  void router.replace("/login");
};
onMounted(() =>
  window.addEventListener("mediaflow:unauthorized", onUnauthorized),
);
onUnmounted(() =>
  window.removeEventListener("mediaflow:unauthorized", onUnauthorized),
);
</script>
<template>
  <el-config-provider :locale="zhCn"><router-view /></el-config-provider>
</template>
