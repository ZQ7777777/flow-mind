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
  it("centers the node bounds in the canvas viewBox", () => {
    const wrapper = mountEditableGraph();

    expect(wrapper.get('[data-testid="process-graph"]').attributes("viewBox")).toBe("-54 -40 980 380");
  });

  it("uses the default canvas viewBox when there are no nodes", () => {
    const wrapper = mount(ProcessGraphDesigner, { props: { nodes: [], edges: [] } });

    expect(wrapper.get('[data-testid="process-graph"]').attributes("viewBox")).toBe("0 0 980 380");
  });

  it("shows zoom controls in read-only mode and keeps the graph center while zooming", async () => {
    const wrapper = mount(ProcessGraphDesigner, { props: { nodes, edges } });
    const initial = wrapper.get('[data-testid="process-graph"]').attributes("viewBox")!.split(" ").map(Number) as [number, number, number, number];

    expect(wrapper.find('[data-testid="add-node"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="zoom-percent"]').text()).toBe("100%");

    await wrapper.get('[data-testid="zoom-in"]').trigger("click");
    const zoomed = wrapper.get('[data-testid="process-graph"]').attributes("viewBox")!.split(" ").map(Number) as [number, number, number, number];

    expect(wrapper.get('[data-testid="zoom-percent"]').text()).toBe("110%");
    expect(zoomed[2]).toBeCloseTo(initial[2] / 1.1);
    expect(zoomed[3]).toBeCloseTo(initial[3] / 1.1);
    expect(zoomed[0] + zoomed[2] / 2).toBeCloseTo(initial[0] + initial[2] / 2);
    expect(zoomed[1] + zoomed[3] / 2).toBeCloseTo(initial[1] + initial[3] / 2);

    await wrapper.get('[data-testid="zoom-reset"]').trigger("click");
    expect(wrapper.get('[data-testid="zoom-percent"]').text()).toBe("100%");
    expect(wrapper.get('[data-testid="process-graph"]').attributes("viewBox")).toBe(initial.join(" "));
  });

  it("supports modified-wheel zoom and enforces the zoom limits", async () => {
    const wrapper = mount(ProcessGraphDesigner, { props: { nodes, edges } });
    const graph = wrapper.get('[data-testid="process-graph"]');

    graph.element.dispatchEvent(new WheelEvent("wheel", { deltaY: -100 }));
    await wrapper.vm.$nextTick();
    expect(wrapper.get('[data-testid="zoom-percent"]').text()).toBe("100%");
    graph.element.dispatchEvent(new WheelEvent("wheel", { ctrlKey: true, deltaY: -100, cancelable: true }));
    await wrapper.vm.$nextTick();
    expect(wrapper.get('[data-testid="zoom-percent"]').text()).toBe("110%");

    for (let index = 0; index < 20; index += 1) {
      await wrapper.get('[data-testid="zoom-out"]').trigger("click");
    }
    expect(wrapper.get('[data-testid="zoom-percent"]').text()).toBe("50%");
    expect((wrapper.get('[data-testid="zoom-out"]').element as HTMLButtonElement).disabled).toBe(true);

    for (let index = 0; index < 20; index += 1) {
      await wrapper.get('[data-testid="zoom-in"]').trigger("click");
    }
    expect(wrapper.get('[data-testid="zoom-percent"]').text()).toBe("200%");
    expect((wrapper.get('[data-testid="zoom-in"]').element as HTMLButtonElement).disabled).toBe(true);
  });

  it("pans the canvas from empty space at the current zoom and resets the viewport", async () => {
    const wrapper = mount(ProcessGraphDesigner, { props: { nodes, edges } });
    const graph = wrapper.get('[data-testid="process-graph"]');
    const svg = graph.element as SVGSVGElement;
    svg.getBoundingClientRect = () => ({
      x: 0,
      y: 0,
      top: 0,
      left: 0,
      right: 980,
      bottom: 380,
      width: 980,
      height: 380,
      toJSON: () => ({}),
    });
    const initialViewBox = graph.attributes("viewBox");

    await wrapper.get('[data-testid="zoom-in"]').trigger("click");
    const zoomed = graph.attributes("viewBox")!.split(" ").map(Number) as [number, number, number, number];
    await graph.trigger("mousedown", { button: 0, clientX: 100, clientY: 100 });
    expect(graph.classes()).toContain("panning");
    expect((wrapper.get('[data-testid="zoom-in"]').element as HTMLButtonElement).disabled).toBe(true);
    await graph.trigger("mousemove", { clientX: 144, clientY: 122 });
    await graph.trigger("mouseup");

    const panned = graph.attributes("viewBox")!.split(" ").map(Number) as [number, number, number, number];
    expect(panned[0]).toBeCloseTo(zoomed[0] - 40);
    expect(panned[1]).toBeCloseTo(zoomed[1] - 20);
    expect(graph.classes()).not.toContain("panning");
    expect((wrapper.get('[data-testid="zoom-reset"]').element as HTMLButtonElement).disabled).toBe(false);

    await wrapper.get('[data-testid="zoom-reset"]').trigger("click");
    expect(wrapper.get('[data-testid="zoom-percent"]').text()).toBe("100%");
    expect(graph.attributes("viewBox")).toBe(initialViewBox);
  });

  it("keeps part of the graph visible while panning and ignores pan gestures for an empty graph", async () => {
    const wrapper = mount(ProcessGraphDesigner, { props: { nodes, edges } });
    const graph = wrapper.get('[data-testid="process-graph"]');
    await graph.trigger("mousedown", { button: 0, clientX: 0, clientY: 0 });
    await graph.trigger("mousemove", { clientX: 10000, clientY: 10000 });
    await graph.trigger("mouseup");
    const panned = graph.attributes("viewBox")!.split(" ").map(Number) as [number, number, number, number];
    expect(panned[0]).toBe(-860);
    expect(panned[1]).toBe(-220);

    const emptyWrapper = mount(ProcessGraphDesigner, { props: { nodes: [], edges: [] } });
    const emptyGraph = emptyWrapper.get('[data-testid="process-graph"]');
    await emptyGraph.trigger("mousedown", { button: 0, clientX: 0, clientY: 0 });
    await emptyGraph.trigger("mousemove", { clientX: 100, clientY: 100 });
    expect(emptyGraph.attributes("viewBox")).toBe("0 0 980 380");
    expect(emptyGraph.classes()).not.toContain("panning");
  });

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
      right: 980,
      bottom: 380,
      width: 980,
      height: 380,
      toJSON: () => ({}),
    });

    await wrapper.get('[data-testid="zoom-in"]').trigger("click");
    await wrapper.get('[data-testid="graph-node-apply"]').trigger("mousedown", { clientX: 320, clientY: 150 });
    expect((wrapper.get('[data-testid="zoom-in"]').element as HTMLButtonElement).disabled).toBe(true);
    await wrapper.get('[data-testid="process-graph"]').trigger("mousemove", { clientX: 364, clientY: 172 });
    await wrapper.get('[data-testid="process-graph"]').trigger("mouseup");

    const emitted = wrapper.emitted("update:nodes");
    expect(emitted).toBeTruthy();
    const latestNodes = emitted?.at(-1)?.[0] as typeof nodes;
    const moved = latestNodes.find((node) => node.nodeCode === "apply");
    expect(moved?.positionX).toBe(300);
    expect(moved?.positionY).toBe(140);
  });
});
