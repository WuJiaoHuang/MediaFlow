<script setup lang="ts">
import { useAuthStore } from "../stores/auth";
import { roleNames } from "../types";
const auth = useAuthStore();
</script>
<template>
  <div>
    <p class="eyebrow dark">MY ACCOUNT</p>
    <div class="page-heading">
      <div>
        <h1>个人信息</h1>
        <p class="muted">你的团队身份与账号资料。</p>
      </div>
    </div>
    <section class="surface profile-card">
      <div class="profile-intro">
        <span class="avatar large">{{
          auth.user?.displayName.slice(0, 1)
        }}</span>
        <div>
          <h2>{{ auth.user?.displayName }}</h2>
          <p class="muted">@{{ auth.user?.username }}</p>
        </div>
        <el-tag type="success">已启用</el-tag>
      </div>
      <el-descriptions :column="1" border
        ><el-descriptions-item label="用户 ID">{{
          auth.user?.id
        }}</el-descriptions-item
        ><el-descriptions-item label="用户名">{{
          auth.user?.username
        }}</el-descriptions-item
        ><el-descriptions-item label="显示名称">{{
          auth.user?.displayName
        }}</el-descriptions-item
        ><el-descriptions-item label="角色"
          ><div class="tags">
            <el-tag v-for="role in auth.user?.roles" :key="role">{{
              roleNames[role] || role
            }}</el-tag>
          </div></el-descriptions-item
        ><el-descriptions-item label="创建时间">{{
          auth.user?.createdAt?.replace("T", " ")
        }}</el-descriptions-item
        ><el-descriptions-item label="更新时间">{{
          auth.user?.updatedAt?.replace("T", " ")
        }}</el-descriptions-item></el-descriptions
      >
      <p class="muted profile-note">
        如需变更姓名、角色或账号状态，请联系管理员。
      </p>
    </section>
  </div>
</template>
