import { describe, expect, it, vi } from "vitest";
import { nextTick } from "vue";
import { shallowMount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { useWorkflowStore } from "../stores/workflow";
import ManagementLists from "./ManagementLists.vue";

describe("ManagementLists", () => {
  it("reloads owner-isolated lists when the current user becomes available", async () => {
    setActivePinia(createPinia());
    const store = useWorkflowStore();
    const load = vi.spyOn(store, "loadManagementLists").mockResolvedValue(undefined);
    shallowMount(ManagementLists, {
      global: { stubs: { ElTabs: true, ElTabPane: true, ElTable: true, ElTableColumn: true, ElTag: true } },
    });
    expect(load).not.toHaveBeenCalled();

    store.currentUser = {
      userId: "u_admin_01",
      username: "admin01",
      realName: "系统管理员一",
      departmentId: "dept_company",
      departmentName: "总公司",
      userType: "ADMIN",
      administrator: true,
    };
    await nextTick();

    expect(load).toHaveBeenCalledTimes(1);
  });
});
