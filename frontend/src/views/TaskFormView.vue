<script setup lang="ts">
import { ref, reactive, computed, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { api } from "../api";
import { getData, listAssets, type Asset } from "../media";
import { taskDetail, priorityNames, type TaskPerson } from "../tasks";
import type { ApiResponse } from "../types";
const route = useRoute();
const router = useRouter();
const id = route.params.id ? Number(route.params.id) : null;
const busy = ref(false);
const loading = ref(false);
const error = ref("");
const version = ref<number | null>(null);
const people = ref<TaskPerson[]>([]);
const options = ref<Asset[]>([]);
const chosen = ref<Asset[]>([]);
const searching = ref(false);
const locked = ref(false);
const form = reactive({
  title: "",
  description: "",
  assigneeId: undefined as number | undefined,
  reviewerId: undefined as number | undefined,
  priority: "NORMAL",
  dueAt: undefined as string | undefined,
  assetIds: [] as number[],
});
function unique(rows: TaskPerson[]) {
  return [...new Map(rows.map((p) => [p.id, p])).values()];
}
const executors = computed(() =>
  unique(people.value.filter((p) => p.roleCode === "EDITOR")),
);
const reviewers = computed(() =>
  unique(
    people.value.filter(
      (p) => p.roleCode === "REVIEWER" || p.roleCode === "ADMIN",
    ),
  ),
);
const assets = computed(() => [
  ...new Map(
    [...chosen.value, ...options.value].map((a) => [a.id, a]),
  ).values(),
]);
async function searchAssets(keyword = "") {
  searching.value = true;
  try {
    options.value = (
      await listAssets({ keyword, size: 100, status: "ACTIVE" })
    ).records;
  } catch {
    error.value = "素材选项加载失败，请重试";
  } finally {
    searching.value = false;
  }
}
async function load() {
  loading.value = true;
  error.value = "";
  try {
    people.value = await getData<TaskPerson[]>("/tasks/people");
    if (id) {
      const t = await taskDetail(id);
      locked.value = t.status === "COMPLETED";
      Object.assign(form, {
        title: t.title,
        description: t.description,
        assigneeId: t.assigneeId,
        reviewerId: t.reviewerId,
        priority: t.priority,
        dueAt: t.dueAt ?? undefined,
      });
      version.value = t.version;
      chosen.value = await getData<Asset[]>(`/tasks/${id}/assets`);
      form.assetIds = chosen.value.map((a) => a.id);
    }
    await searchAssets();
  } catch {
    error.value = "任务或选项加载失败，请返回列表或重新加载";
  } finally {
    loading.value = false;
  }
}
async function save() {
  if (busy.value) return;
  if (
    !form.title.trim() ||
    !form.description.trim() ||
    !form.assigneeId ||
    !form.reviewerId ||
    !form.assetIds.length
  ) {
    error.value = "请填写标题、任务要求、执行人、审核人，并至少关联一份素材";
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    const data = { ...form, dueAt: form.dueAt || null, version: version.value };
    const response = id
      ? await api.put<ApiResponse<{ id: number }>>("/tasks/" + id, data)
      : await api.post<ApiResponse<{ id: number }>>("/tasks", data);
    ElMessage.success(id ? "任务已更新" : "任务已创建");
    await router.replace("/tasks/" + response.data.data.id);
  } catch (e: any) {
    error.value =
      e.response?.data?.message || "任务保存失败，请重新加载后检查内容";
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div v-loading="loading">
    <el-button text @click="$router.push('/tasks')">← 返回任务列表</el-button>
    <div class="page-heading">
      <div>
        <h1>{{ id ? "编辑任务" : "创建任务" }}</h1>
        <p class="muted">
          关联已有素材，不复制媒体文件。任务调整会立即更新参与者授权。
        </p>
      </div>
    </div>
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
    /><el-button v-if="error" @click="load">重新加载任务</el-button
    ><el-alert
      v-if="locked"
      title="任务已完成，不能继续修改或重开"
      type="warning"
      :closable="false"
    />
    <section class="surface task-form">
      <el-form
        label-position="top"
        :disabled="locked || loading"
        @submit.prevent="save"
        ><el-form-item label="任务标题" required
          ><el-input
            v-model="form.title"
            aria-label="任务标题"
            maxlength="200" /></el-form-item
        ><el-form-item label="任务要求" required
          ><el-input
            v-model="form.description"
            type="textarea"
            :rows="5"
            aria-label="任务要求"
            maxlength="4000"
            show-word-limit
        /></el-form-item>
        <div class="task-form-grid">
          <el-form-item label="指定执行人" required
            ><el-select
              v-model="form.assigneeId"
              aria-label="指定执行人"
              filterable
              ><el-option
                v-for="p in executors"
                :key="p.id"
                :label="p.displayName + ' (@' + p.username + ')'"
                :value="p.id" /></el-select></el-form-item
          ><el-form-item label="指定审核人" required
            ><el-select
              v-model="form.reviewerId"
              aria-label="指定审核人"
              filterable
              ><el-option
                v-for="p in reviewers"
                :key="p.id"
                :label="p.displayName + ' (@' + p.username + ')'"
                :value="p.id" /></el-select></el-form-item
          ><el-form-item label="优先级"
            ><el-select v-model="form.priority" aria-label="优先级"
              ><el-option
                v-for="(label, value) in priorityNames"
                :key="value"
                :label="label"
                :value="value" /></el-select></el-form-item
          ><el-form-item label="截止时间"
            ><el-date-picker
              v-model="form.dueAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="允许不设置"
              aria-label="截止时间"
              clearable
          /></el-form-item>
        </div>
        <el-form-item label="关联素材" required
          ><el-select
            v-model="form.assetIds"
            multiple
            filterable
            remote
            :remote-method="searchAssets"
            :loading="searching"
            :multiple-limit="50"
            aria-label="关联素材"
            placeholder="输入名称搜索已有正常素材"
            ><el-option
              v-for="a in assets"
              :key="a.id"
              :label="
                '#' +
                a.id +
                ' ' +
                a.title +
                ' · ' +
                (a.accessScope === 'RESTRICTED' ? '受限' : '共享') +
                (a.status !== 'ACTIVE' ? '（已归档）' : '')
              "
              :value="a.id" /></el-select
        ></el-form-item>
        <p class="muted">
          共享素材保持媒体库独立可读。需要私有隔离时，请先在素材详情将访问范围设为“受限”；仅关联任务不会自动改变素材的基础授权。
        </p>
        <el-button
          type="primary"
          native-type="submit"
          :loading="busy"
          :disabled="locked || loading"
          >保存任务</el-button
        ></el-form
      >
    </section>
  </div>
</template>
