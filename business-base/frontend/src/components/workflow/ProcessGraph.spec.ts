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

    expect(wrapper.findAll("svg g")).toHaveLength(2);
    expect(wrapper.findAll("svg line")).toHaveLength(1);
    expect(wrapper.find("svg g.current").text()).toContain("经理审批");
  });
});
