import type { TaskActionCode } from "../types/workflow";
import { formatDateTime } from "./format";

export const workflowEmptyText = "-";

export const workflowTaskActions: Array<{
  code: TaskActionCode;
  label: string;
  kind: "primary" | "neutral" | "danger";
  requiresTargetUser?: boolean;
}> = [
  { code: "APPROVE", label: "通过", kind: "primary" },
  { code: "SUBMIT", label: "提交", kind: "primary" },
  { code: "REJECT", label: "驳回", kind: "danger" },
  { code: "RETURN", label: "退回", kind: "danger" },
  { code: "WITHDRAW", label: "撤回", kind: "danger" },
  { code: "DIRECT_SEND", label: "直送", kind: "neutral" },
  { code: "TRANSFER", label: "转办", kind: "neutral", requiresTargetUser: true },
  { code: "DELEGATE", label: "委托", kind: "neutral", requiresTargetUser: true },
  { code: "ADD_SIGN", label: "加签", kind: "neutral" },
  { code: "CLAIM", label: "认领", kind: "primary" },
  { code: "UNCLAIM", label: "取消认领", kind: "neutral" },
];

const taskActionLabelByCode = new Map<string, string>(
  workflowTaskActions.map((action) => [action.code, action.label]),
);

const extraTaskActionLabels: Record<string, string> = {
  SEND: "提交",
  OR_SIGN_COMPLETE: "或签完成",
  CANCEL: "已取消",
  CANCELED: "已取消",
  CANCELLED: "已取消",
};

const instanceStatusLabels: Record<string, string> = {
  NOT_STARTED: "未开始",
  RUNNING: "进行中",
  COMPLETED: "已完成",
  ARCHIVED: "已归档",
  TERMINATED: "已终止",
  CANCELLED: "已取消",
  CANCELED: "已取消",
};


export function formatWorkflowDateTime(value: string | undefined | null): string {
  if (!value) return workflowEmptyText;
  const formatted = formatDateTime(value);
  return formatted === "--" ? workflowEmptyText : formatted;
}

export function displayWorkflowValue(value: unknown): string {
  if (value === null || value === undefined || value === "") return workflowEmptyText;
  if (typeof value === "boolean") return value ? "是" : "否";
  return String(value);
}

export function instanceStatusLabel(status: string | undefined | null): string {
  const normalized = status?.trim();
  if (!normalized) return workflowEmptyText;
  return instanceStatusLabels[normalized] ?? "其他状态";
}

export function instanceStatusBadgeClass(status: string | undefined | null): string {
  const normalized = status?.trim();
  if (normalized === "RUNNING") return "is-running";
  if (normalized === "COMPLETED") return "is-completed";
  if (normalized === "TERMINATED" || normalized === "CANCELLED" || normalized === "CANCELED") {
    return "is-stopped";
  }
  if (normalized === "NOT_STARTED") return "is-pending";
  return "is-neutral";
}

export function taskActionLabel(
  action: string | undefined | null,
  emptyFallback = workflowEmptyText,
): string {
  const normalized = action?.trim();
  if (!normalized) return emptyFallback;
  return taskActionLabelByCode.get(normalized) ?? extraTaskActionLabels[normalized] ?? "其他动作";
}

export function taskActionBadgeClass(action: string | undefined | null): string {
  const normalized = action?.trim();
  if (["APPROVE", "SUBMIT", "SEND", "CLAIM", "OR_SIGN_COMPLETE"].includes(normalized ?? "")) {
    return "is-positive";
  }
  if (["REJECT", "RETURN", "WITHDRAW", "CANCEL", "CANCELED", "CANCELLED"].includes(normalized ?? "")) {
    return "is-danger";
  }
  if (["TRANSFER", "DELEGATE", "ADD_SIGN", "DIRECT_SEND", "UNCLAIM"].includes(normalized ?? "")) {
    return "is-neutral";
  }
  return "is-muted";
}

export function workflowNodeLabel(
  nodeCode: string | undefined | null,
  nodeName?: string | null,
): string {
  const name = nodeName?.trim();
  if (name) return name;
  const code = nodeCode?.trim();
  if (!code) return workflowEmptyText;
  return code;
}
