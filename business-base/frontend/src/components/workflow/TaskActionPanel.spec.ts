import { flushPromises, mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import TaskActionPanel from "./TaskActionPanel.vue";
import { fetchWorkflowUsers } from "../../api/workflow";

vi.mock("../../api/workflow", () => ({ fetchWorkflowUsers: vi.fn() }));

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

  it("keeps claim clickable and disables flow actions while claim is required", async () => {
    const wrapper = mount(TaskActionPanel, {
      props: {
        taskVersion: 10,
        allowedActions: ["CLAIM", "APPROVE", "REJECT", "RETURN", "TRANSFER", "DELEGATE", "ADD_SIGN"],
        disabledActions: ["APPROVE", "REJECT", "RETURN", "TRANSFER", "DELEGATE", "ADD_SIGN"],
      },
    });

    expect((wrapper.get('[data-test="action-claim"]').element as HTMLButtonElement).disabled).toBe(false);
    expect((wrapper.get('[data-test="action-approve"]').element as HTMLButtonElement).disabled).toBe(true);
    expect((wrapper.get('[data-test="action-reject"]').element as HTMLButtonElement).disabled).toBe(true);
    expect((wrapper.get('[data-test="action-return"]').element as HTMLButtonElement).disabled).toBe(true);
    expect((wrapper.get('[data-test="action-transfer"]').element as HTMLButtonElement).disabled).toBe(true);
    expect((wrapper.get('[data-test="action-delegate"]').element as HTMLButtonElement).disabled).toBe(true);
    expect((wrapper.get('[data-test="action-add-sign"]').element as HTMLButtonElement).disabled).toBe(true);

    await wrapper.get('[data-test="action-approve"]').trigger("click");
    await wrapper.get('[data-test="action-claim"]').trigger("click");

    expect(wrapper.emitted("submit")).toEqual([
      [{ action: "CLAIM", expectedTaskVersion: 10, comment: "" }],
    ]);
  });
  it("selects a searched target user and emits id with name required by the backend DTO", async () => {
    vi.mocked(fetchWorkflowUsers).mockResolvedValue([
      { userId: "u_operations_01", userName: "运营职工一", departmentName: "运营部" },
    ]);
    const wrapper = mount(TaskActionPanel, {
      props: { taskVersion: 5, allowedActions: ["DELEGATE"] },
    });

    await wrapper.get('[data-test="target-user-search"]').setValue("operation");
    await flushPromises();
    await wrapper.get('[data-test="target-user-option-u_operations_01"]').trigger("click");
    await wrapper.find('[data-test="action-delegate"]').trigger("click");

    expect(fetchWorkflowUsers).toHaveBeenCalledWith("operation", 20);
    expect(wrapper.find('[data-test="target-user-name"]').exists()).toBe(false);
    expect(wrapper.emitted("submit")?.[0]?.[0]).toMatchObject({
      action: "DELEGATE",
      expectedTaskVersion: 5,
      targetUserId: "u_operations_01",
      targetUserName: "运营职工一",
    });
  });

  it("selects searched add-sign users and emits their ids", async () => {
    vi.mocked(fetchWorkflowUsers).mockResolvedValue([
      { userId: "u_operations_01", userName: "运营职工一", departmentName: "运营部" },
      { userId: "u_operations_02", userName: "运营职工二", departmentName: "运营部" },
    ]);
    const wrapper = mount(TaskActionPanel, {
      props: { taskVersion: 6, allowedActions: ["ADD_SIGN"] },
    });

    await wrapper.get('[data-test="add-sign-user-search"]').setValue("operation");
    await flushPromises();
    await wrapper.get('[data-test="add-sign-user-option-u_operations_01"]').trigger("click");
    await wrapper.get('[data-test="add-sign-user-search"]').setValue("operation");
    await flushPromises();
    await wrapper.get('[data-test="add-sign-user-option-u_operations_02"]').trigger("click");
    await wrapper.find('[data-test="action-add-sign"]').trigger("click");

    expect(wrapper.emitted("submit")?.[0]?.[0]).toMatchObject({
      action: "ADD_SIGN",
      addSignUserIds: ["u_operations_01", "u_operations_02"],
    });
  });

  it("renders eligible reject targets by node name and submits the selected node code", async () => {
    const wrapper = mount(TaskActionPanel, {
      props: {
        taskVersion: 7,
        allowedActions: ["REJECT"],
        rejectTargetNodes: [
          { nodeCode: "apply", nodeName: "Application" },
          { nodeCode: "initial-review", nodeName: "Initial Review" },
        ],
      },
    });

    const select = wrapper.get('[data-test="reject-target-node"]');
    expect(select.element.tagName).toBe("SELECT");
    expect(select.findAll("option").map((option) => option.text()))
      .toEqual(["请选择", "Application", "Initial Review"]);

    await select.setValue("initial-review");
    await wrapper.find('[data-test="action-reject"]').trigger("click");

    expect(wrapper.emitted("submit")?.[0]?.[0]).toMatchObject({
      action: "REJECT",
      expectedTaskVersion: 7,
      targetNodeCode: "initial-review",
    });
  });

  it("submits direct send without asking for or emitting a target node code", async () => {
    const wrapper = mount(TaskActionPanel, {
      props: { taskVersion: 8, allowedActions: ["DIRECT_SEND"] },
    });

    expect(wrapper.find('input[type="text"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain("目标节点");

    await wrapper.get('[data-test="action-direct-send"]').trigger("click");

    expect(wrapper.emitted("submit")?.[0]).toEqual([{
      action: "DIRECT_SEND",
      expectedTaskVersion: 8,
      comment: "",
    }]);
  });
});
