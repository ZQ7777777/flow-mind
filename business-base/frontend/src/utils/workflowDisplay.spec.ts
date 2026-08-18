import { describe, expect, it } from "vitest";
import {
  formatWorkflowDateTime,
  instanceStatusLabel,
  taskActionLabel,
  workflowEmptyText,
  workflowNodeLabel,
} from "./workflowDisplay";

describe("workflowDisplay", () => {
  it("maps workflow instance statuses to Chinese labels without exposing unknown enums", () => {
    expect(instanceStatusLabel("RUNNING")).toBe("进行中");
    expect(instanceStatusLabel("COMPLETED")).toBe("已完成");
    expect(instanceStatusLabel("CANCELLED")).toBe("已取消");
    expect(instanceStatusLabel("SUSPENDED")).toBe("其他状态");
    expect(instanceStatusLabel(undefined)).toBe(workflowEmptyText);
  });

  it("maps task actions to Chinese labels without exposing unknown enums", () => {
    expect(taskActionLabel("APPROVE")).toBe("通过");
    expect(taskActionLabel("REJECT")).toBe("驳回");
    expect(taskActionLabel("RETURN")).toBe("退回");
    expect(taskActionLabel("TRANSFER")).toBe("转办");
    expect(taskActionLabel("DELEGATE")).toBe("委托");
    expect(taskActionLabel("ADD_SIGN")).toBe("加签");
    expect(taskActionLabel("UNKNOWN_ACTION")).toBe("其他动作");
  });

  it("formats workflow list dates with the shared formatter and list empty fallback", () => {
    expect(formatWorkflowDateTime(undefined)).toBe(workflowEmptyText);
    expect(formatWorkflowDateTime("2026-08-12T11:10:00")).toContain("2026");
  });

  it("prefers node names and falls back to raw node codes", () => {
    expect(workflowNodeLabel("manager_approve", "部门经理审批")).toBe("部门经理审批");
    expect(workflowNodeLabel("custom_review", "自定义审批")).toBe("自定义审批");
    expect(workflowNodeLabel("custom_review")).toBe("custom_review");
    expect(workflowNodeLabel(undefined)).toBe(workflowEmptyText);
  });
});
