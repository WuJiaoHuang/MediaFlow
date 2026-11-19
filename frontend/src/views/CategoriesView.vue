<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { api } from "../api";
import { listCategories, type Category } from "../media";
const rows = ref<Category[]>([]);
const loading = ref(false);
const error = ref(false);
const dialog = ref(false);
const saving = ref(false);
const selected = ref<number | null>(null);
const form = reactive({
  categoryName: "",
  parentId: undefined as number | undefined,
  sortOrder: 0,
  status: 1,
});
async function load() {
  loading.value = true;
  error.value = false;
  try {
    rows.value = await listCategories();
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}
function open(c?: Category) {
  selected.value = c?.id ?? null;
  Object.assign(form, {
    categoryName: c?.categoryName || "",
    parentId: c?.parentId ?? undefined,
    sortOrder: c?.sortOrder ?? 0,
    status: c?.status ?? 1,
  });
  dialog.value = true;
}
async function save() {
  if (!form.categoryName.trim()) {
    ElMessage.warning("请输入分类名称");
    return;
  }
  saving.value = true;
  try {
    const data = { ...form, parentId: form.parentId ?? null };
    if (selected.value)
      await api.put("/media/categories/" + selected.value, data);
    else await api.post("/media/categories", data);
    ElMessage.success("分类已保存");
    dialog.value = false;
    await load();
  } catch {
  } finally {
    saving.value = false;
  }
}
async function remove(c: Category) {
  try {
    await ElMessageBox.confirm(
      "仅无子分类且无关联素材（含归档）的分类可删除。",
      "删除分类",
      { type: "warning" },
    );
    await api.delete("/media/categories/" + c.id);
    ElMessage.success("分类已删除");
    await load();
  } catch {}
}
onMounted(load);
</script>
<template>
  <div>
    <p class="eyebrow dark">MEDIA CATEGORIES</p>
    <div class="page-heading">
      <div>
        <h1>分类管理</h1>
        <p class="muted">
          最多两级。停用后保留已有素材与统计，禁止分配新素材。
        </p>
      </div>
      <el-button type="primary" @click="open()">新建分类</el-button>
    </div>
    <section class="surface">
      <el-alert
        v-if="error"
        title="分类加载失败"
        type="error"
        :closable="false"
      /><el-button v-if="error" @click="load">重试</el-button
      ><el-table v-else v-loading="loading" :data="rows" empty-text="暂无分类"
        ><el-table-column prop="id" label="ID" width="80" /><el-table-column
          prop="categoryName"
          label="分类名称"
          min-width="180"
        /><el-table-column label="上级分类" min-width="150"
          ><template #default="{ row }">{{
            rows.find((c) => c.id === row.parentId)?.categoryName || "顶级分类"
          }}</template></el-table-column
        ><el-table-column
          prop="sortOrder"
          label="排序"
          width="90"
        /><el-table-column label="状态" width="100"
          ><template #default="{ row }"
            ><el-tag :type="row.status === 1 ? 'success' : 'info'">{{
              row.status === 1 ? "启用" : "停用"
            }}</el-tag></template
          ></el-table-column
        ><el-table-column label="操作" width="150"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="open(row)">编辑</el-button
            ><el-button link type="danger" @click="remove(row)"
              >删除</el-button
            ></template
          ></el-table-column
        ></el-table
      >
    </section>
    <el-dialog
      v-model="dialog"
      :title="selected ? '编辑分类' : '新建分类'"
      width="min(480px,94vw)"
      ><el-form label-position="top"
        ><el-form-item label="分类名称" required
          ><el-input
            v-model="form.categoryName"
            aria-label="分类名称"
            maxlength="100" /></el-form-item
        ><el-form-item label="上级分类"
          ><el-select
            v-model="form.parentId"
            clearable
            aria-label="上级分类"
            placeholder="顶级分类"
            ><el-option
              v-for="c in rows.filter((c) => !c.parentId && c.id !== selected)"
              :key="c.id"
              :label="c.categoryName"
              :value="c.id" /></el-select></el-form-item
        ><el-form-item label="排序值"
          ><el-input-number
            v-model="form.sortOrder"
            :min="0"
            :max="100000"
            aria-label="排序值" /></el-form-item
        ><el-form-item label="状态"
          ><el-radio-group v-model="form.status"
            ><el-radio :value="1">启用</el-radio
            ><el-radio :value="0">停用</el-radio></el-radio-group
          ></el-form-item
        ></el-form
      ><template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >保存分类</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
