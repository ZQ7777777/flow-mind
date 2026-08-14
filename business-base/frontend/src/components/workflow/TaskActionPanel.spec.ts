import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import TaskActionPanel from "./TaskActionPanel.vue";
import { fetchWorkflowUsers } from "../../api/workflow";

vi.mock("../../api/workflow", () => ({ fetchWorkflowUsers: vi.fn() }));

async function openSuggestions(input: ReturnType<ReturnType<typeof mount>["get"]>, keyword: string) {
  await input.setValue(keyword);
  await vi.advanceTimersByTimeAsync(300);
  await flushPromises();
}

async function clickSuggestion(selector: string): Promise<void> {
  const option = document.body.querySelector<HTMLElement>(selector);
  expect(option).not.toBeNull();
  option?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  await flushPromises();
}

describe("TaskActionPanel", () => {
  beforeEach(() => {
    vi.mocked(fetchWorkflowUsers).mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
    document.body.innerHTML = "";
  });

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
    vi.useFakeTimers();
    vi.mocked(fetchWorkflowUsers).mockResolvedValue([
      { userId: "u_operations_01", userName: "运营职工一", departmentName: "运营部" },
    ]);
    const wrapper = mount(TaskActionPanel, {
      props: { taskVersion: 5, allowedActions: ["DELEGATE"] },
    });

    await openSuggestions(wrapper.get('[data-test="target-user-search"]'), "operation");
    expect(document.body.textContent).toContain("运营职工一");
    expect(document.body.textContent).toContain("u_operations_01 / 运营部");
    await clickSuggestion('[data-test="target-user-option-u_operations_01"]');
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
    vi.useFakeTimers();
    vi.mocked(fetchWorkflowUsers).mockResolvedValue([
      { userId: "u_operations_01", userName: "运营职工一", departmentName: "运营部" },
      { userId: "u_operations_02", userName: "运营职工二", departmentName: "运营部" },
    ]);
    const wrapper = mount(TaskActionPanel, {
      props: { taskVersion: 6, allowedActions: ["ADD_SIGN"] },
    });

    const input = wrapper.get('[data-test="add-sign-user-search"]');
    await openSuggestions(input, "operation");
    await clickSuggestion('[data-test="add-sign-user-option-u_operations_01"]');
    await openSuggestions(input, "operation");
    expect(document.body.querySelector('[data-test="add-sign-user-option-u_operations_01"]'))
      .toBeNull();
    await clickSuggestion('[data-test="add-sign-user-option-u_operations_02"]');
    await wrapper.find('[data-test="action-add-sign"]').trigger("click");

    expect(wrapper.emitted("submit")?.[0]?.[0]).toMatchObject({
      action: "ADD_SIGN",
      addSignUserIds: ["u_operations_01", "u_operations_02"],
    });
  });

  it("clears a selected target user when its text is edited", async () => {
    vi.useFakeTimers();
    vi.mocked(fetchWorkflowUsers).mockResolvedValue([
      { userId: "u_operations_01", userName: "运营职工一", departmentName: "运营部" },
    ]);
    const wrapper = mount(TaskActionPanel, {
      props: { taskVersion: 5, allowedActions: ["TRANSFER"] },
    });
    const input = wrapper.get('[data-test="target-user-search"]');

    await openSuggestions(input, "operation");
    await clickSuggestion('[data-test="target-user-option-u_operations_01"]');
    expect(wrapper.find('[data-test="selected-target-user"]').exists()).toBe(true);

    await input.setValue("another user");
    expect(wrapper.find('[data-test="selected-target-user"]').exists()).toBe(false);
    await wrapper.get('[data-test="action-transfer"]').trigger("click");

    expect(wrapper.text()).toContain("请选择目标用户");
    expect(wrapper.emitted("submit")).toBeUndefined();
  });

  it("does not query blank keywords and hides suggestions after a search failure", async () => {
    vi.useFakeTimers();
    vi.mocked(fetchWorkflowUsers).mockRejectedValue(new Error("network error"));
    const wrapper = mount(TaskActionPanel, {
      props: { taskVersion: 5, allowedActions: ["TRANSFER"] },
    });
    const input = wrapper.get('[data-test="target-user-search"]');

    await openSuggestions(input, "   ");
    expect(fetchWorkflowUsers).not.toHaveBeenCalled();

    await openSuggestions(input, "missing");
    expect(fetchWorkflowUsers).toHaveBeenCalledWith("missing", 20);
    expect(document.body.querySelector('[data-test^="target-user-option-"]')).toBeNull();
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
