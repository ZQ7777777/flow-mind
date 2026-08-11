import { describe, expect, it, vi } from "vitest";
import { mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { useAuthStore } from "../stores/auth";
import AdminLogin from "./AdminLogin.vue";

describe("AdminLogin", () => {
  it("submits the administrator credentials through the auth store", async () => {
    setActivePinia(createPinia());
    const auth = useAuthStore();
    const login = vi.spyOn(auth, "login").mockResolvedValue(true);
    const wrapper = mount(AdminLogin);

    await wrapper.get('input[name="username"]').setValue("admin01");
    await wrapper.get('input[name="password"]').setValue("123456");
    await wrapper.get("form").trigger("submit");

    expect(login).toHaveBeenCalledWith("admin01", "123456");
  });

  it("shows the administrator-only message after access is denied", async () => {
    setActivePinia(createPinia());
    const auth = useAuthStore();
    auth.forbidden = true;
    const wrapper = mount(AdminLogin);

    expect(wrapper.text()).toContain("仅管理员可以访问 Agent 工作台");
  });
});
