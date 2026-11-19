<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { getData, type Stats, type Filters, formatBytes } from "../media";
import MediaFilters from "../components/MediaFilters.vue";
withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });
const data = ref<Stats | null>(null);
const error = ref(false);
const loading = ref(false);
const query = reactive<Filters>({
  status: "ACTIVE",
  sort: "createdAt",
  direction: "desc",
});
async function load() {
  loading.value = true;
  error.value = false;
  try {
    data.value = await getData<Stats>("/media/stats", query);
  } catch {
    data.value = null;
    error.value = true;
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div v-loading="loading">
    <div v-if="!embedded" class="page-heading">
      <div>
        <p class="eyebrow dark">MEDIA INSIGHTS</p>
        <h1>媒体统计</h1>
        <p class="muted">
          与媒体列表使用相同过滤条件；默认仅统计正常素材，最近新增指最近 7 天。
        </p>
      </div>
    </div>
    <section v-if="!embedded" class="surface">
      <MediaFilters :model="query" @apply="load" />
    </section>
    <el-alert
      v-if="error"
      title="统计加载失败，未展示统计数值"
      type="error"
      :closable="false"
    /><el-button v-if="error" @click="load">重新加载统计</el-button
    ><template v-if="data"
      ><div class="stats-grid">
        <div
          v-for="item in [
            { label: '素材总数', value: data.total },
            { label: '视频素材', value: data.videos },
            { label: '音频素材', value: data.audios },
            { label: '图片素材', value: data.images },
            { label: '最近 7 天新增', value: data.recent },
          ]"
          :key="item.label"
          class="surface stat-card"
        >
          <p class="muted">{{ item.label }}</p>
          <strong>{{ item.value }}</strong>
        </div>
      </div>
      <section class="surface category-chart">
        <div class="table-toolbar">
          <h3>分类分布</h3>
          <span class="muted">文件总量 {{ formatBytes(data.totalBytes) }}</span>
        </div>
        <div
          v-for="c in data.categories"
          :key="c.categoryId ?? 'none'"
          class="chart-row"
        >
          <span>{{ c.categoryName }}</span>
          <div class="chart-track">
            <div
              :style="{
                width: (data.total ? (c.count * 100) / data.total : 0) + '%',
              }"
            ></div>
          </div>
          <strong>{{ c.count }}</strong>
        </div>
        <el-empty
          v-if="!data.total"
          description="当前范围暂无媒体素材"
          :image-size="70"
        /></section
    ></template>
  </div>
</template>
