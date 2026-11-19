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
          path: "media",
          name: "media",
          component: () => import("./views/MediaListView.vue"),
        },
        {
          path: "media/stats",
          name: "media-stats",
          component: () => import("./views/MediaDashboardView.vue"),
        },
        {
          path: "media/changes",
          name: "media-changes",
          component: () => import("./views/MediaChangesView.vue"),
        },
        {
          path: "media/categories",
          name: "media-categories",
          component: () => import("./views/CategoriesView.vue"),
          meta: { admin: true },
        },
        {
          path: "media/:id(\\d+)",
          name: "media-detail",
          component: () => import("./views/MediaDetailView.vue"),
        },
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
