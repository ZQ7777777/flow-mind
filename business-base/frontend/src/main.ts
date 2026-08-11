import { createApp } from "vue";
import { createPinia } from "pinia";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import App from "./App.vue";
import { AUTHENTICATION_REQUIRED_EVENT } from "./api/http";
import router from "./router";
import { useAuthStore } from "./stores/auth";

const pinia = createPinia();

window.addEventListener(AUTHENTICATION_REQUIRED_EVENT, () => {
  const currentRoute = router.currentRoute.value;
  if (currentRoute.path === "/login" || currentRoute.matched.length === 0) return;

  useAuthStore(pinia).clear();
  void router.replace({
    path: "/login",
    query: { redirect: currentRoute.fullPath },
  });
});

createApp(App).use(pinia).use(router).use(ElementPlus).mount("#app");
