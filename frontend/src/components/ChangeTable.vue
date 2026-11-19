<script setup lang="ts">
import type { Change } from "../media";
import { fieldNames, operationNames } from "../media";
defineProps<{ rows: Change[] }>();
</script>
<template>
  <el-table :data="rows" empty-text="暂无修改记录"
    ><el-table-column prop="id" label="记录 ID" width="90" /><el-table-column
      label="素材"
      min-width="160"
      ><template #default="{ row }"
        ><router-link :to="'/media/' + row.assetId" class="text-link"
          >#{{ row.assetId }} {{ row.assetTitle }}</router-link
        ></template
      ></el-table-column
    ><el-table-column label="操作人" min-width="130"
      ><template #default="{ row }"
        >{{ row.operatorName }} <small>#{{ row.operatorId }}</small></template
      ></el-table-column
    ><el-table-column label="操作" width="80"
      ><template #default="{ row }">{{
        operationNames[row.operationType]
      }}</template></el-table-column
    ><el-table-column label="字段" min-width="100"
      ><template #default="{ row }">{{
        fieldNames[row.fieldName] || row.fieldName
      }}</template></el-table-column
    ><el-table-column label="修改前" min-width="160"
      ><template #default="{ row }"
        ><span class="audit-value">{{
          row.oldLabel ?? row.oldValue ?? "—"
        }}</span
        ><small v-if="row.oldLabel && row.oldValue" class="muted">
          (ID {{ row.oldValue }})</small
        ></template
      ></el-table-column
    ><el-table-column label="修改后" min-width="160"
      ><template #default="{ row }"
        ><span class="audit-value">{{
          row.newLabel ?? row.newValue ?? "—"
        }}</span
        ><small v-if="row.newLabel && row.newValue" class="muted">
          (ID {{ row.newValue }})</small
        ></template
      ></el-table-column
    ><el-table-column label="时间" min-width="170"
      ><template #default="{ row }">{{
        row.createdAt.replace("T", " ")
      }}</template></el-table-column
    ></el-table
  >
</template>
