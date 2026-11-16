import axios from "axios";
import { ElMessage } from "element-plus";
export const TOKEN_KEY = "mediaflow.accessToken";
export const api = axios.create({ baseURL: "/api", timeout: 15000 });
api.interceptors.request.use((config) => {
  const token = sessionStorage.getItem(TOKEN_KEY);
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    if (status === 401 && !error.config?.url?.endsWith("/auth/login")) {
      sessionStorage.removeItem(TOKEN_KEY);
      window.dispatchEvent(new Event("mediaflow:unauthorized"));
    }
    ElMessage.error(
      status === 403
        ? "权限不足，请联系管理员"
        : error.response?.data?.message || "网络请求失败，请稍后重试",
    );
    return Promise.reject(error);
  },
);
