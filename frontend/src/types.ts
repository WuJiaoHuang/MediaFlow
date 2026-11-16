export interface User {
  id: number;
  username: string;
  displayName: string;
  status: number;
  roles: string[];
  createdAt: string;
  updatedAt: string;
}
export interface Role {
  id: number;
  roleCode: string;
  roleName: string;
  description: string;
}
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}
export interface Page<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}
export const roleNames: Record<string, string> = {
  ADMIN: "管理员",
  EDITOR: "剪辑人员",
  REVIEWER: "审核人员",
};
