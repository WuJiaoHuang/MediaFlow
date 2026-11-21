import { api } from "./api";
import { getData, clean } from "./media";
import type { ApiResponse, Page } from "./types";
export interface EditingTask {
  id: number;
  title: string;
  description: string;
  createdBy: number;
  creatorName: string;
  assigneeId: number;
  assigneeName: string;
  reviewerId: number;
  reviewerName: string;
  priority: string;
  dueAt: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}
export interface TaskLog {
  id: number;
  taskId: number;
  operatorId: number;
  operatorName: string;
  operationType: string;
  fieldName: string;
  oldValue: string | null;
  newValue: string | null;
  reason: string | null;
  createdAt: string;
}
export interface TaskPerson {
  id: number;
  username: string;
  displayName: string;
  roleCode: string;
}
export const statusNames: Record<string, string> = {
  PENDING: "待处理",
  PROCESSING: "处理中",
  REVIEWING: "待审核",
  COMPLETED: "已完成",
};
export const priorityNames: Record<string, string> = {
  LOW: "低",
  NORMAL: "普通",
  HIGH: "高",
  URGENT: "紧急",
};
export const taskOperations: Record<string, string> = {
  CREATE: "创建任务",
  ASSIGNEE_CHANGED: "调整执行人",
  REVIEWER_CHANGED: "调整审核人",
  PRIORITY_CHANGED: "调整优先级",
  DUE_DATE_CHANGED: "调整截止时间",
  REQUIREMENTS_CHANGED: "修改任务要求",
  TITLE_CHANGED: "修改标题",
  ASSETS_CHANGED: "调整关联素材",
  START: "开始处理",
  SUBMIT: "提交审核",
  SUBMIT_OVERRIDE: "管理员例外提交",
  APPROVE: "审核通过",
  REJECT: "审核退回",
};
export const taskList = (q: object) =>
  getData<Page<EditingTask>>("/tasks", clean(q));
export const taskDetail = (id: number) => getData<EditingTask>("/tasks/" + id);
export async function taskAction(
  task: EditingTask,
  action: string,
  reason?: string,
) {
  return (
    await api.post<ApiResponse<EditingTask>>(`/tasks/${task.id}/${action}`, {
      version: task.version,
      reason,
    })
  ).data.data;
}
