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

    store.currentUser = { userId: "user_sales", userName: "Sales User" };
    await nextTick();

    expect(load).toHaveBeenCalledTimes(1);
  });
});
