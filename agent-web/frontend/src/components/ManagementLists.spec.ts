import { beforeEach, describe, expect, it, vi } from "vitest";
import { nextTick } from "vue";
import { shallowMount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { useWorkflowStore } from "../stores/workflow";
import ManagementLists from "./ManagementLists.vue";

const messageBox = vi.hoisted(() => ({ confirm: vi.fn() }));

vi.mock("element-plus", async (importOriginal) => ({
  ...(await importOriginal<typeof import("element-plus")>()),
  ElMessageBox: { confirm: messageBox.confirm },
}));

describe("ManagementLists", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    messageBox.confirm.mockReset();
  });

  it("reloads owner-isolated lists when the current user becomes available", async () => {
    const store = useWorkflowStore();
    const load = vi.spyOn(store, "loadManagementLists").mockResolvedValue(undefined);
    shallowMount(ManagementLists, {
      global: { stubs: { ElTabs: true, ElTabPane: true, ElTable: true, ElTableColumn: true, ElTag: true, ElButton: true } },
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

  it("opens an idle historical session without confirmation", async () => {
    const store = useWorkflowStore();
    store.snapshot = {
      sessionId: "current",
      ownerUserId: "u_admin_01",
      state: "CODE_PIPELINE_FAILED",
      rowVersion: 1,
      messages: [],
      allowedActions: ["REVERIFY"],
    };
    const open = vi.spyOn(store, "openSession").mockResolvedValue(undefined);
    const wrapper = shallowMount(ManagementLists, {
      global: { stubs: { ElTabs: true, ElTabPane: true, ElTable: true, ElTableColumn: true, ElTag: true, ElButton: true } },
    });

    await (wrapper.vm as unknown as { openSession: (id: string) => Promise<void> }).openSession("history");

    expect(messageBox.confirm).not.toHaveBeenCalled();
    expect(open).toHaveBeenCalledWith("history");
    expect(wrapper.emitted("opened")).toHaveLength(1);
  });

  it("confirms before leaving a processing session and leaves it unchanged when cancelled", async () => {
    const store = useWorkflowStore();
    store.snapshot = {
      sessionId: "current",
      ownerUserId: "u_admin_01",
      state: "CODE_VERIFYING",
      rowVersion: 1,
      messages: [],
      allowedActions: [],
    };
    const open = vi.spyOn(store, "openSession").mockResolvedValue(undefined);
    messageBox.confirm.mockRejectedValue(new Error("cancelled"));
    const wrapper = shallowMount(ManagementLists, {
      global: { stubs: { ElTabs: true, ElTabPane: true, ElTable: true, ElTableColumn: true, ElTag: true, ElButton: true } },
    });

    await (wrapper.vm as unknown as { openSession: (id: string) => Promise<void> }).openSession("history");

    expect(messageBox.confirm).toHaveBeenCalledTimes(1);
    expect(open).not.toHaveBeenCalled();
    expect(wrapper.emitted("opened")).toBeUndefined();
  });
});
