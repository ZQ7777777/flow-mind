export type WorkflowListType = "todo" | "completed" | "started" | "read";

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

export interface PageRequest {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  taskSource?: "OWN" | "DELEGATED" | "ALL" | string;
  processName?: string;
  title?: string;
  nodeName?: string;
  status?: string;
  action?: string;
}

export interface PageResponse<T> {
  items: T[];
  pageNo: number;
  pageSize: number;
  total: number;
}

export interface WorkflowListItem {
  id: string;
  instanceId?: string;
  taskId?: string;
  title: string;
  processName?: string;
  nodeName?: string;
  starterName?: string;
  taskSource?: "OWN" | "DELEGATED" | string;
  claimed?: boolean;
  status?: string;
  action?: string;
  createdAt?: string;
  dueAt?: string;
  completedAt?: string;
  endedAt?: string;
  readAt?: string;
}

export interface CurrentBusinessUser {
  userId: string;
  displayName: string;
  departmentId?: string;
  departmentName?: string;
}

export interface WorkflowInstanceSummary {
  instanceId: string;
  definitionId?: string;
  processName: string;
  title: string;
  starterName?: string;
  status: string;
  currentNodeName?: string;
  version?: number;
  startedAt?: string;
  endedAt?: string;
}

export interface WorkflowTaskSummary {
  taskId: string;
  taskName: string;
  nodeCode: string;
  nodeName: string;
  taskVersion: number;
  assigneeName?: string;
  allowedActions: TaskActionCode[];
}

export interface WorkflowFormField {
  fieldCode: string;
  fieldName: string;
  fieldType?: string;
  controlType?: string;
  sortOrder?: number;
  validationRule?: unknown;
}

export interface WorkflowGraphNode {
  nodeCode: string;
  nodeName: string;
  sortOrder?: number;
  x?: number;
  y?: number;
}

export interface WorkflowGraphEdge {
  sourceNodeCode: string;
  targetNodeCode: string;
  label?: string;
}

export interface WorkflowGraph {
  nodes: WorkflowGraphNode[];
  edges: WorkflowGraphEdge[];
  currentNodeCodes?: string[];
}

export interface WorkflowAttachment {
  attachmentId: string;
  fileName: string;
  fileSize?: number;
  contentType?: string;
  fieldCode?: string;
  templateCode?: string;
  scope?: "INSTANCE" | "TASK" | string;
  createdAt?: string;
  canDelete?: boolean;
  canDownload?: boolean;
}

export interface WorkflowComment {
  commentId?: string;
  taskName?: string;
  action?: string;
  operatorName?: string;
  content?: string;
  completedAt?: string;
}

export interface WorkflowTimelineItem {
  id: string;
  nodeName?: string;
  action?: string;
  operatorName?: string;
  happenedAt?: string;
  comment?: string;
}

export interface WorkflowDetailResponse {
  instance: WorkflowInstanceSummary;
  currentTask?: WorkflowTaskSummary;
  formFields: WorkflowFormField[];
  variables: Record<string, unknown>;
  graph?: WorkflowGraph;
  attachments: WorkflowAttachment[];
  comments: WorkflowComment[];
  timeline: WorkflowTimelineItem[];
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

export interface TaskActionResult {
  replayed: boolean;
  instance?: WorkflowInstanceSummary;
  archivedTask?: WorkflowTaskSummary;
  newTasks?: WorkflowTaskSummary[];
  updatedTask?: WorkflowTaskSummary;
}

export interface AttachmentUploadPayload {
  file: File;
  fieldCode?: string;
  templateCode?: string;
  idempotencyKey: string;
}
