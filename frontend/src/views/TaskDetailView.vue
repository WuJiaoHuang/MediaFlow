<script setup lang="ts">
import FeedbackPanel from "../components/FeedbackPanel.vue";
import { ref, computed, onMounted } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { useAuthStore } from "../stores/auth";
import { getData, type Asset, typeNames, formatBytes } from "../media";
import {
  taskDetail,
  taskAction,
  statusNames,
  priorityNames,
  taskOperations,
  type EditingTask,
  type TaskLog,
} from "../tasks";
import type { Page } from "../types";
const id = Number(useRoute().params.id);
const auth = useAuthStore();
const task = ref<EditingTask | null>(null);
const media = ref<Asset[]>([]);
const logs = ref<TaskLog[]>([]);
const total = ref(0);
const page = ref(1);
const loading = ref(false);
const busy = ref(false);
const error = ref("");
const actionError = ref("");
const reject = ref(false);
const override = ref(false);
const reason = ref("");
const execute = computed(
  () =>
    task.value &&
    (auth.isAdmin ||
      (auth.user?.roles.includes("EDITOR") &&
        task.value.assigneeId === auth.user.id)),
);
const review = computed(
  () =>
    task.value &&
    (auth.isAdmin ||
      (auth.user?.roles.includes("REVIEWER") &&
        task.value.reviewerId === auth.user.id)),
);
async function loadLogs() {
  const data = await getData<Page<TaskLog>>(`/tasks/${id}/logs`, {
    page: page.value,
    size: 20,
  });
  logs.value = data.records;
  total.value = data.total;
}
async function load() {
  loading.value = true;
  error.value = "";
  actionError.value = "";
  try {
    task.value = await taskDetail(id);
    media.value = await getData<Asset[]>(`/tasks/${id}/assets`);
    await loadLogs();
  } catch (e: any) {
    error.value =
      e.response?.status === 404
        ? "任务不存在或无权访问"
        : "任务加载失败，请重试";
    task.value = null;
    media.value = [];
    logs.value = [];
  } finally {
    loading.value = false;
  }
}
async function action(name: string) {
  if (!task.value || busy.value) return;
  if (name === "reject" && !reason.value.trim()) {
    actionError.value = "审核退回必须填写原因";
    return;
  }
  busy.value = true;
  actionError.value = "";
  try {
    await taskAction(
      task.value,
      name,
      name === "reject" || (name === "submit" && override.value)
        ? reason.value
        : undefined,
    );
    reject.value = false;
    override.value = false;
    reason.value = "";
    ElMessage.success("任务状态已更新");
    await load();
  } catch (e: any) {
    actionError.value = e.response?.data?.message || "操作失败";
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div v-loading="loading">
    <el-button text @click="$router.push('/tasks')">← 返回任务列表</el-button>
    <section v-if="error" class="surface">
      <h1>{{ error }}</h1>
      <p class="muted">无授权的任务统一隐藏其详情和历史。</p>
      <el-button @click="load">重新加载任务</el-button>
    </section>
    <template v-if="task"
      ><div class="page-heading">
        <div>
          <p class="eyebrow dark">TASK #{{ task.id }}</p>
          <h1>{{ task.title }}</h1>
          <p class="muted">
            {{ priorityNames[task.priority] }}优先级 · 创建人
            {{ task.creatorName }} · 版本 {{ task.version }}
          </p>
        </div>
        <el-button
          v-if="auth.isAdmin && task.status !== 'COMPLETED'"
          @click="$router.push('/tasks/' + id + '/edit')"
          >编辑任务</el-button
        >
      </div>
      <section class="surface task-summary">
        <div class="task-state-flow">
          <el-tag
            v-for="state in ['PENDING', 'PROCESSING', 'REVIEWING', 'COMPLETED']"
            :key="state"
            :type="task.status === state ? 'primary' : 'info'"
            :effect="task.status === state ? 'dark' : 'plain'"
            >{{ statusNames[state] }}</el-tag
          >
        </div>
        <el-descriptions :column="2" border
          ><el-descriptions-item label="当前状态"
            ><strong>{{
              statusNames[task.status]
            }}</strong></el-descriptions-item
          ><el-descriptions-item label="截止时间">{{
            task.dueAt?.replace("T", " ") || "未设置"
          }}</el-descriptions-item
          ><el-descriptions-item label="执行人"
            >{{ task.assigneeName }} (#{{
              task.assigneeId
            }})</el-descriptions-item
          ><el-descriptions-item label="审核人"
            >{{ task.reviewerName }} (#{{
              task.reviewerId
            }})</el-descriptions-item
          ><el-descriptions-item label="任务要求" :span="2"
            ><span class="preserve-text">{{
              task.description
            }}</span></el-descriptions-item
          ><el-descriptions-item label="创建时间">{{
            task.createdAt.replace("T", " ")
          }}</el-descriptions-item
          ><el-descriptions-item label="更新时间">{{
            task.updatedAt.replace("T", " ")
          }}</el-descriptions-item></el-descriptions
        >
        <div class="task-actions">
          <el-button
            v-if="execute && task.status === 'PENDING'"
            type="primary"
            :loading="busy"
            @click="action('start')"
            >开始处理</el-button
          ><el-button
            v-if="execute && task.status === 'PROCESSING'"
            type="primary"
            :loading="busy"
            @click="action('submit')"
            >提交审核</el-button
          ><el-button
            v-if="auth.isAdmin && task.status === 'PROCESSING'"
            @click="
              reason = '';
              override = true;
            "
            >管理员例外提交</el-button
          ><template v-if="review && task.status === 'REVIEWING'"
            ><el-button
              type="success"
              :loading="busy"
              @click="action('approve')"
              >审核通过</el-button
            ><el-button
              type="warning"
              :disabled="busy"
              @click="
                reason = '';
                actionError = '';
                reject = true;
              "
              >审核退回</el-button
            ></template
          ><span v-if="task.status === 'COMPLETED'" class="muted"
            >任务已完成，不能继续修改或重开。</span
          ><span
            v-if="auth.isAdmin && task.status !== 'COMPLETED'"
            class="muted"
            >管理员代办也遵循相同的状态转换规则。</span
          >
        </div>
        <el-alert
          v-if="actionError"
          :title="actionError"
          type="error"
          :closable="false"
        /><el-button v-if="actionError" @click="load">重新加载任务</el-button>
      </section>
      <section class="surface task-section">
        <h3>关联素材</h3>
        <p class="muted">
          预览和下载携带任务上下文；重新分配后会重新校验任务授权。共享素材仍具有独立媒体库授权。
        </p>
        <el-table
          :data="media"
          empty-text="暂无可访问的正常素材，归档素材仅管理员可见"
          ><el-table-column
            prop="title"
            label="素材名称"
            min-width="180"
          /><el-table-column label="类型" width="90"
            ><template #default="{ row }">{{
              typeNames[row.mediaType]
            }}</template></el-table-column
          ><el-table-column label="分类" min-width="120"
            ><template #default="{ row }">{{
              row.categoryName || "未分类"
            }}</template></el-table-column
          ><el-table-column label="大小" width="110"
            ><template #default="{ row }">{{
              formatBytes(row.fileSize)
            }}</template></el-table-column
          ><el-table-column label="访问范围" width="110"
            ><template #default="{ row }"
              ><el-tag
                :type="row.accessScope === 'RESTRICTED' ? 'warning' : 'info'"
                >{{
                  row.accessScope === "RESTRICTED" ? "受限" : "共享"
                }}</el-tag
              ></template
            ></el-table-column
          ><el-table-column label="预览与下载" width="160"
            ><template #default="{ row }"
              ><router-link
                :to="'/media/' + row.id + '?taskId=' + id"
                class="text-link"
                >查看素材 / 下载</router-link
              ></template
            ></el-table-column
          ></el-table
        >
      </section>
      <FeedbackPanel :key="task.version" :task="task" :assets="media" />
      <section class="surface task-section">
        <h3>任务操作历史</h3>
        <el-table :data="logs" empty-text="暂无操作历史"
          ><el-table-column label="操作" width="150"
            ><template #default="{ row }">{{
              taskOperations[row.operationType] || row.operationType
            }}</template></el-table-column
          ><el-table-column label="操作人" min-width="140"
            ><template #default="{ row }"
              >{{ row.operatorName }} (#{{ row.operatorId }})</template
            ></el-table-column
          ><el-table-column label="修改前" min-width="180"
            ><template #default="{ row }"
              ><span class="audit-value">{{
                row.oldValue ?? "—"
              }}</span></template
            ></el-table-column
          ><el-table-column label="修改后 / 原因" min-width="220"
            ><template #default="{ row }"
              ><span class="audit-value">{{ row.newValue ?? "—" }}</span>
              <p v-if="row.reason" class="reject-reason">
                {{
                  row.operationType === "REJECT" ? "退回原因" : "操作原因"
                }}：{{ row.reason }}
              </p></template
            ></el-table-column
          ><el-table-column label="时间" min-width="170"
            ><template #default="{ row }">{{
              row.createdAt.replace("T", " ")
            }}</template></el-table-column
          ></el-table
        ><el-pagination
          v-model:current-page="page"
          :page-size="20"
          :total="total"
          layout="total,prev,pager,next"
          @current-change="loadLogs"
        />
      </section>
      <el-dialog
        v-model="override"
        title="管理员例外提交"
        width="min(520px,94vw)"
        ><p>
          仅允许显式说明原因后跳过未解决反馈的提交检查；审批仍要求全部确认。
        </p>
        <el-input
          v-model="reason"
          type="textarea"
          aria-label="例外提交原因"
          maxlength="1800"
        />
        <p v-if="actionError" class="error">{{ actionError }}</p>
        <template #footer
          ><el-button @click="override = false">取消</el-button
          ><el-button :loading="busy" type="warning" @click="action('submit')"
            >确认例外提交</el-button
          ></template
        ></el-dialog
      >
      <el-dialog v-model="reject" title="审核退回" width="min(520px,94vw)"
        ><el-form label-position="top" @submit.prevent="action('reject')"
          ><el-form-item label="退回原因" required
            ><el-input
              v-model="reason"
              type="textarea"
              aria-label="退回原因"
              :rows="4"
              maxlength="2000"
              show-word-limit
          /></el-form-item>
          <p v-if="actionError" role="alert" class="error">
            {{ actionError }}
          </p></el-form
        ><template #footer
          ><el-button @click="reject = false">取消</el-button
          ><el-button type="warning" :loading="busy" @click="action('reject')"
            >确认退回</el-button
          ></template
        ></el-dialog
      ></template
    >
  </div>
</template>
