import { buildQuery, requestBlob, requestJson } from "./http";
import type {
  AttachmentUploadPayload,
  CurrentBusinessUser,
  PageRequest,
  PageResponse,
  TaskActionPayload,
  TaskActionResult,
  WorkflowAttachment,
  WorkflowDetailResponse,
  WorkflowListItem,
  WorkflowListType,
} from "../types/workflow";

const WORKFLOW_BASE = "/api/workflow";

const listEndpointByType: Record<WorkflowListType, string> = {
  todo: `${WORKFLOW_BASE}/tasks/todo`,
  completed: `${WORKFLOW_BASE}/tasks/completed`,
  started: `${WORKFLOW_BASE}/instances/started`,
  read: `${WORKFLOW_BASE}/read-records`,
};

const actionEndpointByCode = {
  APPROVE: "approve",
  SUBMIT: "submit",
  REJECT: "reject",
  RETURN: "return",
  WITHDRAW: "withdraw",
  DIRECT_SEND: "direct-send",
  TRANSFER: "transfer",
  DELEGATE: "delegate",
  ADD_SIGN: "add-sign",
  CLAIM: "claim",
  UNCLAIM: "unclaim",
} as const;

export async function fetchCurrentUser(): Promise<CurrentBusinessUser> {
  return requestJson<CurrentBusinessUser>(`${WORKFLOW_BASE}/me`);
}

export async function fetchWorkflowList(
  type: WorkflowListType,
  params: PageRequest,
): Promise<PageResponse<WorkflowListItem>> {
  return requestJson<PageResponse<WorkflowListItem>>(
    `${listEndpointByType[type]}${buildQuery(params)}`,
  );
}

export async function fetchTaskDetail(taskId: string): Promise<WorkflowDetailResponse> {
  return requestJson<WorkflowDetailResponse>(
    `${WORKFLOW_BASE}/tasks/${encodeURIComponent(taskId)}`,
  );
}

export async function fetchInstanceDetail(instanceId: string): Promise<WorkflowDetailResponse> {
  return requestJson<WorkflowDetailResponse>(
    `${WORKFLOW_BASE}/instances/${encodeURIComponent(instanceId)}`,
  );
}

export async function markInstanceRead(instanceId: string, idempotencyKey: string): Promise<void> {
  await requestJson<unknown>(`${WORKFLOW_BASE}/instances/${encodeURIComponent(instanceId)}/read`, {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
  });
}

export async function performTaskAction(
  taskId: string,
  action: keyof typeof actionEndpointByCode,
  payload: TaskActionPayload,
): Promise<TaskActionResult> {
  const { idempotencyKey, ...body } = payload;
  return requestJson<TaskActionResult>(
    `${WORKFLOW_BASE}/tasks/${encodeURIComponent(taskId)}/${actionEndpointByCode[action]}`,
    {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify(body),
    },
  );
}

export async function approveTask(
  taskId: string,
  payload: Pick<TaskActionPayload, "expectedTaskVersion" | "comment" | "idempotencyKey">,
): Promise<TaskActionResult> {
  return performTaskAction(taskId, "APPROVE", payload);
}

export async function fetchAttachments(params: {
  instanceId?: string;
  taskId?: string;
}): Promise<WorkflowAttachment[]> {
  return requestJson<WorkflowAttachment[]>(
    `${WORKFLOW_BASE}/attachments${buildQuery(params)}`,
  );
}

export async function uploadInstanceAttachment(
  instanceId: string,
  payload: AttachmentUploadPayload,
): Promise<WorkflowAttachment> {
  return uploadAttachment(
    `${WORKFLOW_BASE}/instances/${encodeURIComponent(instanceId)}/attachments`,
    payload,
  );
}

export async function uploadTaskAttachment(
  taskId: string,
  payload: AttachmentUploadPayload,
): Promise<WorkflowAttachment> {
  return uploadAttachment(
    `${WORKFLOW_BASE}/tasks/${encodeURIComponent(taskId)}/attachments`,
    payload,
  );
}

export async function downloadAttachment(attachmentId: string): Promise<Blob> {
  return requestBlob(
    `${WORKFLOW_BASE}/attachments/${encodeURIComponent(attachmentId)}/content`,
  );
}

export async function deleteAttachment(
  attachmentId: string,
  idempotencyKey: string,
): Promise<void> {
  await requestJson<unknown>(
    `${WORKFLOW_BASE}/attachments/${encodeURIComponent(attachmentId)}`,
    {
      method: "DELETE",
      headers: { "Idempotency-Key": idempotencyKey },
    },
  );
}

async function uploadAttachment(
  url: string,
  payload: AttachmentUploadPayload,
): Promise<WorkflowAttachment> {
  const formData = new FormData();
  formData.append("file", payload.file, payload.file.name);
  if (payload.fieldCode) {
    formData.append("fieldCode", payload.fieldCode);
  }
  if (payload.templateCode) {
    formData.append("templateCode", payload.templateCode);
  }

  return requestJson<WorkflowAttachment>(url, {
    method: "POST",
    headers: { "Idempotency-Key": payload.idempotencyKey },
    body: formData,
  });
}
