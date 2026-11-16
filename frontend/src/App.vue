<script setup lang="ts">
import { onMounted, onUnmounted } from "vue";
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
<template><router-view /></template>
