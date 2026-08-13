import { buildQuery, requestJson } from "./http";
import type { WorkflowPageResponse } from "../types/workflow";
import type { AlertHandlePayload, AlertListQuery, AlertRecord } from "../types/alert";

const ALERT_BASE = "/api/workflow/admin/alerts";

/** 查询异常告警列表（仅管理员）。 */
export async function fetchAlerts(
  params: AlertListQuery,
): Promise<WorkflowPageResponse<AlertRecord>> {
  return requestJson<WorkflowPageResponse<AlertRecord>>(
    `${ALERT_BASE}${buildQuery(params)}`,
  );
}

/** 处理或忽略告警（仅管理员），需携带幂等键。 */
export async function handleAlert(
  alertId: string,
  payload: AlertHandlePayload,
  idempotencyKey: string,
): Promise<AlertRecord> {
  return requestJson<AlertRecord>(
    `${ALERT_BASE}/${encodeURIComponent(alertId)}/handle`,
    {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify(payload),
    },
  );
}
