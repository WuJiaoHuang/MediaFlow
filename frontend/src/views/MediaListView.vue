<script setup lang="ts">
import { ref, reactive, onMounted, computed } from "vue";
import { useAuthStore } from "../stores/auth";
import {
  listAssets,
  type Asset,
  type Filters,
  typeNames,
  formatBytes,
} from "../media";
import MediaFilters from "../components/MediaFilters.vue";
import MediaUpload from "../components/MediaUpload.vue";
const auth = useAuthStore();
const query = reactive<Filters>({
  status: "ACTIVE",
  sort: "createdAt",
  direction: "desc",
});
const rows = ref<Asset[]>([]);
const page = ref(1);
const size = ref(12);
const total = ref(0);
const loading = ref(false);
const error = ref(false);
const upload = ref(false);
const canUpload = computed(
  () => auth.isAdmin || auth.user?.roles.includes("EDITOR"),
);
async function load() {
  loading.value = true;
  error.value = false;
  try {
    const data = await listAssets({
      ...query,
      page: page.value,
      size: size.value,
    });
    rows.value = data.records;
    total.value = data.total;
  } catch {
    error.value = true;
    rows.value = [];
  } finally {
    loading.value = false;
  }
}
function apply() {
  page.value = 1;
  void load();
}
onMounted(load);
</script>
<template>
  <div>
    <p class="eyebrow dark">MEDIA LIBRARY</p>
    <div class="page-heading">
      <div>
        <h1>媒体库</h1>
        <p class="muted">集中管理团队图片、音频与视频，让素材随时可用。</p>
      </div>
      <el-button v-if="canUpload" type="primary" @click="upload = true"
        >上传素材</el-button
      >
    </div>
    <section class="surface">
      <MediaFilters :model="query" @apply="apply" /><el-alert
        v-if="error"
        title="素材加载失败，请重试"
        type="error"
        show-icon
        :closable="false"
      /><el-button v-if="error" @click="load">重新加载</el-button
      ><el-table
        v-else
        v-loading="loading"
        :data="rows"
        empty-text="暂无符合条件的素材"
        ><el-table-column label="素材名称" min-width="220"
          ><template #default="{ row }"
            ><router-link :to="'/media/' + row.id" class="text-link">{{
              row.title
            }}</router-link>
            <div class="muted">{{ row.originalFilename }}</div></template
          ></el-table-column
        ><el-table-column label="类型" width="90"
          ><template #default="{ row }"
            ><el-tag effect="plain">{{
              typeNames[row.mediaType]
            }}</el-tag></template
          ></el-table-column
        ><el-table-column label="分类" min-width="140"
          ><template #default="{ row }">{{
            row.categoryName || "未分类"
          }}</template></el-table-column
        ><el-table-column
          prop="uploaderName"
          label="上传人"
          min-width="120"
        /><el-table-column label="大小" width="110"
          ><template #default="{ row }">{{
            formatBytes(row.fileSize)
          }}</template></el-table-column
        ><el-table-column label="创建时间" min-width="170"
          ><template #default="{ row }">{{
            row.createdAt.replace("T", " ")
          }}</template></el-table-column
        ><el-table-column label="状态" width="90"
          ><template #default="{ row }"
            ><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{
              row.status === "ACTIVE" ? "正常" : "归档"
            }}</el-tag></template
          ></el-table-column
        ><el-table-column label="操作" width="90"
          ><template #default="{ row }"
            ><el-button
              link
              type="primary"
              @click="$router.push('/media/' + row.id)"
              >详情</el-button
            ></template
          ></el-table-column
        ></el-table
      ><el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[12, 24, 48]"
        layout="total,sizes,prev,pager,next"
        @current-change="load"
        @size-change="apply"
      />
    </section>
    <MediaUpload v-model="upload" @uploaded="apply" />
  </div>
</template>
