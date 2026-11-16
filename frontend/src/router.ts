import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "./stores/auth";
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/login",
      name: "login",
      component: () => import("./views/LoginView.vue"),
      meta: { public: true },
    },
    {
      path: "/",
      component: () => import("./views/LayoutView.vue"),
      children: [
        {
          path: "",
          name: "home",
          component: () => import("./views/HomeView.vue"),
        },
        {
          path: "profile",
          name: "profile",
          component: () => import("./views/ProfileView.vue"),
        },
        {
          path: "admin/users",
          name: "users",
          component: () => import("./views/UsersView.vue"),
          meta: { admin: true },
        },
      ],
    },
    { path: "/:pathMatch(.*)*", redirect: "/" },
  ],
});
router.beforeEach(async (to) => {
  const auth = useAuthStore();
  if (to.meta.public) return true;
  if (!auth.token) return { name: "login", query: { redirect: to.fullPath } };
  if (!(await auth.refresh())) return { name: "login" };
  if (to.meta.admin && !auth.isAdmin) return { name: "home" };
  return true;
});
