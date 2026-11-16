import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { api, TOKEN_KEY } from "../api";
import type { User, ApiResponse } from "../types";
export const useAuthStore = defineStore("auth", () => {
  const user = ref<User | null>(null);
  const token = ref(sessionStorage.getItem(TOKEN_KEY) || "");
  const isAdmin = computed(() => user.value?.roles.includes("ADMIN") ?? false);
  async function login(username: string, password: string) {
    const { data } = await api.post<
      ApiResponse<{ accessToken: string; user: User }>
    >("/auth/login", { username, password });
    token.value = data.data.accessToken;
    sessionStorage.setItem(TOKEN_KEY, token.value);
    user.value = data.data.user;
  }
  async function refresh() {
    try {
      const { data } = await api.get<ApiResponse<User>>("/auth/me");
      user.value = data.data;
      return true;
    } catch {
      logout();
      return false;
    }
  }
  function logout() {
    user.value = null;
    token.value = "";
    sessionStorage.removeItem(TOKEN_KEY);
  }
  return { user, token, isAdmin, login, refresh, logout };
});
