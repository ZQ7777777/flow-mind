import type { AlertStatus } from "../types/alert";

/** 提示条语气，用于弹窗/提示条着色。 */
export type ToastTone = "info" | "success" | "warning" | "error";

export interface MessageMeta {
  label: string;
  tone: ToastTone;
  defaultTitle: string;
}

const MESSAGE_META: Record<string, MessageMeta> = {
  ALERT: { label: "告警", tone: "error", defaultTitle: "流程异常告警" },
  TASK_TIMEOUT: { label: "任务超时", tone: "error", defaultTitle: "任务超时提醒" },
  TASK_DUE_SOON: { label: "即将超时", tone: "warning", defaultTitle: "任务即将超时" },
  TASK_REMIND: { label: "催办", tone: "info", defaultTitle: "任务催办" },
};

const DEFAULT_MESSAGE_META: MessageMeta = { label: "消息", tone: "info", defaultTitle: "新消息" };

export function messageMeta(messageType: string | undefined | null): MessageMeta {
  if (!messageType) return DEFAULT_MESSAGE_META;
  return MESSAGE_META[messageType] ?? DEFAULT_MESSAGE_META;
}

export function messageTypeLabel(messageType: string | undefined | null): string {
  return messageMeta(messageType).label;
}

export function messageTone(messageType: string | undefined | null): ToastTone {
  return messageMeta(messageType).tone;
}

const SEVERITY_LABELS: Record<string, string> = {
  HIGH: "高",
  MEDIUM: "中",
  NORMAL: "普通",
  LOW: "低",
};

export function severityLabel(severity: string | undefined | null): string {
  if (!severity) return "--";
  return SEVERITY_LABELS[severity] ?? severity;
}

export function severityClass(severity: string | undefined | null): string {
  if (severity === "HIGH") return "is-high";
  if (severity === "MEDIUM") return "is-medium";
  return "is-normal";
}

const READ_STATUS_LABELS: Record<string, string> = {
  UNREAD: "未读",
  READ: "已读",
};

export function readStatusLabel(status: string | undefined | null): string {
  if (!status) return "--";
  return READ_STATUS_LABELS[status] ?? status;
}

const ALERT_TYPE_LABELS: Record<string, string> = {
  TASK_TIMEOUT: "任务超时",
  CALLBACK_FAILED: "回调失败",
  ACTION_EXCEPTION: "动作异常",
};

export function alertTypeLabel(type: string | undefined | null): string {
  if (!type) return "--";
  return ALERT_TYPE_LABELS[type] ?? type;
}

const ALERT_STATUS_LABELS: Record<string, string> = {
  OPEN: "待处理",
  HANDLED: "已处理",
  IGNORED: "已忽略",
};

export function alertStatusLabel(status: string | undefined | null): string {
  if (!status) return "--";
  return ALERT_STATUS_LABELS[status] ?? status;
}

export function alertStatusClass(status: AlertStatus | string | undefined | null): string {
  if (status === "OPEN") return "is-open";
  if (status === "HANDLED") return "is-handled";
  if (status === "IGNORED") return "is-ignored";
  return "";
}

/** 提供消息中心与告警页面下拉框使用的过滤选项。 */
export const MESSAGE_TYPE_FILTERS: ReadonlyArray<{ value: string; label: string }> = [
  { value: "", label: "全部类型" },
  { value: "ALERT", label: "告警" },
  { value: "TASK_TIMEOUT", label: "任务超时" },
  { value: "TASK_DUE_SOON", label: "即将超时" },
  { value: "TASK_REMIND", label: "催办" },
];

export const READ_STATUS_FILTERS: ReadonlyArray<{ value: string; label: string }> = [
  { value: "", label: "全部" },
  { value: "UNREAD", label: "未读" },
  { value: "READ", label: "已读" },
];

export const ALERT_TYPE_FILTERS: ReadonlyArray<{ value: string; label: string }> = [
  { value: "", label: "全部类型" },
  { value: "TASK_TIMEOUT", label: "任务超时" },
  { value: "CALLBACK_FAILED", label: "回调失败" },
  { value: "ACTION_EXCEPTION", label: "动作异常" },
];

export const ALERT_STATUS_FILTERS: ReadonlyArray<{ value: string; label: string }> = [
  { value: "", label: "全部状态" },
  { value: "OPEN", label: "待处理" },
  { value: "HANDLED", label: "已处理" },
  { value: "IGNORED", label: "已忽略" },
];

export const ALERT_SEVERITY_FILTERS: ReadonlyArray<{ value: string; label: string }> = [
  { value: "", label: "全部级别" },
  { value: "HIGH", label: "高" },
  { value: "MEDIUM", label: "中" },
  { value: "LOW", label: "低" },
];
