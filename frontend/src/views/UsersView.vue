<script setup lang="ts">
import { reactive, ref, onMounted } from "vue";
import { ElMessage } from "element-plus";
import { api } from "../api";
import type { User, Role, Page, ApiResponse } from "../types";
import { roleNames } from "../types";
const rows = ref<User[]>([]);
const roles = ref<Role[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const keyword = ref("");
const loading = ref(false);
const saving = ref(false);
const dialog = ref(false);
const selected = ref<User | null>(null);
const mode = ref<"create" | "edit" | "roles">("create");
const form = reactive({
  username: "",
  password: "",
  displayName: "",
  status: 1,
  roles: [] as string[],
});
async function load() {
  loading.value = true;
  try {
    const { data } = await api.get<ApiResponse<Page<User>>>("/admin/users", {
      params: { page: page.value, size: size.value, keyword: keyword.value },
    });
    rows.value = data.data.records;
    total.value = data.data.total;
  } catch {
  } finally {
    loading.value = false;
  }
}
function open(m: typeof mode.value, u?: User) {
  mode.value = m;
  selected.value = u || null;
  Object.assign(form, {
    username: u?.username || "",
    password: "",
    displayName: u?.displayName || "",
    status: u?.status ?? 1,
    roles: [...(u?.roles || ["EDITOR"])],
  });
  dialog.value = true;
}
async function save() {
  if (mode.value !== "roles" && !form.displayName.trim()) {
    ElMessage.warning("请输入显示名称");
    return;
  }
  if (
    mode.value === "create" &&
    (!/^[a-zA-Z0-9_]{3,50}$/.test(form.username) ||
      !/^[\x21-\x7E]{12,64}$/.test(form.password))
  ) {
    ElMessage.warning(
      "用户名需为 3–50 位字母、数字或下划线，密码需为 12–64 位 ASCII 可见字符",
    );
    return;
  }
  if (mode.value !== "edit" && !form.roles.length) {
    ElMessage.warning("请至少选择一个角色");
    return;
  }
  saving.value = true;
  try {
    if (mode.value === "create")
      await api.post("/admin/users", {
        username: form.username,
        password: form.password,
        displayName: form.displayName,
        roles: form.roles,
      });
    else if (mode.value === "edit")
      await api.put(`/admin/users/${selected.value!.id}`, {
        displayName: form.displayName,
        status: form.status,
      });
    else
      await api.put(`/admin/users/${selected.value!.id}/roles`, {
        roles: form.roles,
      });
    ElMessage.success("保存成功");
    dialog.value = false;
    form.password = "";
    await load();
  } catch {
  } finally {
    saving.value = false;
  }
}
onMounted(async () => {
  await load();
  try {
    roles.value = (
      await api.get<ApiResponse<Role[]>>("/admin/roles")
    ).data.data;
  } catch {}
});
</script>
<template>
  <div>
    <p class="eyebrow dark">TEAM MANAGEMENT</p>
    <div class="page-heading">
      <div>
        <h1>用户管理</h1>
        <p class="muted">管理团队账号、账号状态与角色权限。</p>
      </div>
      <el-button type="primary" @click="open('create')">＋ 新建用户</el-button>
    </div>
    <section class="surface users-surface">
      <div class="table-toolbar">
        <div>
          <h3>
            团队成员 <span class="count">{{ total }}</span>
          </h3>
        </div>
        <el-input
          v-model="keyword"
          placeholder="搜索用户名或显示名称"
          clearable
          style="max-width: 300px"
          aria-label="搜索用户"
          @keyup.enter="
            page = 1;
            load();
          "
          @clear="
            page = 1;
            load();
          "
          ><template #append
            ><el-button
              @click="
                page = 1;
                load();
              "
              >搜索</el-button
            ></template
          ></el-input
        >
      </div>
      <el-table
        v-loading="loading"
        :data="rows"
        stripe
        empty-text="没有匹配的用户"
        ><el-table-column
          prop="username"
          label="用户名"
          min-width="140"
        /><el-table-column
          prop="displayName"
          label="显示名称"
          min-width="140"
        /><el-table-column label="角色" min-width="220"
          ><template #default="scope"
            ><div class="tags">
              <el-tag v-for="r in scope.row.roles" :key="r" effect="plain">{{
                roleNames[r] || r
              }}</el-tag>
            </div></template
          ></el-table-column
        ><el-table-column label="状态" width="100"
          ><template #default="scope"
            ><el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{
              scope.row.status === 1 ? "启用" : "禁用"
            }}</el-tag></template
          ></el-table-column
        ><el-table-column label="操作" width="180" fixed="right"
          ><template #default="scope"
            ><el-button link type="primary" @click="open('edit', scope.row)"
              >编辑</el-button
            ><el-button link type="primary" @click="open('roles', scope.row)"
              >分配角色</el-button
            ></template
          ></el-table-column
        ></el-table
      ><el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="
          page = 1;
          load();
        "
      />
    </section>
    <p class="permission-note">
      角色决定功能访问权限。后续任务与作品的访问范围将单独进行数据权限校验。
    </p>
    <el-dialog
      v-model="dialog"
      :title="
        mode === 'create'
          ? '新建用户'
          : mode === 'roles'
            ? '分配角色'
            : '编辑用户'
      "
      width="min(480px,92vw)"
      @closed="form.password = ''"
      :close-on-click-modal="false"
      ><el-form label-position="top" @submit.prevent="save"
        ><template v-if="mode === 'create'"
          ><el-form-item label="用户名" required
            ><el-input
              v-model="form.username"
              aria-label="用户名"
              maxlength="50" /></el-form-item
          ><el-form-item label="初始密码" required
            ><el-input
              v-model="form.password"
              type="password"
              show-password
              aria-label="初始密码"
              autocomplete="new-password"
              placeholder="12–64 位 ASCII 可见字符"
              maxlength="64" /></el-form-item></template
        ><el-form-item v-if="mode !== 'roles'" label="显示名称" required
          ><el-input
            v-model="form.displayName"
            aria-label="显示名称"
            maxlength="100" /></el-form-item
        ><el-form-item v-if="mode === 'edit'" label="账号状态"
          ><el-radio-group v-model="form.status"
            ><el-radio :value="1">启用</el-radio
            ><el-radio :value="0">禁用</el-radio></el-radio-group
          ></el-form-item
        ><el-form-item v-if="mode !== 'edit'" label="角色" required
          ><el-checkbox-group v-model="form.roles"
            ><el-checkbox v-for="r in roles" :key="r.id" :value="r.roleCode">{{
              r.roleName
            }}</el-checkbox></el-checkbox-group
          ></el-form-item
        ></el-form
      ><template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >保存</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
