<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from "vue";
import { useRoute } from "vue-router";
import { api } from "../api";
import { getData } from "../media";
import { taskDetail, type EditingTask } from "../tasks";
import { useAuthStore } from "../stores/auth";
import type { Page } from "../types";
import {
  feedbackPath,
  feedbackStates,
  feedbackTypes,
  feedbackAction,
  feedbackError,
  timecode,
  type FeedbackDetail,
  type Reply,
  type FeedbackLog,
} from "../feedback";
const route = useRoute(),
  taskId = Number(route.params.id),
  id = Number(route.params.feedbackId),
  auth = useAuthStore();
const task = ref<EditingTask | null>(null),
  detail = ref<FeedbackDetail | null>(null),
  replies = ref<Reply[]>([]),
  logs = ref<FeedbackLog[]>([]),
  replyPage = ref(1),
  logPage = ref(1),
  replyTotal = ref(0),
  logTotal = ref(0),
  content = ref(""),
  reason = ref(""),
  reopen = ref(false),
  loading = ref(false),
  busy = ref(false),
  error = ref(""),
  actionError = ref(""),
  access = ref<{ contentUrl: string; downloadUrl: string } | null>(null),
  playError = ref("");
let timer: ReturnType<typeof setInterval> | undefined;
const writable = computed(
  () => task.value && ["PROCESSING", "REVIEWING"].includes(task.value.status),
);
const execute = computed(
  () =>
    task.value &&
    (auth.isAdmin ||
      (auth.user?.roles.includes("EDITOR") &&
        auth.user.id === task.value.assigneeId)),
);
const confirm = computed(
  () =>
    task.value &&
    detail.value &&
    (auth.isAdmin ||
      auth.user?.id === detail.value.feedback.creatorId ||
      (auth.user?.roles.includes("REVIEWER") &&
        auth.user.id === task.value.reviewerId)),
);
async function renew() {
  if (!detail.value?.attachment) return;
  try {
    access.value = (
      await api.post(
        feedbackPath(taskId, id) +
          `/attachments/${detail.value.attachment.id}/access`,
      )
    ).data.data;
    playError.value = "";
  } catch (e) {
    access.value = null;
    playError.value = feedbackError(e);
  }
}
async function loadReplies() {
  const p = await getData<Page<Reply>>(feedbackPath(taskId, id) + "/replies", {
    page: replyPage.value,
    size: 20,
  });
  replies.value = p.records;
  replyTotal.value = p.total;
}
async function loadLogs() {
  const p = await getData<Page<FeedbackLog>>(
    feedbackPath(taskId, id) + "/logs",
    { page: logPage.value, size: 20 },
  );
  logs.value = p.records;
  logTotal.value = p.total;
}
async function load() {
  loading.value = true;
  error.value = "";
  actionError.value = "";
  try {
    task.value = await taskDetail(taskId);
    detail.value = await getData(feedbackPath(taskId, id));
    await loadReplies();
    await loadLogs();
    await renew();
  } catch (e) {
    error.value = feedbackError(e);
    detail.value = null;
    access.value = null;
  } finally {
    loading.value = false;
  }
}
async function action(name: string) {
  if (!detail.value || busy.value) return;
  if (name === "reopen" && !reason.value.trim()) {
    actionError.value = "重新打开必须填写原因";
    return;
  }
  busy.value = true;
  actionError.value = "";
  try {
    await feedbackAction(taskId, detail.value.feedback, name, reason.value);
    reopen.value = false;
    reason.value = "";
    await load();
  } catch (e) {
    actionError.value = feedbackError(e);
  } finally {
    busy.value = false;
  }
}
async function reply() {
  if (!detail.value || busy.value) return;
  if (!content.value.trim()) {
    actionError.value = "请填写回复内容";
    return;
  }
  busy.value = true;
  actionError.value = "";
  try {
    await api.post(feedbackPath(taskId, id) + "/replies", {
      version: detail.value.feedback.version,
      content: content.value,
    });
    content.value = "";
    await load();
  } catch (e) {
    actionError.value = feedbackError(e);
  } finally {
    busy.value = false;
  }
}
onMounted(() => {
  void load();
  timer = setInterval(() => {
    void renew();
  }, 90000);
});
onUnmounted(() => clearInterval(timer));
</script>
<template>
  <div v-loading="loading">
    <el-button text @click="$router.push('/tasks/' + taskId)"
      >← 返回任务详情</el-button
    ><el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
    /><el-button @click="load">重新加载反馈</el-button
    ><template v-if="detail && task"
      ><section class="surface task-section">
        <h1>{{ feedbackTypes[detail.feedback.type] }}反馈 #{{ id }}</h1>
        <p>
          {{ detail.feedback.creatorName }} ·
          {{ detail.feedback.createdAt.replace("T", " ") }} · 版本
          {{ detail.feedback.version }}
        </p>
        <p>
          关联素材：{{ detail.feedback.assetTitle || "整个任务" }}
          <router-link
            v-if="detail.feedback.timecodeMs !== null"
            class="text-link"
            :to="`/media/${detail.feedback.assetId}?taskId=${taskId}&timecodeMs=${detail.feedback.timecodeMs}`"
            >跳至 {{ timecode(detail.feedback.timecodeMs) }}</router-link
          >
        </p>
        <el-tag>{{ feedbackStates[detail.feedback.status] }}</el-tag>
        <p class="preserve-text">{{ detail.feedback.content }}</p>
        <template v-if="detail.attachment"
          ><p>{{ detail.attachment.originalFilename }}</p>
          <audio
            v-if="access && detail.feedback.type === 'AUDIO'"
            :src="access.contentUrl"
            controls
            preload="metadata"
            @error="playError = '附件播放失败，请重新获取播放权限'"
          /><video
            v-if="access && detail.feedback.type === 'VIDEO'"
            :src="access.contentUrl"
            controls
            preload="metadata"
            style="max-width: 100%; max-height: 420px"
            @error="playError = '附件播放失败，请重新获取播放权限'"
          />
          <p v-if="playError" class="error" role="alert">{{ playError }}</p>
          <p>
            <a v-if="access" :href="access.downloadUrl" class="text-link"
              >下载反馈附件</a
            >
            <el-button @click="renew">重新获取播放权限</el-button>
          </p></template
        >
        <div v-if="writable" class="task-actions">
          <el-button
            v-if="
              execute && ['OPEN', 'REOPENED'].includes(detail.feedback.status)
            "
            type="primary"
            :loading="busy"
            @click="action('resolve')"
            >标记已处理</el-button
          ><el-button
            v-if="confirm && detail.feedback.status === 'RESOLVED'"
            type="success"
            :loading="busy"
            @click="action('close')"
            >确认解决</el-button
          ><el-button
            v-if="
              confirm && ['RESOLVED', 'CLOSED'].includes(detail.feedback.status)
            "
            type="warning"
            @click="reopen = true"
            >重新打开</el-button
          >
        </div>
        <p v-else class="muted">当前任务阶段反馈只读。</p>
        <el-alert
          v-if="actionError"
          :title="actionError"
          type="error"
          :closable="false"
        />
      </section>
      <section class="surface task-section">
        <h3>回复记录</h3>
        <p v-if="!replies.length" class="muted">暂无回复</p>
        <article v-for="r in replies" :key="r.id">
          <p>{{ r.creatorName }} · {{ r.createdAt.replace("T", " ") }}</p>
          <p class="preserve-text">{{ r.content }}</p>
        </article>
        <el-pagination
          v-model:current-page="replyPage"
          :page-size="20"
          :total="replyTotal"
          layout="total,prev,pager,next"
          @current-change="loadReplies"
        /><el-form
          v-if="writable && detail.feedback.status !== 'CLOSED'"
          label-position="top"
          @submit.prevent="reply"
          ><el-form-item label="回复意见"
            ><el-input
              v-model="content"
              aria-label="回复内容"
              type="textarea"
              maxlength="4000"
              show-word-limit
              :rows="3"
              :disabled="busy" /></el-form-item
          ><el-button type="primary" :loading="busy" @click="reply"
            >发送回复</el-button
          ></el-form
        >
      </section>
      <section class="surface task-section">
        <h3>完整处理历史</h3>
        <el-table :data="logs"
          ><el-table-column
            prop="operatorName"
            label="操作人" /><el-table-column
            prop="operationType"
            label="操作" /><el-table-column label="原状态"
            ><template #default="{ row }">{{
              feedbackStates[row.oldStatus] || "—"
            }}</template></el-table-column
          ><el-table-column label="新状态"
            ><template #default="{ row }">{{
              feedbackStates[row.newStatus]
            }}</template></el-table-column
          ><el-table-column
            prop="reason"
            label="原因 / 记录"
            min-width="180" /><el-table-column
            prop="createdAt"
            label="时间"
            min-width="180" /></el-table
        ><el-pagination
          v-model:current-page="logPage"
          :page-size="20"
          :total="logTotal"
          layout="total,prev,pager,next"
          @current-change="loadLogs"
        /></section></template
    ><el-dialog v-model="reopen" title="重新打开反馈" width="min(520px,94vw)"
      ><el-form label-position="top"
        ><el-form-item label="重新打开原因" required
          ><el-input
            v-model="reason"
            aria-label="重新打开原因"
            type="textarea"
            maxlength="2000"
            :rows="4"
        /></el-form-item>
        <p v-if="actionError" class="error" role="alert">
          {{ actionError }}
        </p></el-form
      ><template #footer
        ><el-button @click="reopen = false">取消</el-button
        ><el-button type="warning" :loading="busy" @click="action('reopen')"
          >确认重新打开</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
