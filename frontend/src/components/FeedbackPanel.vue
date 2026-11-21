<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import { getData, clean, type Asset } from "../media";
import type { Page } from "../types";
import type { EditingTask } from "../tasks";
import { useAuthStore } from "../stores/auth";
import {
  feedbackStates,
  feedbackTypes,
  feedbackPath,
  feedbackError,
  type Feedback,
  timecode,
} from "../feedback";
import FeedbackComposer from "./FeedbackComposer.vue";
const props = defineProps<{ task: EditingTask; assets: Asset[] }>();
const auth = useAuthStore();
const records = ref<Feedback[]>([]),
  summary = ref({ total: 0, unresolved: 0, resolved: 0, closed: 0 }),
  page = ref(1),
  total = ref(0),
  assetId = ref<number>(),
  type = ref(""),
  status = ref(""),
  direction = ref("DESC"),
  loading = ref(false),
  error = ref(""),
  compose = ref(false);
const canCreate = computed(
  () =>
    props.task.status === "PROCESSING" ||
    (props.task.status === "REVIEWING" &&
      (auth.isAdmin ||
        (auth.user?.roles.includes("REVIEWER") &&
          auth.user.id === props.task.reviewerId))),
);
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const p = await getData<Page<Feedback>>(
      feedbackPath(props.task.id),
      clean({
        page: page.value,
        size: 10,
        assetId: assetId.value,
        type: type.value,
        status: status.value,
        direction: direction.value,
      }),
    );
    records.value = p.records;
    total.value = p.total;
    summary.value = await getData(feedbackPath(props.task.id) + "/summary");
  } catch (e) {
    error.value = feedbackError(e);
    records.value = [];
  } finally {
    loading.value = false;
  }
}
function filter() {
  page.value = 1;
  void load();
}
function created() {
  compose.value = false;
  page.value = 1;
  void load();
}
onMounted(load);
</script>
<template>
  <section class="surface task-section" v-loading="loading">
    <h3>协作反馈</h3>
    <p>
      未解决 {{ summary.unresolved }} 条 · 待确认 {{ summary.resolved }} 条 ·
      已确认 {{ summary.closed }} 条
    </p>
    <div class="feedback-filters">
      <el-select
        v-model="assetId"
        clearable
        aria-label="筛选反馈素材"
        placeholder="全部素材"
        @change="filter"
        ><el-option
          v-for="a in assets"
          :key="a.id"
          :value="a.id"
          :label="a.title" /></el-select
      ><el-select
        v-model="type"
        clearable
        aria-label="筛选反馈类型"
        placeholder="全部类型"
        @change="filter"
        ><el-option
          v-for="(v, k) in feedbackTypes"
          :key="k"
          :label="v"
          :value="k" /></el-select
      ><el-select
        v-model="status"
        clearable
        aria-label="筛选反馈状态"
        placeholder="全部状态"
        @change="filter"
        ><el-option
          v-for="(v, k) in feedbackStates"
          :key="k"
          :label="v"
          :value="k" /></el-select
      ><el-select v-model="direction" aria-label="反馈排序" @change="filter"
        ><el-option label="最新在前" value="DESC" /><el-option
          label="最早在前"
          value="ASC"
      /></el-select>
    </div>
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
    /><el-button @click="load">刷新反馈</el-button
    ><el-button v-if="canCreate" type="primary" @click="compose = true"
      >提出反馈</el-button
    >
    <p v-else class="muted">当前任务阶段下无法提出新反馈。</p>
    <el-table :data="records" empty-text="暂无协作反馈"
      ><el-table-column label="意见" min-width="220"
        ><template #default="{ row }"
          ><router-link
            class="text-link"
            :to="`/tasks/${task.id}/feedback/${row.id}`"
            >{{ row.content || feedbackTypes[row.type] + "附件" }}</router-link
          ></template
        ></el-table-column
      ><el-table-column label="素材 / 时间码" min-width="160"
        ><template #default="{ row }"
          >{{ row.assetTitle || "整个任务" }}
          <router-link
            v-if="row.timecodeMs !== null"
            :to="`/media/${row.assetId}?taskId=${task.id}&timecodeMs=${row.timecodeMs}`"
            >{{ timecode(row.timecodeMs) }}</router-link
          ></template
        ></el-table-column
      ><el-table-column label="类型" width="80"
        ><template #default="{ row }">{{
          feedbackTypes[row.type]
        }}</template></el-table-column
      ><el-table-column label="状态" width="140"
        ><template #default="{ row }">{{
          feedbackStates[row.status]
        }}</template></el-table-column
      ><el-table-column prop="creatorName" label="提出人" /><el-table-column
        prop="createdAt"
        label="提出时间"
        min-width="170" /></el-table
    ><el-pagination
      v-model:current-page="page"
      :page-size="10"
      :total="total"
      layout="total,prev,pager,next"
      @current-change="load"
    /><el-dialog
      v-model="compose"
      title="提出协作反馈"
      width="min(680px,95vw)"
      destroy-on-close
      :close-on-click-modal="false"
      ><FeedbackComposer :task-id="task.id" :assets="assets" @created="created"
    /></el-dialog>
  </section>
</template>
<style scoped>
.feedback-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}
.feedback-filters .el-select {
  width: 180px;
}
</style>
