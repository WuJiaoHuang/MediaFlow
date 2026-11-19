<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { useAuthStore } from "../stores/auth";
import {
  taskList,
  statusNames,
  priorityNames,
  type EditingTask,
} from "../tasks";
const auth = useAuthStore();
const rows = ref<EditingTask[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(12);
const loading = ref(false);
const error = ref(false);
const q = reactive({
  keyword: "",
  status: undefined as string | undefined,
  priority: undefined as string | undefined,
  assigneeId: undefined as number | undefined,
  reviewerId: undefined as number | undefined,
  sort: "createdAt",
  direction: "desc",
  mine: false,
});
async function load() {
  loading.value = true;
  error.value = false;
  try {
    const data = await taskList({ ...q, page: page.value, size: size.value });
    rows.value = data.records;
    total.value = data.total;
  } catch {
    error.value = true;
    rows.value = [];
  } finally {
    loading.value = false;
  }
}
function search() {
  page.value = 1;
  void load();
}
onMounted(load);
</script>
<template>
  <div>
    <p class="eyebrow dark">EDITING WORKFLOW</p>
    <div class="page-heading">
      <div>
        <h1>剪辑任务</h1>
        <p class="muted">
          查看当前分配、处理进度与审核结果。任务范围由服务端实时授权。
        </p>
      </div>
      <el-button
        v-if="auth.isAdmin"
        type="primary"
        @click="$router.push('/tasks/new')"
        >创建任务</el-button
      >
    </div>
    <section class="surface">
      <div v-if="auth.isAdmin" class="task-tabs">
        <el-radio-group v-model="q.mine" @change="search"
          ><el-radio-button :value="false">全部任务</el-radio-button
          ><el-radio-button :value="true"
            >我的任务</el-radio-button
          ></el-radio-group
        >
      </div>
      <p v-else class="muted">我的任务 · 当前执行人或审核人授权的并集</p>
      <div class="media-filters">
        <el-input
          v-model="q.keyword"
          placeholder="搜索任务标题"
          aria-label="搜索任务标题"
          clearable
          @keyup.enter="search"
        /><el-select
          v-model="q.status"
          placeholder="全部状态"
          aria-label="任务状态"
          clearable
          ><el-option
            v-for="(name, value) in statusNames"
            :key="value"
            :label="name"
            :value="value" /></el-select
        ><el-select
          v-model="q.priority"
          placeholder="全部优先级"
          aria-label="任务优先级"
          clearable
          ><el-option
            v-for="(name, value) in priorityNames"
            :key="value"
            :label="name"
            :value="value" /></el-select
        ><el-input-number
          v-model="q.assigneeId"
          placeholder="执行人 ID"
          aria-label="执行人 ID"
          :min="1"
          :controls="false"
        /><el-input-number
          v-model="q.reviewerId"
          placeholder="审核人 ID"
          aria-label="审核人 ID"
          :min="1"
          :controls="false"
        /><el-select v-model="q.sort" aria-label="任务排序"
          ><el-option label="创建时间" value="createdAt" /><el-option
            label="截止时间"
            value="dueAt" /><el-option
            label="优先级"
            value="priority" /></el-select
        ><el-select v-model="q.direction" aria-label="任务排序方向"
          ><el-option label="降序" value="desc" /><el-option
            label="升序"
            value="asc" /></el-select
        ><el-button type="primary" @click="search">筛选任务</el-button>
      </div>
      <el-alert
        v-if="error"
        title="任务列表加载失败，请重试"
        type="error"
        :closable="false"
      /><el-button v-if="error" @click="load">重试</el-button
      ><el-table
        v-else
        v-loading="loading"
        :data="rows"
        empty-text="当前没有符合条件的任务"
        ><el-table-column label="任务" min-width="230"
          ><template #default="{ row }"
            ><router-link :to="'/tasks/' + row.id" class="text-link"
              >#{{ row.id }} {{ row.title }}</router-link
            ></template
          ></el-table-column
        ><el-table-column label="状态" width="100"
          ><template #default="{ row }"
            ><el-tag
              :type="
                row.status === 'COMPLETED'
                  ? 'success'
                  : row.status === 'REVIEWING'
                    ? 'warning'
                    : 'primary'
              "
              >{{ statusNames[row.status] }}</el-tag
            ></template
          ></el-table-column
        ><el-table-column label="优先级" width="100"
          ><template #default="{ row }">{{
            priorityNames[row.priority]
          }}</template></el-table-column
        ><el-table-column
          prop="assigneeName"
          label="执行人"
          min-width="120"
        /><el-table-column
          prop="reviewerName"
          label="审核人"
          min-width="120"
        /><el-table-column label="截止时间" min-width="170"
          ><template #default="{ row }">{{
            row.dueAt?.replace("T", " ") || "未设置"
          }}</template></el-table-column
        ><el-table-column label="操作" width="120"
          ><template #default="{ row }"
            ><el-button
              link
              type="primary"
              @click="$router.push('/tasks/' + row.id)"
              >查看任务</el-button
            ></template
          ></el-table-column
        ></el-table
      ><el-pagination
        v-if="!error"
        v-model:current-page="page"
        v-model:page-size="size"
        :page-sizes="[12, 24, 48]"
        :total="total"
        layout="total,sizes,prev,pager,next"
        @current-change="load"
        @size-change="search"
      />
    </section>
  </div>
</template>
