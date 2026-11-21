import { api } from "./api";
export const feedbackStates: Record<string, string> = {
  OPEN: "待处理",
  RESOLVED: "已处理待确认",
  CLOSED: "已确认解决",
  REOPENED: "重新打开",
};
export const feedbackTypes: Record<string, string> = {
  TEXT: "文字",
  AUDIO: "语音",
  VIDEO: "视频",
};
export interface Feedback {
  id: number;
  taskId: number;
  assetId: number | null;
  assetTitle: string | null;
  mediaType: string | null;
  creatorId: number;
  creatorName: string;
  type: string;
  content: string;
  timecodeMs: number | null;
  status: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}
export interface Attachment {
  id: number;
  feedbackId: number;
  originalFilename: string;
  mimeType: string;
  fileSize: number;
}
export interface FeedbackDetail {
  feedback: Feedback;
  attachment: Attachment | null;
}
export interface Reply {
  id: number;
  creatorName: string;
  creatorId: number;
  content: string;
  createdAt: string;
}
export interface FeedbackLog {
  id: number;
  operatorName: string;
  operationType: string;
  oldStatus: string | null;
  newStatus: string;
  reason: string | null;
  createdAt: string;
}
export const feedbackPath = (taskId: number, id?: number) =>
  `/tasks/${taskId}/feedback${id === undefined ? "" : "/" + id}`;
export async function feedbackAction(
  taskId: number,
  f: Feedback,
  action: string,
  reason?: string,
) {
  return (
    await api.post(feedbackPath(taskId, f.id) + "/" + action, {
      version: f.version,
      reason,
    })
  ).data.data as FeedbackDetail;
}
export function feedbackError(e: any) {
  return (
    e.response?.data?.message ||
    (e.name === "CanceledError"
      ? "上传已取消，可重新提交"
      : "请求失败，请检查网络后重试")
  );
}
export function timecode(ms: number) {
  return `${Math.floor(ms / 60000)}:${String(Math.floor(ms / 1000) % 60).padStart(2, "0")}.${String(ms % 1000).padStart(3, "0")}`;
}
