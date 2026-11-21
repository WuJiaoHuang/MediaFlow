<script setup lang="ts">
import { ref, onUnmounted, watch } from "vue";
import { api } from "../api";
import type { Asset } from "../media";
import { feedbackPath, feedbackError, feedbackTypes } from "../feedback";
import FeedbackRecorder from "./FeedbackRecorder.vue";
const props = defineProps<{ taskId: number; assets: Asset[] }>();
const emit = defineEmits<{ created: [] }>();
const recording = ref(false);
const kind = ref("TEXT"),
  content = ref(""),
  assetId = ref<number>(),
  timecodeMs = ref(""),
  file = ref<File | null>(null),
  busy = ref(false),
  progress = ref(0),
  error = ref("");
let key = crypto.randomUUID(),
  controller: AbortController | undefined;
watch([content, assetId, timecodeMs, file], () => {
  key = crypto.randomUUID();
});
watch(kind, () => {
  file.value = null;
  error.value = "";
  key = crypto.randomUUID();
});
function select(e: Event) {
  file.value = (e.target as HTMLInputElement).files?.[0] || null;
}
async function submit() {
  if (busy.value || recording.value) return;
  error.value = "";
  if (kind.value === "TEXT" && !content.value.trim()) {
    error.value = "请填写文字意见";
    return;
  }
  if (kind.value !== "TEXT" && !file.value) {
    error.value = "请录制或选择附件";
    return;
  }
  const ms = timecodeMs.value === "" ? undefined : Number(timecodeMs.value);
  if (ms !== undefined && (!Number.isSafeInteger(ms) || ms < 0)) {
    error.value = "时间码必须是非负整数毫秒";
    return;
  }
  busy.value = true;
  progress.value = 0;
  controller = new AbortController();
  try {
    let payload: object;
    if (kind.value === "TEXT")
      payload = {
        type: kind.value,
        content: content.value,
        assetId: assetId.value,
        timecodeMs: ms,
      };
    else {
      const form = new FormData();
      form.set("type", kind.value);
      form.set("content", content.value);
      if (assetId.value) form.set("assetId", String(assetId.value));
      if (ms !== undefined) form.set("timecodeMs", String(ms));
      form.set("file", file.value!);
      payload = form;
    }
    await api.post(feedbackPath(props.taskId), payload, {
      headers: { "Idempotency-Key": key },
      timeout: 120000,
      signal: controller.signal,
      onUploadProgress: (e) => {
        progress.value = Math.min(99, Math.round((e.progress || 0) * 100));
      },
    });
    progress.value = 100;
    key = crypto.randomUUID();
    content.value = "";
    file.value = null;
    emit("created");
  } catch (e) {
    error.value = feedbackError(e);
  } finally {
    busy.value = false;
  }
}
onUnmounted(() => controller?.abort());
</script>
<template>
  <el-form label-position="top" @submit.prevent="submit"
    ><el-form-item label="反馈类型"
      ><el-select v-model="kind" aria-label="反馈类型" :disabled="busy"
        ><el-option
          v-for="(label, value) in feedbackTypes"
          :key="value"
          :label="label"
          :value="value" /></el-select></el-form-item
    ><el-form-item label="关联素材"
      ><el-select
        v-model="assetId"
        aria-label="反馈关联素材"
        clearable
        :disabled="busy"
        @change="timecodeMs = ''"
        placeholder="整个任务"
        ><el-option
          v-for="a in assets"
          :key="a.id"
          :label="a.title"
          :value="a.id" /></el-select></el-form-item
    ><el-form-item
      v-if="
        assets.some(
          (a) => a.id === assetId && ['AUDIO', 'VIDEO'].includes(a.mediaType),
        )
      "
      label="时间码（毫秒，可选）"
      ><el-input
        v-model="timecodeMs"
        aria-label="时间码毫秒"
        :disabled="busy"
        type="number"
        min="0"
        step="1" /></el-form-item
    ><el-form-item
      :label="kind === 'TEXT' ? '文字意见' : '附加文字说明'"
      :required="kind === 'TEXT'"
      ><el-input
        v-model="content"
        aria-label="反馈内容"
        type="textarea"
        :rows="4"
        maxlength="4000"
        show-word-limit
        :disabled="busy" /></el-form-item
    ><template v-if="kind !== 'TEXT'"
      ><FeedbackRecorder
        v-if="!busy"
        :kind="kind"
        @file="file = $event"
        @recording="recording = $event"
      />
      <p class="muted">
        也可选择本地文件。音频 MP3 / WAV / M4A / WebM，最大 10 MiB；视频 MP4 /
        WebM，最大 25 MiB。
      </p>
      <input
        type="file"
        aria-label="反馈附件"
        :disabled="busy"
        :accept="kind === 'AUDIO' ? '.mp3,.wav,.m4a,.webm' : '.mp4,.webm'"
        @change="select"
      />
      <p v-if="file">已选择：{{ file.name }}（{{ file.size }} 字节）</p>
      <el-progress v-if="busy" :percentage="progress" /><el-button
        v-if="busy"
        @click="controller?.abort()"
        >取消上传</el-button
      ></template
    >
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <el-button
      type="primary"
      :loading="busy"
      :disabled="recording"
      @click="submit"
      >提交反馈</el-button
    ></el-form
  >
</template>
