import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import { ENTRY_APPLICATION_REQUIREMENT, type RequirementRevision } from "@flowmind/agent-contracts";
import RequirementEditor from "./RequirementEditor.vue";

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value));
}

function revision(): RequirementRevision {
  return {
    sessionId: "ags_test",
    revision: 1,
    requirement: clone(ENTRY_APPLICATION_REQUIREMENT),
    missingItems: [],
    ambiguities: [],
    readyForReview: true,
    source: "AGENT",
    createdAt: "2026-08-04T00:00:00.000Z",
  };
}

const elementStubs = {
  ElAlert: { template: "<div>{{ title }}<slot /></div>", props: ["title"] },
  ElButton: { template: "<button :disabled=\"disabled\" @click=\"$emit('click')\"><slot /></button>", props: ["disabled"] },
  ElCheckbox: { template: "<label><input type=\"checkbox\" /><slot /></label>" },
  ElFormItem: { template: "<div><slot /></div>" },
  ElInput: { template: "<input />" },
  ElInputNumber: { template: "<input type=\"number\" />" },
  ElOption: { template: "<option><slot /></option>" },
  ElSelect: { template: "<select><slot /></select>" },
  ElTag: { template: "<span><slot /></span>" },
};

function saveButton(wrapper: ReturnType<typeof mount>) {
  const button = wrapper.findAll("button").find((item) => item.text().includes("保存修改"));
  if (!button) throw new Error("save button not found");
  return button;
}

describe("RequirementEditor", () => {
  it("keeps graph CRUD in the preview canvas instead of rendering legacy node and edge lists", () => {
    const wrapper = mount(RequirementEditor, {
      props: { revision: revision(), disabled: false },
      global: { stubs: elementStubs },
    });

    expect(wrapper.text()).toContain("需求流程图");
    expect(wrapper.text().indexOf("参与角色")).toBeLessThan(wrapper.text().indexOf("需求流程图"));
    expect(wrapper.text().indexOf("需求流程图")).toBeLessThan(wrapper.text().indexOf("表单字段"));
    expect(wrapper.text()).not.toContain("流程节点");
    expect(wrapper.text()).not.toContain("流程连线");
    expect(wrapper.find('[data-testid="graph-search"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="add-node"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="start-connection"]').exists()).toBe(true);
  });

  it("saves graph node coordinates changed by dragging in requirement preview", async () => {
    const wrapper = mount(RequirementEditor, {
      props: { revision: revision(), disabled: false },
      attachTo: document.body,
      global: { stubs: elementStubs },
    });
    const svg = wrapper.get('[data-testid="process-graph"]').element as SVGSVGElement;
    svg.getBoundingClientRect = () => ({
      x: 0,
      y: 0,
      top: 0,
      left: 0,
      right: 980,
      bottom: 360,
      width: 980,
      height: 360,
      toJSON: () => ({}),
    });

    await wrapper.get('[data-testid="graph-node-apply"]').trigger("mousedown", { clientX: 320, clientY: 150 });
    await wrapper.get('[data-testid="process-graph"]').trigger("mousemove", { clientX: 350, clientY: 180 });
    await wrapper.get('[data-testid="process-graph"]').trigger("mouseup");
    await saveButton(wrapper).trigger("click");

    const saved = wrapper.emitted("save")?.[0]?.[0] as RequirementRevision["requirement"];
    const apply = saved.nodes.find((node) => node.nodeCode === "apply");
    expect(apply?.positionX).toBe(290);
    expect(apply?.positionY).toBe(150);
  });

  it("keeps attachment applicable node references when a graph node code changes", async () => {
    const wrapper = mount(RequirementEditor, {
      props: { revision: revision(), disabled: false },
      global: { stubs: elementStubs },
    });

    await wrapper.get('[data-testid="graph-node-apply"]').trigger("click");
    await wrapper.get('[data-testid="node-code"]').setValue("submit");
    await saveButton(wrapper).trigger("click");

    const saved = wrapper.emitted("save")?.[0]?.[0] as RequirementRevision["requirement"];
    expect(saved.attachments[0].applicableNodeCodes).toEqual(["submit"]);
  });

  it("saves edge strategy changed from the requirement preview canvas", async () => {
    const wrapper = mount(RequirementEditor, {
      props: { revision: revision(), disabled: false },
      global: { stubs: elementStubs },
    });

    await wrapper.get('[data-testid="graph-edge-e2"]').trigger("click");
    await wrapper.get('[data-testid="edge-condition-expression"]').setValue("amount > 50000");
    await wrapper.get('[data-testid="edge-default"]').setValue(true);
    await saveButton(wrapper).trigger("click");

    const saved = wrapper.emitted("save")?.[0]?.[0] as RequirementRevision["requirement"];
    const edge = saved.edges.find((item) => item.edgeCode === "e2");
    expect(edge?.conditionExpression).toBe("amount > 50000");
    expect(edge?.defaultEdge).toBe(true);
  });
});
