<script setup lang="ts">
import { useAuthStore } from "../stores/auth";
import { useRouter, useRoute } from "vue-router";
import { roleNames } from "../types";
import { House, User, UserFilled, SwitchButton } from "@element-plus/icons-vue";
const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
function logout() {
  auth.logout();
  void router.replace("/login");
}
</script>
<template>
  <div class="app-shell">
    <aside class="sidebar">
      <router-link to="/" class="brand">M<span>MediaFlow</span></router-link>
      <div class="workspace-label">团队工作空间</div>
      <el-menu :default-active="route.path" router
        ><el-menu-item index="/"
          ><el-icon><House /></el-icon><span>工作台</span></el-menu-item
        ><el-menu-item v-if="auth.isAdmin" index="/admin/users"
          ><el-icon><UserFilled /></el-icon><span>用户管理</span></el-menu-item
        ><el-menu-item index="/profile"
          ><el-icon><User /></el-icon><span>个人信息</span></el-menu-item
        ></el-menu
      >
      <div class="sidebar-bottom">
        <div class="online-dot"></div>
        MediaFlow 工作空间
      </div>
    </aside>
    <div class="main-shell">
      <header class="topbar">
        <span
          >工作空间
          <span class="muted"
            >/
            {{
              route.name === "users"
                ? "用户管理"
                : route.name === "profile"
                  ? "个人信息"
                  : "工作台"
            }}</span
          ></span
        >
        <div class="topbar-user">
          <span class="avatar">{{ auth.user?.displayName.slice(0, 1) }}</span
          ><span
            >{{ auth.user?.displayName }}
            <small class="muted">{{
              auth.user?.roles.map((r) => roleNames[r] || r).join(" / ")
            }}</small></span
          ><el-button text :icon="SwitchButton" @click="logout"
            >退出登录</el-button
          >
        </div>
      </header>
      <main class="page-content"><router-view /></main>
      <footer class="app-footer">MediaFlow · 让协作有序，让创作专注</footer>
    </div>
  </div>
</template>
