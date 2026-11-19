<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { getData, type Change } from "../media";
import type { Page } from "../types";
import ChangeTable from "../components/ChangeTable.vue";
const q = reactive({
  assetId: undefined as number | undefined,
  operatorId: undefined as number | undefined,
});
const dates = ref<string[]>([]);
const page = ref(1);
const total = ref(0);
const rows = ref<Change[]>([]);
const loading = ref(false);
const error = ref(false);
async function load() {
  loading.value = true;
  error.value = false;
  try {
    const p = await getData<Page<Change>>("/media/changes", {
      ...q,
      page: page.value,
      size: 20,
      from: dates.value?.[0],
      to: dates.value?.[1],
    });
    rows.value = p.records;
    total.value = p.total;
  } catch {
    error.value = true;
    rows.value = [];
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div>
    <p class="eyebrow dark">CHANGE HISTORY</p>
    <div class="page-heading">
      <div>
        <h1>修改记录</h1>
        <p class="muted">
          每一处变更均可追溯。普通用户只显示可访问的正常素材历史。
        </p>
      </div>
    </div>
    <section class="surface">
      <div class="media-filters">
        <el-input-number
          v-model="q.assetId"
          :min="1"
          placeholder="素材 ID"
          aria-label="素材 ID"
          :controls="false"
        /><el-input-number
          v-model="q.operatorId"
          :min="1"
          placeholder="操作人 ID"
          aria-label="操作人 ID"
          :controls="false"
        /><el-date-picker
          v-model="dates"
          type="datetimerange"
          value-format="YYYY-MM-DDTHH:mm:ss"
          start-placeholder="修改开始时间"
          end-placeholder="修改结束时间"
        /><el-button
          type="primary"
          @click="
            page = 1;
            load();
          "
          >查询记录</el-button
        >
      </div>
      <el-alert
        v-if="error"
        title="修改记录加载失败"
        type="error"
        :closable="false"
      /><ChangeTable v-else v-loading="loading" :rows="rows" /><el-pagination
        v-model:current-page="page"
        :total="total"
        :page-size="20"
        layout="total,prev,pager,next"
        @current-change="load"
      />
    </section>
  </div>
</template>
