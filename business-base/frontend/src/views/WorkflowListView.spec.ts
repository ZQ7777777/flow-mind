import { flushPromises, mount } from "@vue/test-utils";
import { createPinia } from "pinia";
import { afterEach, describe, expect, it, vi } from "vitest";
import WorkflowListView from "./WorkflowListView.vue";

function emptyPage() {
  return new Response(
    JSON.stringify({
      items: [],
      pageNo: 1,
      pageSize: 20,
      total: 0,
    }),
    { status: 200, headers: { "Content-Type": "application/json" } },
  );
}

describe("WorkflowListView", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("separates own todo tasks from delegated todo tasks", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(emptyPage())
      .mockResolvedValueOnce(emptyPage());
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(WorkflowListView, {
      props: { type: "todo", title: "我的待办" },
      global: {
        plugins: [createPinia()],
        stubs: {
          RouterLink: {
            props: ["to"],
            template: '<a :href="to"><slot /></a>',
          },
        },
      },
    });
    await flushPromises();

    expect(fetchMock.mock.calls[0][0]).toContain("taskSource=OWN");
    expect(wrapper.find('[data-test="todo-scope-own"]').classes()).toContain("active");

    await wrapper.find('[data-test="todo-scope-delegated"]').trigger("click");
    await flushPromises();

    expect(fetchMock.mock.calls[1][0]).toContain("taskSource=DELEGATED");
    expect(wrapper.find('[data-test="todo-scope-delegated"]').classes()).toContain("active");
  });

  it("keeps the list headers visible while data is loading", async () => {
    vi.stubGlobal("fetch", vi.fn(() => new Promise(() => undefined)));

    const wrapper = mount(WorkflowListView, {
      props: { type: "started", title: "我发起的" },
      global: {
        plugins: [createPinia()],
        stubs: {
          RouterLink: {
            props: ["to"],
            template: '<a :href="to"><slot /></a>',
          },
        },
      },
    });
    await wrapper.vm.$nextTick();

    expect(wrapper.find("thead").text()).toContain("标题");
    expect(wrapper.find("thead").text()).toContain("状态");
    expect(wrapper.find("tbody").text()).toContain("加载中");
  });
});
