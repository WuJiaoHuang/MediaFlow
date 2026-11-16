<script setup lang="ts">
import { useAuthStore } from "../stores/auth";
import { roleNames } from "../types";
const auth = useAuthStore();
</script>
<template>
  <div>
    <p class="eyebrow dark">WORKSPACE OVERVIEW</p>
    <div class="page-heading">
      <div>
        <h1>工作台</h1>
        <p class="muted">
          欢迎回来，{{ auth.user?.displayName }}。从这里开始你的协作。
        </p>
      </div>
      <span class="pill">团队工作空间</span>
    </div>
    <section class="welcome-banner">
      <div>
        <p>保持专注，有序协作</p>
        <h2>每个角色，都有自己的创作空间。</h2>
        <p>
          你的账号和权限已就绪，随时查看个人信息{{
            auth.isAdmin ? "，或管理团队成员" : ""
          }}。
        </p>
        <el-button @click="$router.push('/profile')">查看个人信息 →</el-button>
      </div>
      <div class="banner-mark" aria-hidden="true">M<span>FLOW</span></div>
    </section>
    <div class="overview-grid">
      <section class="surface">
        <p class="section-kicker">当前身份</p>
        <h3>{{ auth.user?.displayName }}</h3>
        <p class="muted">@{{ auth.user?.username }}</p>
        <div class="tags">
          <el-tag v-for="r in auth.user?.roles" :key="r" effect="plain">{{
            roleNames[r] || r
          }}</el-tag>
        </div>
      </section>
      <section class="surface">
        <p class="section-kicker">账号状态</p>
        <h3><span class="online-dot"></span> 正常使用</h3>
        <p class="muted">账号已启用，可访问已授权的功能。</p>
        <router-link to="/profile" class="text-link"
          >查看账号详情 →</router-link
        >
      </section>
      <section class="surface">
        <p class="section-kicker">快捷入口</p>
        <h3>{{ auth.isAdmin ? "团队成员管理" : "我的个人信息" }}</h3>
        <p class="muted">
          {{
            auth.isAdmin
              ? "管理账号状态，配置团队角色与访问权限。"
              : "查看当前账号、角色及基本资料。"
          }}
        </p>
        <router-link
          :to="auth.isAdmin ? '/admin/users' : '/profile'"
          class="text-link"
          >{{ auth.isAdmin ? "前往用户管理" : "前往个人信息" }} →</router-link
        >
      </section>
    </div>
    <section class="surface workspace-note">
      <div class="note-icon">i</div>
      <div>
        <h3>关于当前工作空间</h3>
        <p class="muted">
          当前已开放账号与权限管理。媒体素材、剪辑任务和协作反馈将在后续阶段接入。
        </p>
      </div>
    </section>
  </div>
</template>
