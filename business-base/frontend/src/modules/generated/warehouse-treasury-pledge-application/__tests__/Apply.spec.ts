import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import Apply from "../Apply.vue";

describe("仓单、国债（解）质押申请 Apply", () => {
  it("delegates runtime submission to the shared shell with the fixed process code", () => {
    const wrapper = mount(Apply, { global: { stubs: { WorkflowStartShell: { name: "WorkflowStartShell", props: ["processCode", "businessForm"], template: '<div data-test="shell" />' } } } });
    const shell = wrapper.findComponent({ name: "WorkflowStartShell" });
    expect(shell.props("processCode")).toBe("warehouse_treasury_pledge_application");
    expect(wrapper.html()).not.toContain("start-submit");
    expect(wrapper.find('button[type="submit"]').exists()).toBe(false);
  });
});
