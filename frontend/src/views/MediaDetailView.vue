<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { api } from "../api";
import { useAuthStore } from "../stores/auth";
import {
  getData,
  listCategories,
  enabledCategories,
  type Asset,
  type Category,
  type Change,
  typeNames,
  formatBytes,
} from "../media";
import type { ApiResponse, Page } from "../types";
import ChangeTable from "../components/ChangeTable.vue";
const route = useRoute();
function seekTimecode(event: Event) {
  const media = event.target as HTMLMediaElement;
  const ms = Number(route.query.timecodeMs);
  if (Number.isFinite(ms) && ms >= 0) {
    const seconds = ms / 1000;
    media.currentTime = Number.isFinite(media.duration)
      ? Math.min(seconds, media.duration)
      : seconds;
  }
}
const id = Number(route.params.id);
const taskId = route.query.taskId ? Number(route.query.taskId) : undefined;
const scopeDialog = ref(false);
const nextScope = ref("LIBRARY");
const auth = useAuthStore();
const asset = ref<Asset | null>(null);
const error = ref(false);
const loading = ref(false);
const access = ref<{ contentUrl: string; downloadUrl: string } | null>(null);
const previewError = ref(false);
const historyError = ref(false);
const history = ref<Change[]>([]);
const total = ref(0);
const page = ref(1);
const categories = ref<Category[]>([]);
const edit = ref(false);
const saving = ref(false);
const form = reactive({
  title: "",
  description: "",
  categoryId: undefined as number | undefined,
});
const choices = computed(() => enabledCategories(categories.value));
const canEdit = computed(
  () =>
    asset.value?.status === "ACTIVE" &&
    (auth.isAdmin ||
      (auth.user?.roles.includes("EDITOR") &&
        asset.value?.createdBy === auth.user.id)),
);
let timer: ReturnType<typeof setInterval> | undefined;
async function renew() {
  try {
    const response = await api.post<
      ApiResponse<{ contentUrl: string; downloadUrl: string }>
    >(`/media/assets/${id}/access`, null, { params: { taskId } });
    access.value = response.data.data;
    previewError.value = false;
  } catch {
    previewError.value = true;
    access.value = null;
  }
}
async function loadHistory() {
  historyError.value = false;
  try {
    const data = await getData<Page<Change>>("/media/changes", {
      assetId: id,
      page: page.value,
      size: 10,
    });
    history.value = data.records;
    total.value = data.total;
  } catch {
    historyError.value = true;
  }
}
async function load() {
  loading.value = true;
  error.value = false;
  try {
    asset.value = await getData<Asset>(`/media/assets/${id}`, { taskId });
    await renew();
    await loadHistory();
  } catch {
    error.value = true;
    asset.value = null;
  } finally {
    loading.value = false;
  }
}
async function saveScope() {
  if (!asset.value) return;
  try {
    await api.put(`/media/assets/${id}/scope`, {
      accessScope: nextScope.value,
      version: asset.value.version,
    });
    scopeDialog.value = false;
    ElMessage.success("访问范围已更新");
    await load();
  } catch {}
}
async function openEdit() {
  if (!asset.value) return;
  try {
    categories.value = await listCategories();
    Object.assign(form, {
      title: asset.value.title,
      description: asset.value.description,
      categoryId: asset.value.categoryId ?? undefined,
    });
    edit.value = true;
  } catch {}
}
async function save() {
  if (!asset.value || saving.value) return;
  if (!form.title.trim()) {
    ElMessage.warning("请输入素材名称");
    return;
  }
  saving.value = true;
  try {
    await api.put(`/media/assets/${id}`, {
      title: form.title,
      description: form.description,
      categoryId: form.categoryId ?? null,
      version: asset.value.version,
    });
    edit.value = false;
    ElMessage.success("素材已更新");
    await load();
  } catch {
  } finally {
    saving.value = false;
  }
}
async function archive() {
  try {
    await ElMessageBox.confirm(
      "归档后将从正常素材列表及默认统计中移除，原始文件与修改记录保留。",
      "归档素材",
      { type: "warning" },
    );
    await api.post(`/media/assets/${id}/archive`);
    ElMessage.success("素材已归档");
    await load();
  } catch {}
}
onMounted(async () => {
  await load();
  timer = setInterval(() => {
    if (asset.value) void renew();
  }, 90000);
});
onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>
<template>
  <div v-loading="loading">
    <el-button text @click="$router.push('/media')">← 返回媒体库</el-button
    ><el-alert
      v-if="error"
      title="素材加载失败或没有访问权限"
      type="error"
      :closable="false"
    /><el-button v-if="error" @click="load">重试</el-button
    ><template v-if="asset"
      ><div class="page-heading">
        <div>
          <h1>{{ asset.title }}</h1>
          <p class="muted">
            {{ typeNames[asset.mediaType] }} ·
            {{ asset.categoryName || "未分类" }} ·
            {{ asset.status === "ACTIVE" ? "正常素材" : "已归档" }}
          </p>
        </div>
        <div class="tags">
          <el-button v-if="canEdit" @click="openEdit">编辑素材</el-button
          ><el-button
            v-if="auth.isAdmin && asset.status === 'ACTIVE'"
            type="warning"
            plain
            @click="archive"
            >归档素材</el-button
          ><a v-if="access" :href="access.downloadUrl" class="download-link"
            >下载原文件</a
          >
        </div>
      </div>
      <div class="task-context-note">
        <el-tag
          :type="asset.accessScope === 'RESTRICTED' ? 'warning' : 'info'"
          >{{
            asset.accessScope === "RESTRICTED" ? "受限素材" : "共享素材"
          }}</el-tag
        ><span v-if="taskId" class="muted">任务 #{{ taskId }} 上下文</span
        ><el-button
          v-if="auth.isAdmin"
          size="small"
          @click="
            nextScope = asset!.accessScope;
            scopeDialog = true;
          "
          >设置访问范围</el-button
        >
      </div>
      <section class="surface media-preview">
        <el-alert
          v-if="previewError"
          title="预览加载失败，请重新授权后重试"
          type="error"
          :closable="false"
        /><el-button v-if="previewError" @click="renew">重新授权预览</el-button
        ><template v-if="access"
          ><img
            v-if="asset.mediaType === 'IMAGE'"
            :src="access.contentUrl"
            :alt="asset.title"
            @error="previewError = true" /><audio
            v-else-if="asset.mediaType === 'AUDIO'"
            :src="access.contentUrl"
            controls
            preload="metadata"
            @loadedmetadata="seekTimecode"
            @error="previewError = true" /><video
            v-else
            :src="access.contentUrl"
            controls
            preload="metadata"
            @loadedmetadata="seekTimecode"
            @error="previewError = true"
        /></template>
      </section>
      <section class="surface detail-metadata">
        <el-descriptions :column="2" border
          ><el-descriptions-item label="素材 ID">{{
            asset.id
          }}</el-descriptions-item
          ><el-descriptions-item label="文件大小">{{
            formatBytes(asset.fileSize)
          }}</el-descriptions-item
          ><el-descriptions-item label="上传人"
            >{{ asset.uploaderName }} (#{{
              asset.createdBy
            }})</el-descriptions-item
          ><el-descriptions-item label="上传时间">{{
            asset.createdAt.replace("T", " ")
          }}</el-descriptions-item
          ><el-descriptions-item label="原始文件名">{{
            asset.originalFilename
          }}</el-descriptions-item
          ><el-descriptions-item label="内容类型">{{
            asset.mimeType
          }}</el-descriptions-item
          ><el-descriptions-item label="描述" :span="2"
            ><span class="preserve-text">{{
              asset.description || "暂无描述"
            }}</span></el-descriptions-item
          ></el-descriptions
        >
      </section>
      <section class="surface">
        <h3>修改历史</h3>
        <p class="muted">记录真实字段变化，分类名称保留修改时的快照。</p>
        <el-alert
          v-if="historyError"
          title="修改记录加载失败"
          type="error"
          :closable="false"
        /><ChangeTable v-else :rows="history" /><el-pagination
          v-model:current-page="page"
          :total="total"
          :page-size="10"
          layout="total,prev,pager,next"
          @current-change="loadHistory"
        />
      </section>
      <el-dialog
        v-model="scopeDialog"
        title="素材访问范围"
        width="min(520px,94vw)"
        ><p class="muted">
          共享：登录用户可读。受限：仅
          ADMIN、上传者及当前有任务授权的用户可读。此修改将记录在素材审计中。
        </p>
        <el-radio-group v-model="nextScope"
          ><el-radio value="LIBRARY">共享</el-radio
          ><el-radio value="RESTRICTED">受限</el-radio></el-radio-group
        ><template #footer
          ><el-button @click="scopeDialog = false">取消</el-button
          ><el-button type="primary" @click="saveScope"
            >保存访问范围</el-button
          ></template
        ></el-dialog
      >
      <el-dialog v-model="edit" title="编辑素材" width="min(520px,94vw)"
        ><el-form label-position="top"
          ><el-form-item label="素材名称" required
            ><el-input
              v-model="form.title"
              aria-label="素材名称"
              maxlength="200" /></el-form-item
          ><el-form-item label="素材描述"
            ><el-input
              v-model="form.description"
              type="textarea"
              aria-label="素材描述"
              maxlength="2000" /></el-form-item
          ><el-form-item label="素材分类"
            ><el-select
              v-model="form.categoryId"
              clearable
              aria-label="素材分类"
              placeholder="未分类"
              ><el-option
                v-if="
                  asset.categoryId &&
                  !choices.some((c) => c.id === asset!.categoryId)
                "
                :value="asset.categoryId"
                :label="
                  (asset.categoryName || '原分类') + '（已停用，保留原分类）'
                "
                disabled /><el-option
                v-for="c in choices"
                :key="c.id"
                :label="c.categoryName"
                :value="c.id" /></el-select></el-form-item></el-form
        ><template #footer
          ><el-button @click="edit = false">取消</el-button
          ><el-button type="primary" :loading="saving" @click="save"
            >保存修改</el-button
          ></template
        ></el-dialog
      ></template
    >
  </div>
</template>
