export type WorkflowListType = "todo" | "completed" | "started" | "read";

export type WorkflowDeadlineStatus = "NONE" | "NORMAL" | "DUE_SOON" | "OVERDUE" | string;

export type TaskActionCode =
  | "APPROVE"
  | "SUBMIT"
  | "REJECT"
  | "RETURN"
  | "WITHDRAW"
  | "DIRECT_SEND"
  | "TRANSFER"
  | "DELEGATE"
  | "ADD_SIGN"
  | "CLAIM"
  | "UNCLAIM";

export interface WorkflowListQuery {
  pageNo: number;
  pageSize: number;
  processCode?: string;
  processName?: string;
  instanceTitle?: string;
  starterUserId?: string;
  nodeCode?: string;
  status?: string;
  source?: "OWN" | "DELEGATED" | "ALL" | string;
  actionType?: string;
  businessKey?: string;
  currentNodeCode?: string;
  instanceId?: string;
  sortBy?: string;
  sortDirection?: string;
  from?: string;
  to?: string;
}

export interface WorkflowPageResponse<T> {
  records: T[];
  pageNo: number;
  pageSize: number;
  total: number;
  totalPages: number;
}

export interface WorkflowUserResponse {
  userId: string;
  userName: string;
  departmentId?: string;
  departmentName?: string;
}

export interface WorkflowUserCandidateResponse {
  userId: string;
  userName: string;
  departmentId?: string;
  departmentName?: string;
}

export interface WorkflowStartableProcessResponse {
  processCode: string;
  processName: string;
}

export interface WorkflowProcessEntryLink {
  definitionId: string;
  processCode?: string;
  processName?: string;
  entryDisplayName?: string;
  entryPageUrl?: string;
  entrySource?: "MANUAL" | "AGENT_GENERATED" | string;
  enabled?: boolean;
}
export interface WorkflowTaskResponse {
  taskId: string;
  instanceId: string;
  processCode?: string;
  processName?: string;
  instanceTitle: string;
  starterUserId?: string;
  starterUserName?: string;
  nodeCode: string;
  nodeName?: string;
  candidateUserIds: string[];
  assigneeUserId?: string;
  assigneeUserName?: string;
  delegateFromUserId?: string;
  delegateFromUserName?: string;
  taskStatus?: string;
  taskVersion: number;
  createdAt?: string;
  deadlineStatus?: WorkflowDeadlineStatus;
  dueAt?: string;
}

export interface WorkflowHistoryTaskResponse {
  historyTaskId: string;
  instanceId: string;
  activeTaskId?: string;
  processCode?: string;
  processName?: string;
  instanceTitle: string;
  nodeCode?: string;
  nodeName?: string;
  assigneeUserId?: string;
  assigneeUserName?: string;
  delegateFromUserId?: string;
  delegateFromUserName?: string;
  handleType?: string;
  actionType?: string;
  comment?: string;
  startedAt?: string;
  completedAt?: string;
  withdrawContext?: WorkflowWithdrawContext;
}

export interface WorkflowWithdrawContext {
  taskId: string;
  expectedTaskVersion: number;
  targetNodeCode: string;
  targetNodeName?: string;
}

export interface WorkflowInstanceResponse {
  instanceId: string;
  definitionId?: string;
  processCode?: string;
  processName?: string;
  version?: number;
  instanceTitle: string;
  starterUserId?: string;
  starterUserName?: string;
  starterDepartmentId?: string;
  instanceStatus?: string;
  currentNodeCodes: string[];
  variables: Record<string, unknown>;
  startedAt?: string;
  endedAt?: string;
}

export interface WorkflowReadRecordResponse {
  readRecordId: string;
  instanceId: string;
  taskId?: string;
  processCode?: string;
  processName?: string;
  instanceTitle: string;
  instanceStatus?: string;
  readAt?: string;
}

export type WorkflowListRecord =
  | WorkflowTaskResponse
  | WorkflowHistoryTaskResponse
  | WorkflowInstanceResponse
  | WorkflowReadRecordResponse;

export interface WorkflowDefinitionView {
  processCode?: string;
  processName?: string;
  version?: number;
}

export interface WorkflowFormField {
  fieldCode: string;
  fieldName: string;
  fieldType?: string;
  controlType?: string;
  required?: boolean;
  visible?: boolean;
  editable?: boolean;
  validationRule?: string;
  sortOrder?: number;
}

export interface WorkflowNodeView {
  nodeCode: string;
  nodeName: string;
  nodeType?: string;
  positionX?: number;
  positionY?: number;
  sortOrder?: number;
}

export interface WorkflowEdgeView {
  edgeCode?: string;
  sourceNodeCode: string;
  targetNodeCode: string;
  defaultEdge?: boolean;
  sortOrder?: number;
}

export interface WorkflowCommentView {
  commentId?: string;
  taskId?: string;
  nodeCode?: string;
  operatorUserId?: string;
  operatorUserName?: string;
  comment?: string;
  createdAt?: string;
}

export interface WorkflowAttachmentView {
  attachmentId: string;
  instanceId?: string;
  taskId?: string;
  ownerType?: "INSTANCE" | "TASK" | string;
  attachmentCode?: string;
  fieldCode?: string;
  fileName: string;
  contentType?: string;
  sizeBytes?: number;
  uploadedBy?: string;
  uploadedAt?: string;
}

export interface WorkflowUploadableAttachmentView {
  attachmentCode: string;
  attachmentName?: string;
  description?: string;
  fieldCode?: string;
  ownerType?: "INSTANCE" | "TASK" | string;
  required?: boolean;
  minCount?: number;
  maxCount?: number;
  maxSizeBytes?: number;
  allowedExtensions: string[];
  sortOrder?: number;
}

export interface WorkflowDetailResponse {
  instance: WorkflowInstanceResponse;
  definition?: WorkflowDefinitionView;
  formFields: WorkflowFormField[];
  nodes: WorkflowNodeView[];
  edges: WorkflowEdgeView[];
  currentTask?: WorkflowTaskResponse;
  activeTasks: WorkflowTaskResponse[];
  historyTasks: WorkflowHistoryTaskResponse[];
  comments: WorkflowCommentView[];
  attachments: WorkflowAttachmentView[];
  uploadableAttachments: WorkflowUploadableAttachmentView[];
  rejectTargetNodes: WorkflowNodeView[];
  allowedActions: TaskActionCode[];
  disabledActions: TaskActionCode[];
}

export interface TaskActionPayload {
  expectedTaskVersion: number;
  comment?: string;
  targetNodeCode?: string;
  targetUserId?: string;
  targetUserName?: string;
  addSignUserIds?: string[];
  variables?: Record<string, unknown>;
  idempotencyKey: string;
}

export interface WorkflowTaskActionResponse {
  operationId?: string;
  instance?: WorkflowInstanceResponse;
  archivedTasks: WorkflowHistoryTaskResponse[];
  createdTasks: WorkflowTaskResponse[];
  updatedTasks: WorkflowTaskResponse[];
  replayed: boolean;
}

export interface AttachmentUploadPayload {
  file: File;
  ownerType?: "INSTANCE" | "TASK";
  attachmentCode?: string;
  fieldCode?: string;
  instanceId?: string;
  sourceTaskId?: string;
  expectedTaskVersion?: number;
  idempotencyKey: string;
}



