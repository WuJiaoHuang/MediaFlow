import { api } from "./api";
import type { ApiResponse, Page } from "./types";
export interface Category {
  id: number;
  categoryName: string;
  parentId: number | null;
  sortOrder: number;
  status: number;
  createdAt: string;
  updatedAt: string;
}
export interface Asset {
  id: number;
  title: string;
  description: string;
  categoryId: number | null;
  categoryName: string | null;
  mediaType: string;
  createdBy: number;
  uploaderName: string;
  updatedBy: number;
  createdAt: string;
  updatedAt: string;
  status: string;
  version: number;
  originalFilename: string;
  mimeType: string;
  fileSize: number;
}
export interface Change {
  id: number;
  assetId: number;
  operatorId: number;
  operatorName: string;
  assetTitle: string;
  operationType: string;
  fieldName: string;
  oldValue: string | null;
  newValue: string | null;
  oldLabel: string | null;
  newLabel: string | null;
  createdAt: string;
}
export interface Stats {
  total: number;
  videos: number;
  audios: number;
  images: number;
  recent: number;
  totalBytes: number;
  categories: {
    categoryId: number | null;
    categoryName: string;
    count: number;
  }[];
}
export type Filters = {
  keyword?: string;
  categoryId?: number;
  mediaType?: string;
  uploadedBy?: number;
  from?: string;
  to?: string;
  status?: string;
  sort?: string;
  direction?: string;
};
export const typeNames: Record<string, string> = {
  IMAGE: "图片",
  AUDIO: "音频",
  VIDEO: "视频",
};
export const fieldNames: Record<string, string> = {
  title: "素材名称",
  description: "描述",
  category_id: "分类",
  status: "状态",
  file_id: "原始文件",
};
export const operationNames: Record<string, string> = {
  CREATE: "创建",
  UPDATE: "修改",
  ARCHIVE: "归档",
};
export function clean(q: object) {
  return Object.fromEntries(
    Object.entries(q).filter(
      ([, v]) => v !== "" && v !== null && v !== undefined,
    ),
  );
}
export async function getData<T>(path: string, params?: object): Promise<T> {
  return (
    await api.get<ApiResponse<T>>(path, {
      params: params ? clean(params) : undefined,
    })
  ).data.data;
}
export const listAssets = (q: object) =>
  getData<Page<Asset>>("/media/assets", q);
export const listCategories = () => getData<Category[]>("/media/categories");
export const formatBytes = (n: number) =>
  n >= 1048576
    ? (n / 1048576).toFixed(1) + " MB"
    : n >= 1024
      ? (n / 1024).toFixed(1) + " KB"
      : n + " B";
export function enabledCategories(categories: Category[]) {
  return categories.filter(
    (c) =>
      c.status === 1 &&
      (!c.parentId ||
        categories.some((p) => p.id === c.parentId && p.status === 1)),
  );
}
