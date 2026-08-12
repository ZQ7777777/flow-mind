import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import ProcessGraph from "./ProcessGraph.vue";

describe("ProcessGraph", () => {
  it("renders backend nodes, edges and the current node", () => {
    const wrapper = mount(ProcessGraph, {
      props: {
        nodes: [
          { nodeCode: "apply", nodeName: "提交申请", positionX: 20, positionY: 30 },
          { nodeCode: "approve", nodeName: "经理审批", positionX: 220, positionY: 30 },
        ],
        edges: [
          { edgeCode: "edge-1", sourceNodeCode: "apply", targetNodeCode: "approve" },
        ],
        currentNodeCodes: ["approve"],
      },
    });

    expect(wrapper.findAll("svg g[data-graph-interactive]")).toHaveLength(2);
    expect(wrapper.findAll("svg line")).toHaveLength(1);
    expect(wrapper.find("svg g.current").text()).toContain("经理审批");
  });

  it("zooms within bounds and does not mutate read-only graph data", async () => {
    const nodes = [{ nodeCode: "apply", nodeName: "提交申请", positionX: 20, positionY: 30 }];
    const snapshot = structuredClone(nodes);
    const wrapper = mount(ProcessGraph, { props: { nodes, edges: [], currentNodeCodes: [] } });
    const zoomIn = wrapper.find('button[aria-label="放大"]');
    const zoomOut = wrapper.find('button[aria-label="缩小"]');
    for (let index = 0; index < 20; index += 1) await zoomIn.trigger("click");
    expect(wrapper.get('[data-testid="graph-zoom"]').text()).toBe("200%");
    for (let index = 0; index < 30; index += 1) await zoomOut.trigger("click");
    expect(wrapper.get('[data-testid="graph-zoom"]').text()).toBe("25%");
    expect(nodes).toEqual(snapshot);
  });
});
