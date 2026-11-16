<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useAuthStore } from "../stores/auth";
const form = reactive({ username: "", password: "" });
const loading = ref(false);
const error = ref("");
const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
async function submit() {
  if (!form.username || !form.password) {
    error.value = "请输入用户名和密码";
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    await auth.login(form.username, form.password);
    const next =
      typeof route.query.redirect === "string" ? route.query.redirect : "/";
    await router.replace(
      next.startsWith("/") && !next.startsWith("//") ? next : "/",
    );
  } catch {
    error.value = "登录失败，请检查账号、密码和账号状态";
  } finally {
    loading.value = false;
  }
}
</script>
<template>
  <main class="login-shell">
    <section class="login-story">
      <a class="brand light" href="/">M<span>MediaFlow</span></a>
      <div>
        <p class="eyebrow">YOUR TEAM. ONE FLOW.</p>
        <h1>让每一份创作，<br />有序流转。</h1>
        <p class="story-description">
          媒体资产与剪辑协作管理系统<br />从清晰的分工开始，连接团队的每一步。
        </p>
        <div class="flow-art" aria-hidden="true">
          <div class="art-line"></div>
          <div class="art-card">01 <span>团队</span></div>
          <div class="art-card mid">02 <span>协作</span></div>
          <div class="art-card end">03 <span>创作</span></div>
        </div>
      </div>
      <small>MEDIAFLOW / 企业内部协作平台</small>
    </section>
    <section class="login-area">
      <div class="login-card">
        <span class="pill">工作空间登录</span>
        <h2>欢迎回来</h2>
        <p class="muted">使用你的团队账号，进入 MediaFlow。</p>
        <el-form @submit.prevent="submit" label-position="top"
          ><el-form-item label="用户名"
            ><el-input
              v-model="form.username"
              placeholder="请输入用户名"
              autocomplete="username"
              size="large"
              aria-label="用户名"
              maxlength="50" /></el-form-item
          ><el-form-item label="密码"
            ><el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="请输入密码"
              autocomplete="current-password"
              size="large"
              aria-label="密码"
              maxlength="72"
          /></el-form-item>
          <p v-if="error" role="alert" class="error">{{ error }}</p>
          <el-button
            native-type="submit"
            type="primary"
            size="large"
            :loading="loading"
            class="login-button"
            >登录工作空间</el-button
          ></el-form
        >
        <p class="login-note">
          账号由管理员统一创建。无法登录时，请联系管理员。
        </p>
      </div>
      <small class="login-footer">MediaFlow · 安全连接每一位协作者</small>
    </section>
  </main>
</template>
