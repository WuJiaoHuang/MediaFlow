<script setup lang="ts">
import { ref, onMounted } from "vue";
import type { Category, Filters } from "../media";
import { listCategories } from "../media";
import { useAuthStore } from "../stores/auth";
const props = defineProps<{ model: Filters }>();
const emit = defineEmits<{ apply: [] }>();
const categories = ref<Category[]>([]);
const dateRange = ref<string[]>([]);
const auth = useAuthStore();
const categoryError = ref(false);
onMounted(async () => {
  try {
    categories.value = await listCategories();
  } catch {
    categoryError.value = true;
  }
});
function apply() {
  props.model.from = dateRange.value?.[0];
  props.model.to = dateRange.value?.[1];
  emit("apply");
}
</script>
<template>
  <div class="media-filters">
    <el-input
      v-model="model.keyword"
      placeholder="搜索素材名称"
      aria-label="搜索素材名称"
      clearable
      @keyup.enter="apply"
    /><el-select
      v-model="model.categoryId"
      placeholder="全部分类"
      aria-label="分类筛选"
      clearable
      ><el-option label="未分类" :value="0" /><el-option
        v-for="c in categories"
        :key="c.id"
        :label="c.categoryName + (c.status === 0 ? '（停用）' : '')"
        :value="c.id" /></el-select
    ><el-select
      v-model="model.mediaType"
      placeholder="全部类型"
      aria-label="类型筛选"
      clearable
      ><el-option label="图片" value="IMAGE" /><el-option
        label="音频"
        value="AUDIO" /><el-option label="视频" value="VIDEO" /></el-select
    ><el-input-number
      v-model="model.uploadedBy"
      :min="1"
      placeholder="上传人 ID"
      aria-label="上传人 ID"
      :controls="false"
    /><el-date-picker
      v-model="dateRange"
      type="datetimerange"
      value-format="YYYY-MM-DDTHH:mm:ss"
      start-placeholder="创建开始时间"
      end-placeholder="创建结束时间"
    /><el-select
      v-if="auth.isAdmin"
      v-model="model.status"
      aria-label="素材状态"
      ><el-option label="正常素材" value="ACTIVE" /><el-option
        label="已归档"
        value="ARCHIVED" /><el-option label="全部状态" value="ALL" /></el-select
    ><el-select v-model="model.sort" aria-label="排序字段"
      ><el-option label="按创建时间" value="createdAt" /><el-option
        label="按名称"
        value="title" /><el-option
        label="按文件大小"
        value="fileSize" /></el-select
    ><el-select v-model="model.direction" aria-label="排序方向"
      ><el-option label="降序" value="desc" /><el-option
        label="升序"
        value="asc" /></el-select
    ><el-button type="primary" @click="apply">筛选</el-button
    ><span v-if="categoryError" role="alert" class="error"
      >分类加载失败，请刷新重试</span
    >
  </div>
</template>
