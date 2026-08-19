import { buildQuery, requestBlob, requestJson } from "./http";
import type {
  AttachmentUploadPayload,
  WorkflowListQuery,
  WorkflowPageResponse,
  TaskActionPayload,
  WorkflowTaskActionResponse,
  WorkflowAttachmentView,
  WorkflowDetailResponse,
  WorkflowListRecord,
  WorkflowListType,
  WorkflowStartContext,
  WorkflowStartPayload,
  WorkflowStartResponse,
  WorkflowProcessEntryLink,
  WorkflowStartableProcessResponse,
  WorkflowUserResponse,
  WorkflowUserCandidateResponse,
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

export async function fetchCurrentUser(): Promise<WorkflowUserResponse> {
  return requestJson<WorkflowUserResponse>(`${WORKFLOW_BASE}/me`);
}

export async function fetchWorkflowUsers(
  keyword: string,
  limit = 20,
): Promise<WorkflowUserCandidateResponse[]> {
  return requestJson<WorkflowUserCandidateResponse[]>(
    `${WORKFLOW_BASE}/users${buildQuery({ keyword, limit })}`,
  );
}

export async function fetchEntryApplicationProcess(): Promise<WorkflowStartableProcessResponse> {
  return requestJson<WorkflowStartableProcessResponse>(
    `${WORKFLOW_BASE}/startable-processes/entry-application`,
  );
}

export async function fetchProcessEntryLinks(): Promise<WorkflowProcessEntryLink[]> {
  return requestJson<WorkflowProcessEntryLink[]>(
    `${WORKFLOW_BASE}/process-entry-links`,
  );
}

export async function fetchProcessEntryLink(
  definitionId: string,
): Promise<WorkflowProcessEntryLink> {
  return requestJson<WorkflowProcessEntryLink>(
    `${WORKFLOW_BASE}/process-entry-links/${encodeURIComponent(definitionId)}`,
  );
}

export async function fetchWorkflowStartContext(processCode: string): Promise<WorkflowStartContext> {
  return requestJson<WorkflowStartContext>(
    `${WORKFLOW_BASE}/processes/${encodeURIComponent(processCode)}/start-context`,
  );
}

export async function startWorkflowProcess(
  processCode: string,
  payload: WorkflowStartPayload,
): Promise<WorkflowStartResponse> {
  const formData = new FormData();
  formData.append("payload", new Blob([
    JSON.stringify({ variables: payload.variables }),
  ], { type: "application/json" }));
  for (const [attachmentCode, files] of Object.entries(payload.attachments)) {
    for (const file of files) {
      formData.append(attachmentCode, file, file.name);
    }
  }

  return requestJson<WorkflowStartResponse>(
    `${WORKFLOW_BASE}/processes/${encodeURIComponent(processCode)}/start-submit`,
    {
      method: "POST",
      headers: { "Idempotency-Key": payload.idempotencyKey },
      body: formData,
    },
  );
}

export async function fetchWorkflowList(
  type: WorkflowListType,
  params: WorkflowListQuery,
): Promise<WorkflowPageResponse<WorkflowListRecord>> {
  return requestJson<WorkflowPageResponse<WorkflowListRecord>>(
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
): Promise<WorkflowTaskActionResponse> {
  const { idempotencyKey, ...body } = payload;
  return requestJson<WorkflowTaskActionResponse>(
    `${WORKFLOW_BASE}/tasks/${encodeURIComponent(taskId)}/${actionEndpointByCode[action]}`,
    {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify(body),
    },
  );
}


export async function remindTask(
  taskId: string,
  payload: Pick<TaskActionPayload, "expectedTaskVersion" | "comment" | "idempotencyKey">,
): Promise<void> {
  const { idempotencyKey, ...body } = payload;
  await requestJson<unknown>(
    `${WORKFLOW_BASE}/tasks/${encodeURIComponent(taskId)}/remind`,
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
): Promise<WorkflowTaskActionResponse> {
  return performTaskAction(taskId, "APPROVE", payload);
}

export async function fetchAttachments(params: {
  instanceId?: string;
  taskId?: string;
}): Promise<WorkflowAttachmentView[]> {
  return requestJson<WorkflowAttachmentView[]>(
    `${WORKFLOW_BASE}/attachments${buildQuery(params)}`,
  );
}

export async function uploadInstanceAttachment(
  instanceId: string,
  payload: AttachmentUploadPayload,
): Promise<WorkflowAttachmentView> {
  return uploadAttachment(
    `${WORKFLOW_BASE}/instances/${encodeURIComponent(instanceId)}/attachments`,
    payload,
  );
}

export async function uploadTaskAttachment(
  taskId: string,
  payload: AttachmentUploadPayload,
): Promise<WorkflowAttachmentView> {
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

export async function replaceInstanceAttachment(
  taskId: string,
  attachmentId: string,
  file: File,
  expectedTaskVersion: number,
  idempotencyKey: string,
): Promise<WorkflowAttachmentView> {
  const formData = new FormData();
  formData.append("file", file, file.name);
  formData.append("expectedTaskVersion", String(expectedTaskVersion));
  return requestJson<WorkflowAttachmentView>(
    `${WORKFLOW_BASE}/tasks/${encodeURIComponent(taskId)}/instance-attachments/${encodeURIComponent(attachmentId)}`,
    {
      method: "PUT",
      headers: { "Idempotency-Key": idempotencyKey },
      body: formData,
    },
  );
}

async function uploadAttachment(
  url: string,
  payload: AttachmentUploadPayload,
): Promise<WorkflowAttachmentView> {
  const formData = new FormData();
  formData.append("file", payload.file, payload.file.name);
  if (payload.fieldCode) {
    formData.append("fieldCode", payload.fieldCode);
  }
  if (payload.attachmentCode) {
    formData.append("attachmentCode", payload.attachmentCode);
  }
  if (payload.instanceId) formData.append("instanceId", payload.instanceId);
  if (payload.sourceTaskId) formData.append("sourceTaskId", payload.sourceTaskId);
  if (payload.expectedTaskVersion != null) {
    formData.append("expectedTaskVersion", String(payload.expectedTaskVersion));
  }

  return requestJson<WorkflowAttachmentView>(url, {
    method: "POST",
    headers: { "Idempotency-Key": payload.idempotencyKey },
    body: formData,
  });
}
