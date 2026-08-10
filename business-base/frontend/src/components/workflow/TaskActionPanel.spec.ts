import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import TaskActionPanel from "./TaskActionPanel.vue";

describe("TaskActionPanel", () => {
  it("only renders actions allowed by the workflow detail response", async () => {
    const wrapper = mount(TaskActionPanel, {
      props: {
        taskVersion: 3,
        allowedActions: ["APPROVE", "TRANSFER"],
      },
    });

    expect(wrapper.text()).toContain("通过");
    expect(wrapper.text()).toContain("转办");
    expect(wrapper.text()).not.toContain("驳回");
  });

  it("emits the action with the current task version", async () => {
    const wrapper = mount(TaskActionPanel, {
      props: {
        taskVersion: 9,
        allowedActions: ["APPROVE"],
      },
    });

    await wrapper.find('[data-test="action-approve"]').trigger("click");

    expect(wrapper.emitted("submit")?.[0]).toEqual([
      {
        action: "APPROVE",
        expectedTaskVersion: 9,
        comment: "",
      },
    ]);
  });
});
