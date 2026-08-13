import { buildQuery, requestJson } from "./http";
import type {
  MessageListQuery,
  MessagePageResponse,
  UnreadCountResponse,
} from "../types/message";

const MESSAGE_BASE = "/api/messages";

/** 查询当前用户消息收件箱。 */
export async function fetchMessages(
  params: MessageListQuery,
): Promise<MessagePageResponse> {
  return requestJson<MessagePageResponse>(
    `${MESSAGE_BASE}${buildQuery(params)}`,
  );
}

/** 查询当前用户未读消息数。 */
export async function fetchUnreadCount(): Promise<UnreadCountResponse> {
  return requestJson<UnreadCountResponse>(`${MESSAGE_BASE}/unread-count`);
}

/** 标记单条消息为已读。 */
export async function markMessageRead(messageId: string): Promise<void> {
  await requestJson<unknown>(
    `${MESSAGE_BASE}/${encodeURIComponent(messageId)}/read`,
    { method: "POST" },
  );
}

/** 标记当前用户全部消息为已读。 */
export async function markAllMessagesRead(): Promise<void> {
  await requestJson<unknown>(`${MESSAGE_BASE}/read-all`, { method: "POST" });
}
