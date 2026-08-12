import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import ProcessGraphDesigner from "./ProcessGraphDesigner.vue";

function button(wrapper: ReturnType<typeof mount>, label: string) {
  const found = wrapper.findAll("button").find((item) => item.text().includes(label));
  if (!found) throw new Error(`button not found: ${label}`);
  return found;
}

describe("ProcessGraphDesigner", () => {
  const graph = {
    nodes: [
      { nodeCode: "start", nodeName: "开始", nodeType: "START", positionX: 20, positionY: 20 },
      { nodeCode: "apply", nodeName: "申请", nodeType: "USER_TASK", positionX: 220, positionY: 20, approverRuleType: "USER", approverRuleConfig: '{"userIds":["u1"]}', listenerConfig: '{"listeners":[{"event":"TASK_CREATED","handler":"audit"}]}' },
      { nodeCode: "end", nodeName: "结束", nodeType: "END", positionX: 420, positionY: 20 },
    ],
    edges: [{ edgeCode: "edge_start_apply", sourceNodeCode: "start", targetNodeCode: "apply" }],
  };

  it("creates edges by clicking nodes and selects and deletes an edge", async () => {
    const wrapper = mount(ProcessGraphDesigner, { props: { ...structuredClone(graph) } });
    await button(wrapper, "开始连线").trigger("click");
    const canvasNodes = wrapper.findAll(".designer-node");
    await canvasNodes[1].trigger("click");
    await canvasNodes[2].trigger("click");
    expect(wrapper.emitted("update:edges")?.at(-1)?.[0]).toEqual(expect.arrayContaining([
      expect.objectContaining({ sourceNodeCode: "apply", targetNodeCode: "end" }),
    ]));

    await wrapper.setProps({ edges: [
      ...graph.edges,
      { edgeCode: "edge_apply_end", sourceNodeCode: "apply", targetNodeCode: "end" },
    ] });
    await wrapper.findAll(".edge-hit")[1].trigger("pointerdown");
    expect(wrapper.text()).toContain("连线配置：edge_apply_end");
    await button(wrapper, "删除连线").trigger("click");
    expect(wrapper.emitted("update:edges")?.at(-1)?.[0]).toHaveLength(1);
  });

  it("configures user tasks with controls while preserving advanced listener content", async () => {
    const wrapper = mount(ProcessGraphDesigner, {
      props: {
        ...structuredClone(graph),
        options: {
          users: [{ userId: "u1", userName: "用户一", roleCodes: ["finance"] }],
          departments: [{ departmentId: "d1", departmentName: "财务部" }],
          roles: [{ roleCode: "finance", roleName: "财务" }],
        },
      },
    });
    await wrapper.findAll(".designer-node")[1].trigger("pointerdown", { clientX: 230, clientY: 30, pointerId: 1, button: 0 });
    expect(wrapper.text()).toContain("审批来源");
    expect(wrapper.text()).toContain("超时与提醒");
    expect(wrapper.text()).toContain("驳回与直送");
    const directSend = wrapper.findAll("label.checkbox").find((item) => item.text().includes("启用直送"));
    await directSend!.find("input").setValue(true);
    const listener = JSON.parse((wrapper.props("nodes") as typeof graph.nodes)[1].listenerConfig!);
    expect(listener.listeners).toEqual([{ event: "TASK_CREATED", handler: "audit" }]);
    expect(listener.taskActionRules.directSend.enabled).toBe(true);
  });

  it("auto-layouts nodes and exposes zoom controls", async () => {
    const wrapper = mount(ProcessGraphDesigner, { props: { ...structuredClone(graph) } });
    await button(wrapper, "自动布局").trigger("click");
    expect(wrapper.emitted("update:nodes")?.at(-1)?.[0]).toEqual(expect.arrayContaining([
      expect.objectContaining({ nodeCode: "start", positionX: 70, positionY: 150 }),
    ]));
    for (let index = 0; index < 20; index += 1) await wrapper.get('button[aria-label="放大"]').trigger("click");
    expect(wrapper.find(".zoom-label").text()).toBe("200%");
  });
});
