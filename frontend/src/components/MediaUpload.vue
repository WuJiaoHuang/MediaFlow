<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import { ElMessage } from "element-plus";
import { api } from "../api";
import type { ApiResponse } from "../types";
import {
  enabledCategories,
  listCategories,
  type Category,
  type Asset,
} from "../media";
const visible = defineModel<boolean>({ required: true });
const emit = defineEmits<{ uploaded: [asset: Asset] }>();
const form = reactive({
  title: "",
  description: "",
  categoryId: undefined as number | undefined,
});
const file = ref<File | null>(null);
const categories = ref<Category[]>([]);
const saving = ref(false);
const progress = ref(0);
const error = ref("");
const requestKey = ref("");
const choices = computed(() => enabledCategories(categories.value));
watch(visible, async (value) => {
  if (value) {
    Object.assign(form, { title: "", description: "", categoryId: undefined });
    file.value = null;
    error.value = "";
    progress.value = 0;
    requestKey.value = crypto.randomUUID();
    try {
      categories.value = await listCategories();
    } catch {
      error.value = "分类加载失败，请重试";
    }
  }
});
function select(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0] || null;
  if (file.value && !form.title)
    form.title = file.value.name.replace(/\.[^.]+$/, "");
}
async function submit() {
  if (saving.value) return;
  if (!file.value || !form.title.trim()) {
    error.value = "请选择文件并填写素材名称";
    return;
  }
  if (file.value.size > 100 * 1024 * 1024) {
    error.value = "单个文件不能超过 100 MB";
    return;
  }
  if (!/\.(jpe?g|png|webp|mp3|wav|m4a|mp4|webm)$/i.test(file.value.name)) {
    error.value = "文件格式不受支持";
    return;
  }
  saving.value = true;
  error.value = "";
  try {
    const data = new FormData();
    data.append("file", file.value);
    data.append("title", form.title);
    data.append("description", form.description);
    if (form.categoryId) data.append("categoryId", String(form.categoryId));
    const response = await api.post<ApiResponse<Asset>>("/media/assets", data, {
      headers: { "Idempotency-Key": requestKey.value },
      timeout: 120000,
      onUploadProgress: (event) => {
        progress.value = event.total
          ? Math.round((event.loaded * 100) / event.total)
          : 0;
      },
    });
    ElMessage.success("素材上传成功");
    visible.value = false;
    emit("uploaded", response.data.data);
  } catch {
    error.value = "上传失败，请检查提示后重试；同一请求不会重复创建素材";
  } finally {
    saving.value = false;
  }
}
</script>
<template>
  <el-dialog
    v-model="visible"
    title="上传媒体素材"
    width="min(560px,94vw)"
    :close-on-click-modal="!saving"
    :show-close="!saving"
    ><el-form label-position="top" @submit.prevent="submit"
      ><el-form-item label="媒体文件" required
        ><input
          v-if="visible"
          type="file"
          aria-label="媒体文件"
          accept=".jpg,.jpeg,.png,.webp,.mp3,.wav,.m4a,.mp4,.webm"
          :disabled="saving"
          @change="select"
        />
        <p class="muted upload-hint">
          图片 JPEG / PNG / WebP · 音频 MP3 / WAV / M4A · 视频 MP4 / WebM<br />单个文件最大
          100 MB，服务端会校验真实内容。
        </p></el-form-item
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
          maxlength="2000"
          :rows="3" /></el-form-item
      ><el-form-item label="素材分类"
        ><el-select
          v-model="form.categoryId"
          aria-label="素材分类"
          clearable
          placeholder="未分类"
          ><el-option
            v-for="c in choices"
            :key="c.id"
            :label="c.categoryName"
            :value="c.id" /></el-select></el-form-item
      ><el-progress v-if="saving" :percentage="progress" />
      <p v-if="error" class="error" role="alert">{{ error }}</p></el-form
    ><template #footer
      ><el-button :disabled="saving" @click="visible = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="submit"
        >确认上传</el-button
      ></template
    ></el-dialog
  >
</template>
