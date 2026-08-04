import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import ProcessGraphDesigner from "./ProcessGraphDesigner.vue";

const nodes = [
  {
    nodeCode: "start",
    nodeName: "开始",
    nodeType: "START",
    positionX: 80,
    positionY: 120,
    sortOrder: 1,
  },
  {
    nodeCode: "apply",
    nodeName: "申请",
    nodeType: "USER_TASK",
    approverRule: { type: "STARTER", config: {} },
    multiInstanceMode: "SINGLE",
    listenerConfig: {
      taskActionRules: {
        directSend: { enabled: true, targetMode: "REJECT_SOURCE" },
      },
    },
    timeoutConfig: { enabled: true, durationMinutes: 1440, action: "REMIND", severity: "MEDIUM" },
    reminderConfig: { enabled: true, maxCount: 2, messageTemplate: "您有代办，请及时处理。" },
    positionX: 260,
    positionY: 120,
    sortOrder: 2,
  },
  {
    nodeCode: "end",
    nodeName: "结束",
    nodeType: "END",
    positionX: 660,
    positionY: 120,
    sortOrder: 4,
  },
  {
    nodeCode: "review",
    nodeName: "复核",
    nodeType: "USER_TASK",
    approverRule: { type: "ROLE", config: {} },
    multiInstanceMode: "SINGLE",
    listenerConfig: {
      taskActionRules: {
        reject: { enabled: true, targetNodeCodes: ["apply", "review"] },
        directSend: { enabled: true, targetMode: "REJECT_SOURCE" },
      },
    },
    timeoutConfig: { enabled: true, durationMinutes: 1440, action: "REMIND", severity: "MEDIUM" },
    reminderConfig: { enabled: true, maxCount: 2, messageTemplate: "您有代办，请及时处理。" },
    positionX: 460,
    positionY: 120,
    sortOrder: 3,
  },
];

const edges = [
  { edgeCode: "e1", sourceNodeCode: "start", targetNodeCode: "apply", defaultEdge: false, sortOrder: 1 },
  {
    edgeCode: "e2",
    sourceNodeCode: "apply",
    targetNodeCode: "review",
    conditionExpression: "amount > 1000",
    defaultEdge: true,
    sortOrder: 2,
  },
  { edgeCode: "e3", sourceNodeCode: "review", targetNodeCode: "end", defaultEdge: false, sortOrder: 3 },
];

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value));
}

function mountEditableGraph() {
  let wrapper!: ReturnType<typeof mount>;
  wrapper = mount(ProcessGraphDesigner, {
    props: {
      nodes: clone(nodes),
      edges: clone(edges),
      editable: true,
      "onUpdate:nodes": (nextNodes) => wrapper.setProps({ nodes: nextNodes }),
      "onUpdate:edges": (nextEdges) => wrapper.setProps({ edges: nextEdges }),
    },
  });
  return wrapper;
}

describe("ProcessGraphDesigner", () => {
  it("shows the first user task with reject disabled by default", async () => {
    const wrapper = mountEditableGraph();

    await wrapper.get('[data-testid="graph-node-apply"]').trigger("click");

    expect((wrapper.get('[data-testid="reject-enabled"]').element as HTMLInputElement).checked).toBe(false);
    expect((wrapper.get('[data-testid="reject-target-apply"]').element as HTMLInputElement).checked).toBe(false);
    expect((wrapper.get('[data-testid="reject-target-apply"]').element as HTMLInputElement).disabled).toBe(true);
  });

  it("shows Chinese runtime policy editors and updates node policy configs", async () => {
    const wrapper = mountEditableGraph();

    await wrapper.get('[data-testid="graph-node-review"]').trigger("click");

    expect(wrapper.text()).toContain("申请");
    expect(wrapper.text()).toContain("超时策略");
    expect(wrapper.text()).toContain("驳回策略");
    expect(wrapper.text()).toContain("直送策略");
    expect(wrapper.text()).toContain("提醒策略");
    expect(wrapper.text()).not.toContain("timeoutConfig");

    await wrapper.get('[data-testid="timeout-duration-minutes"]').setValue("30");
    expect(wrapper.text()).not.toContain("可驳回节点");
    expect(wrapper.text()).not.toContain("已选：申请");
    expect((wrapper.get('[data-testid="reject-target-apply"]').element as HTMLInputElement).checked).toBe(true);
    expect((wrapper.get('[data-testid="reject-target-review"]').element as HTMLInputElement).checked).toBe(true);

    await wrapper.get('[data-testid="reject-target-apply"]').setValue(false);
    await wrapper.get('[data-testid="direct-send-enabled"]').setValue(false);
    await wrapper.get('[data-testid="reminder-max-count"]').setValue("4");

    const emitted = wrapper.emitted("update:nodes");
    expect(emitted).toBeTruthy();
    const latestNodes = emitted?.at(-1)?.[0] as typeof nodes;
    const review = latestNodes.find((node) => node.nodeCode === "review");
    expect(review?.timeoutConfig).toMatchObject({ enabled: true, durationMinutes: 30, action: "REMIND" });
    expect(review?.listenerConfig).toEqual({
      taskActionRules: {
        reject: { enabled: true, targetNodeCodes: ["review"] },
      },
    });
    expect(review?.reminderConfig).toMatchObject({ enabled: true, maxCount: 4 });
  });

  it("updates approver rule configuration from the node property panel", async () => {
    const wrapper = mountEditableGraph();

    await wrapper.get('[data-testid="graph-node-review"]').trigger("click");
    await wrapper.get('[data-testid="approver-rule-type"]').setValue("ROLE");
    await wrapper.get('[data-testid="approver-role-code"]').setValue("finance");

    let latestNodes = wrapper.emitted("update:nodes")?.at(-1)?.[0] as typeof nodes;
    let review = latestNodes.find((node) => node.nodeCode === "review");
    expect(review?.approverRule).toEqual({ type: "ROLE", config: { roleCode: "finance" } });

    await wrapper.setProps({ nodes: latestNodes });
    await wrapper.get('[data-testid="approver-rule-type"]').setValue("ROLE_IN_DEPARTMENT");
    await wrapper.get('[data-testid="approver-role-code"]').setValue("manager");
    await wrapper.get('[data-testid="approver-department-from"]').setValue("starter");

    latestNodes = wrapper.emitted("update:nodes")?.at(-1)?.[0] as typeof nodes;
    review = latestNodes.find((node) => node.nodeCode === "review");
    expect(review?.approverRule).toEqual({
      type: "ROLE_IN_DEPARTMENT",
      config: { roleCode: "manager", departmentFrom: "starter" },
    });
  });

  it("keeps node-code references consistent when a node code changes", async () => {
    const renamedNodes = clone(nodes) as Array<(typeof nodes)[number] & { timeoutConfig?: Record<string, unknown> }>;
    const review = renamedNodes.find((node) => node.nodeCode === "review");
    review!.timeoutConfig = {
      enabled: true,
      durationMinutes: 1440,
      action: "JUMP",
      severity: "MEDIUM",
      targetNodeCode: "apply",
    };
    const wrapper = mount(ProcessGraphDesigner, {
      props: {
        nodes: renamedNodes,
        edges: clone(edges),
        editable: true,
        "onUpdate:nodes": (nextNodes) => wrapper.setProps({ nodes: nextNodes }),
        "onUpdate:edges": (nextEdges) => wrapper.setProps({ edges: nextEdges }),
      },
    });

    await wrapper.get('[data-testid="graph-node-apply"]').trigger("click");
    await wrapper.get('[data-testid="node-code"]').setValue("submit");

    const latestNodes = wrapper.emitted("update:nodes")?.at(-1)?.[0] as typeof nodes;
    const latestEdges = wrapper.emitted("update:edges")?.at(-1)?.[0] as typeof edges;
    const latestReview = latestNodes.find((node) => node.nodeCode === "review");
    expect(latestEdges.find((edge) => edge.edgeCode === "e2")?.sourceNodeCode).toBe("submit");
    expect(latestReview?.listenerConfig).toMatchObject({
      taskActionRules: {
        reject: { enabled: true, targetNodeCodes: ["submit", "review"] },
      },
    });
    expect(latestReview?.timeoutConfig).toMatchObject({ targetNodeCode: "submit" });
  });

  it("shows and updates condition branch strategy when an edge is selected", async () => {
    const wrapper = mountEditableGraph();

    await wrapper.get('[data-testid="graph-edge-e2"]').trigger("click");

    expect(wrapper.text()).toContain("e2");
    expect(wrapper.text()).toContain("条件分支策略");
    expect((wrapper.get('[data-testid="edge-condition-expression"]').element as HTMLInputElement).value)
      .toBe("amount > 1000");
    await wrapper.get('[data-testid="edge-condition-expression"]').setValue("amount <= 1000");
    await wrapper.get('[data-testid="edge-default"]').setValue(false);

    const latestEdges = wrapper.emitted("update:edges")?.at(-1)?.[0] as typeof edges;
    const edge = latestEdges.find((item) => item.edgeCode === "e2");
    expect(edge?.conditionExpression).toBe("amount <= 1000");
    expect(edge?.defaultEdge).toBe(false);
  });

  it("does not render the removed graph search box", () => {
    const wrapper = mountEditableGraph();

    expect(wrapper.find('[data-testid="graph-search"]').exists()).toBe(false);
  });

  it("adds nodes, creates edges by connection-mode node clicks, and deletes graph items", async () => {
    const wrapper = mountEditableGraph();

    await wrapper.get('[data-testid="add-node"]').trigger("click");
    const addedNodes = wrapper.emitted("update:nodes")?.at(-1)?.[0] as typeof nodes;
    expect(addedNodes).toHaveLength(nodes.length + 1);
    expect(addedNodes.at(-1)?.nodeType).toBe("USER_TASK");
    expect(addedNodes.at(-1)?.listenerConfig).toMatchObject({
      taskActionRules: {
        reject: { enabled: true, targetNodeCodes: ["apply", "review", "node_5"] },
      },
    });

    await wrapper.get('[data-testid="start-connection"]').trigger("click");
    await wrapper.get('[data-testid="graph-node-start"]').trigger("click");
    expect(wrapper.text()).toContain("已选择来源节点：start");
    await wrapper.get('[data-testid="graph-node-review"]').trigger("click");
    const addedEdges = wrapper.emitted("update:edges")?.at(-1)?.[0] as typeof edges;
    expect(addedEdges).toHaveLength(edges.length + 1);
    expect(addedEdges.at(-1)).toMatchObject({ sourceNodeCode: "start", targetNodeCode: "review" });
    expect(wrapper.emitted("update:edges")?.at(-1)?.[0]).toEqual(addedEdges);

    await wrapper.get('[data-testid="graph-node-apply"]').trigger("click");
    await wrapper.get('[data-testid="delete-selected"]').trigger("click");
    const remainingNodes = wrapper.emitted("update:nodes")?.at(-1)?.[0] as typeof nodes;
    const remainingEdges = wrapper.emitted("update:edges")?.at(-1)?.[0] as typeof edges;
    expect(remainingNodes.some((node) => node.nodeCode === "apply")).toBe(false);
    expect(remainingEdges.some((edge) => edge.sourceNodeCode === "apply" || edge.targetNodeCode === "apply")).toBe(false);
  });

  it("emits updated node coordinates after dragging in editable mode", async () => {
    const wrapper = mount(ProcessGraphDesigner, {
      props: { nodes, edges, editable: true },
      attachTo: document.body,
    });
    const svg = wrapper.get('[data-testid="process-graph"]').element as SVGSVGElement;
    svg.getBoundingClientRect = () => ({
      x: 0,
      y: 0,
      top: 0,
      left: 0,
      right: 960,
      bottom: 280,
      width: 960,
      height: 280,
      toJSON: () => ({}),
    });

    await wrapper.get('[data-testid="graph-node-apply"]').trigger("mousedown", { clientX: 320, clientY: 150 });
    await wrapper.get('[data-testid="process-graph"]').trigger("mousemove", { clientX: 360, clientY: 170 });
    await wrapper.get('[data-testid="process-graph"]').trigger("mouseup");

    const emitted = wrapper.emitted("update:nodes");
    expect(emitted).toBeTruthy();
    const latestNodes = emitted?.at(-1)?.[0] as typeof nodes;
    const moved = latestNodes.find((node) => node.nodeCode === "apply");
    expect(moved?.positionX).toBe(300);
    expect(moved?.positionY).toBe(140);
  });
});
