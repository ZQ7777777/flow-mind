/** 消息类型，与平台 ProcessMessage.messageType 对应。 */
export type MessageType =
  | "ALERT"
  | "TASK_TIMEOUT"
  | "TASK_DUE_SOON"
  | "TASK_REMIND"
  | string;

/** 消息级别。 */
export type MessageSeverity = "HIGH" | "NORMAL" | string;

/** 消息已读状态。 */
export type MessageReadStatus = "UNREAD" | "READ" | string;

/** 个人消息中心查询参数，对应 BusinessMessageQuery。 */
export interface MessageListQuery {
  pageNo: number;
  pageSize: number;
  readStatus?: string;
  messageType?: string;
}

/** 个人消息条目，对应 BusinessMessageResponse。 */
export interface BusinessMessage {
  messageId: string;
  sourceMessageId?: string;
  messageType: MessageType;
  title: string;
  content?: string;
  severity?: MessageSeverity;
  payload?: Record<string, unknown> | null;
  readStatus: MessageReadStatus;
  createdAt?: string;
  readAt?: string;
}

/** 消息分页响应，对应 BusinessMessagePageResponse（仅返回未读数，无 total）。 */
export interface MessagePageResponse {
  records: BusinessMessage[];
  pageNo: number;
  pageSize: number;
  unreadCount: number;
}

/** 未读消息数响应，对应 BusinessUnreadCountResponse。 */
export interface UnreadCountResponse {
  unreadCount: number;
}

/**
 * SSE 推送的消息体，对应 BusinessUserMessageEntity。
 * 注意 payloadJson 是序列化后的字符串（区别于 REST 响应中的 payload 对象）。
 */
export interface StreamMessage {
  id: string;
  sourceMessageId?: string;
  recipientUserId?: string;
  messageType: MessageType;
  title?: string;
  content?: string;
  severity?: MessageSeverity;
  payloadJson?: string | null;
  readStatus: MessageReadStatus;
  createdAt?: string;
  readAt?: string;
}
