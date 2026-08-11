import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory } from "vue-router";
import { createBusinessRouter } from "./router";
import { useAuthStore } from "./stores/auth";
import App from "./App.vue";

describe("App navigation", () => {
  it("shows generated routes and the authenticated user in the business shell", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const auth = useAuthStore();
    auth.user = {
      userId: "u_sales_01", username: "sales01", realName: "张三",
      departmentId: "dept_sales", departmentName: "业务一部",
      userType: "USER", administrator: false,
    };
    auth.initialized = true;
    const router = createBusinessRouter([], createMemoryHistory());
    await router.push("/workflow/todo");
    const wrapper = mount(App, {
      global: {
        plugins: [pinia, router],
        stubs: {
          RouterLink: {
            props: ["to"],
            template: '<a class="nav-link" :href="to"><slot /></a>',
          },
          RouterView: true,
        },
      },
    });

    expect(wrapper.findAll(".nav-link").map((link) => link.text())).toEqual([
      "入金申请",
      "我发起的",
      "我的待办",
      "我的已办",
      "我的已阅",
    ]);
    expect(wrapper.text()).toContain("张三");
    expect(wrapper.text()).toContain("业务一部");
    expect(wrapper.find('[data-test="logout"]').exists()).toBe(true);
  });
});
