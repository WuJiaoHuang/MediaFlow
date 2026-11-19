<script setup lang="ts">
import { useAuthStore } from "../stores/auth";
import { useRouter, useRoute } from "vue-router";
import { roleNames } from "../types";
import { House, User, UserFilled, SwitchButton, FolderOpened, DataAnalysis, Clock, Collection } from "@element-plus/icons-vue";
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
        ><el-menu-item index="/media"><el-icon><FolderOpened /></el-icon><span>媒体库</span></el-menu-item><el-menu-item index="/media/stats"><el-icon><DataAnalysis /></el-icon><span>媒体统计</span></el-menu-item><el-menu-item index="/media/changes"><el-icon><Clock /></el-icon><span>修改记录</span></el-menu-item><el-menu-item v-if="auth.isAdmin" index="/media/categories"><el-icon><Collection /></el-icon><span>分类管理</span></el-menu-item><el-menu-item v-if="auth.isAdmin" index="/admin/users"
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
                  : ({"media":"媒体库","media-stats":"媒体统计","media-changes":"修改记录","media-categories":"分类管理","media-detail":"素材详情"} as Record<string,string>)[String(route.name)] || "工作台"
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
