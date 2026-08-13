/** 异常告警类型，对应 AlertTypeEnum。 */
export type AlertType =
  | "TASK_TIMEOUT"
  | "CALLBACK_FAILED"
  | "ACTION_EXCEPTION"
  | string;

/** 告警级别，对应 AlertSeverityEnum。 */
export type AlertSeverity = "LOW" | "MEDIUM" | "HIGH" | string;

/** 告警处理状态，对应 AlertStatusEnum。 */
export type AlertStatus = "OPEN" | "HANDLED" | "IGNORED" | string;

/** 告警记录，对应 AlertDTO。 */
export interface AlertRecord {
  alertId: string;
  instanceId?: string;
  taskId?: string;
  alertType?: AlertType;
  severity?: AlertSeverity;
  alertStatus?: AlertStatus;
  detail?: Record<string, unknown> | null;
  handledBy?: string;
  handledAt?: string;
  createdAt?: string;
}

/** 告警查询参数，对应 AlertQuery。 */
export interface AlertListQuery {
  pageNo: number;
  pageSize: number;
  alertStatus?: string;
  alertType?: string;
  severity?: string;
  instanceId?: string;
  taskId?: string;
}

/** 处理告警请求体，对应 HandleAlertRequest（仅前端可控字段）。 */
export interface AlertHandlePayload {
  targetStatus: AlertStatus;
  comment?: string;
}
