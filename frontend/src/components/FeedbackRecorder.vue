<script setup lang="ts">
import { ref, onUnmounted, watch } from "vue";
const props = defineProps<{ kind: string }>();
const emit = defineEmits<{ file: [File | null]; recording: [boolean] }>();
const starting = ref(false);
const recording = ref(false),
  error = ref(""),
  url = ref(""),
  seconds = ref(0);
let recorder: MediaRecorder | null = null,
  stream: MediaStream | null = null,
  timer: ReturnType<typeof setInterval> | undefined;
let disposed = false;
let chunks: BlobPart[] = [],
  bytes = 0;
function cleanup() {
  clearInterval(timer);
  stream?.getTracks().forEach((t) => t.stop());
  stream = null;
}
function reset() {
  if (recorder?.state === "recording") recorder.stop();
  cleanup();
  if (url.value) URL.revokeObjectURL(url.value);
  url.value = "";
  seconds.value = 0;
}
function stop() {
  if (recorder?.state === "recording") recorder.stop();
}
async function start() {
  if (starting.value || recording.value) return;
  starting.value = true;
  reset();
  emit("file", null);
  error.value = "";
  try {
    if (
      !navigator.mediaDevices?.getUserMedia ||
      typeof MediaRecorder === "undefined"
    )
      throw new Error("浏览器不支持录制，请选择本地文件上传");
    const video = props.kind === "VIDEO";
    stream = await navigator.mediaDevices.getUserMedia({ audio: true, video });
    if (disposed) {
      cleanup();
      return;
    }
    const options = video
      ? ["video/webm;codecs=vp8,opus", "video/webm", "video/mp4"]
      : ["audio/webm;codecs=opus", "audio/webm", "audio/mp4"];
    const mime = options.find((v) => MediaRecorder.isTypeSupported(v));
    if (!mime) throw new Error("浏览器没有支持的录制格式，请选择文件上传");
    recorder = new MediaRecorder(stream, { mimeType: mime });
    chunks = [];
    bytes = 0;
    recorder.ondataavailable = (e) => {
      if (e.data.size) {
        bytes += e.data.size;
        if (bytes > (video ? 25 : 10) * 1024 * 1024) {
          error.value = "录制超过附件大小上限，请重新录制";
          stop();
          return;
        }
        chunks.push(e.data);
      }
    };
    recorder.onerror = () => {
      error.value = "录制失败，请重新录制或上传文件";
      emit("recording", false);
      cleanup();
      recording.value = false;
    };
    recorder.onstop = () => {
      emit("recording", false);
      recording.value = false;
      cleanup();
      if (error.value || !chunks.length) return;
      const type = mime.split(";")[0]!;
      const blob = new Blob(chunks, { type });
      url.value = URL.createObjectURL(blob);
      emit(
        "file",
        new File(
          [blob],
          `feedback-${Date.now()}.${type.endsWith("mp4") ? (video ? "mp4" : "m4a") : "webm"}`,
          { type },
        ),
      );
      chunks = [];
    };
    recorder.start(250);
    recording.value = true;
    emit("recording", true);
    timer = setInterval(() => {
      seconds.value++;
      if (seconds.value >= (video ? 60 : 120)) stop();
    }, 1000);
  } catch (e: any) {
    cleanup();
    recording.value = false;
    emit("recording", false);
    error.value =
      e.name === "NotAllowedError"
        ? "麦克风或摄像头权限被拒绝，请授权后重试或选择文件上传"
        : e.message || "无法启动录制，请选择文件上传";
  } finally {
    starting.value = false;
  }
}
watch(() => props.kind, reset);
onUnmounted(() => {
  disposed = true;
  if (recorder) {
    recorder.onstop = null;
    recorder.onerror = null;
    if (recorder.state === "recording") recorder.stop();
  }
  cleanup();
  if (url.value) URL.revokeObjectURL(url.value);
});
</script>
<template>
  <div class="recorder">
    <p class="muted">
      {{
        kind === "AUDIO"
          ? "录音最长 120 秒 / 10 MiB"
          : "短视频最长 60 秒 / 25 MiB"
      }}；录制需要安全上下文和设备权限。
    </p>
    <el-button v-if="!recording" :loading="starting" @click="start">{{
      url ? "重新录制" : kind === "AUDIO" ? "开始录音" : "开始录像"
    }}</el-button
    ><el-button v-else type="danger" @click="stop"
      >停止录制（{{ seconds }} 秒）</el-button
    >
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <audio v-if="url && kind === 'AUDIO'" :src="url" controls /><video
      v-if="url && kind === 'VIDEO'"
      :src="url"
      controls
      style="max-width: 100%; max-height: 280px"
    />
  </div>
</template>
