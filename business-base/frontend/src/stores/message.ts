import { defineStore } from "pinia";
import {
  fetchMessages,
  fetchUnreadCount,
  markAllMessagesRead,
  markMessageRead,
} from "../api/message";
import { useToastStore } from "./toast";
import { messageMeta, messageTone } from "../utils/message";
import type {
  BusinessMessage,
  MessageListQuery,
  StreamMessage,
} from "../types/message";

interface MessageFilters {
  readStatus: string;
  messageType: string;
}

interface MessageState {
  records: BusinessMessage[];
  pageNo: number;
  pageSize: number;
  unreadCount: number;
  hasMore: boolean;
  loading: boolean;
  error: string;
  markingAllRead: boolean;
  filters: MessageFilters;
}

const MAX_RECONNECT_ATTEMPTS = 5;

let stream: EventSource | null = null;
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
let reconnectAttempts = 0;

function clearReconnect(): void {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
}

function closeStream(): void {
  clearReconnect();
  if (stream) {
    stream.onmessage = null;
    stream.onerror = null;
    stream.close();
    stream = null;
  }
}

/** 将 SSE 推送的实体（payloadJson 为字符串）归一化为收件箱条目。 */
function toBusinessMessage(data: StreamMessage): BusinessMessage {
  let payload: Record<string, unknown> | null = null;
  if (data.payloadJson) {
    try {
      payload = JSON.parse(data.payloadJson) as Record<string, unknown>;
    } catch {
      payload = null;
    }
  }
  return {
    messageId: data.id,
    sourceMessageId: data.sourceMessageId,
    messageType: data.messageType,
    title: data.title ?? "",
    content: data.content,
    severity: data.severity,
    payload,
    readStatus: data.readStatus ?? "UNREAD",
    createdAt: data.createdAt,
    readAt: data.readAt,
  };
}

export const useMessageStore = defineStore("message", {
  state: (): MessageState => ({
    records: [],
    pageNo: 1,
    pageSize: 20,
    unreadCount: 0,
    hasMore: false,
    loading: false,
    error: "",
    markingAllRead: false,
    filters: { readStatus: "", messageType: "" },
  }),
  actions: {
    async loadMessages(pageNo?: number): Promise<void> {
      const targetPage = pageNo ?? this.pageNo;
      this.pageNo = targetPage;
      this.loading = true;
      this.error = "";
      const query: MessageListQuery = {
        pageNo: targetPage,
        pageSize: this.pageSize,
        readStatus: this.filters.readStatus || undefined,
        messageType: this.filters.messageType || undefined,
      };
      try {
        const page = await fetchMessages(query);
        this.records = page.records;
        this.pageNo = page.pageNo;
        this.pageSize = page.pageSize;
        this.unreadCount = page.unreadCount;
        this.hasMore = page.records.length >= this.pageSize;
      } catch (error) {
        this.error = error instanceof Error ? error.message : "消息加载失败";
      } finally {
        this.loading = false;
      }
    },

    async refreshUnreadCount(): Promise<void> {
      try {
        const { unreadCount } = await fetchUnreadCount();
        this.unreadCount = unreadCount;
      } catch {
        // 未读数刷新失败不阻塞主流程，保留上次值。
      }
    },

    applyFilters(readStatus: string, messageType: string): void {
      this.filters.readStatus = readStatus;
      this.filters.messageType = messageType;
      this.pageNo = 1;
      void this.loadMessages(1);
    },

    async markRead(messageId: string): Promise<void> {
      const record = this.records.find((item) => item.messageId === messageId);
      if (record && record.readStatus !== "READ") {
        record.readStatus = "READ";
        record.readAt = new Date().toISOString();
        if (this.unreadCount > 0) {
          this.unreadCount -= 1;
        }
      }
      try {
        await markMessageRead(messageId);
      } catch (error) {
        // 回滚本地乐观更新。
        if (record) {
          record.readStatus = "UNREAD";
          record.readAt = undefined;
          this.unreadCount += 1;
        }
        throw error;
      }
    },

    async markAllRead(): Promise<void> {
      this.markingAllRead = true;
      const previous = this.records.map((record) => ({ ...record }));
      const previousUnread = this.unreadCount;
      for (const record of this.records) {
        record.readStatus = "READ";
        record.readAt = new Date().toISOString();
      }
      this.unreadCount = 0;
      try {
        await markAllMessagesRead();
      } catch (error) {
        this.records = previous;
        this.unreadCount = previousUnread;
        throw error;
      } finally {
        this.markingAllRead = false;
      }
    },

    /** 处理 SSE 推送：累加未读、弹出提示条，并在首屏筛选匹配时前置展示。 */
    handleStreamMessage(data: StreamMessage): void {
      this.unreadCount += 1;
      const toast = useToastStore();
      const meta = messageMeta(data.messageType);
      toast.push({
        tone: messageTone(data.messageType),
        title: data.title || meta.defaultTitle,
        content: data.content,
        messageType: data.messageType,
      });
      const matchesType =
        !this.filters.messageType || this.filters.messageType === data.messageType;
      const matchesRead = this.filters.readStatus !== "READ";
      if (this.pageNo === 1 && matchesType && matchesRead) {
        this.records = [toBusinessMessage(data), ...this.records].slice(
          0,
          this.pageSize,
        );
        this.hasMore = this.records.length >= this.pageSize;
      }
    },

    /** 建立消息 SSE 连接（鉴权后调用）。 */
    connectStream(): void {
      if (stream) return;
      reconnectAttempts = 0;
      this.openStream();
    },

    openStream(): void {
      stream = new EventSource("/api/messages/stream", { withCredentials: true });
      stream.onmessage = (event: MessageEvent) => {
        try {
          const data = JSON.parse(event.data) as StreamMessage;
          reconnectAttempts = 0;
          this.handleStreamMessage(data);
        } catch {
          // 忽略无法解析的推送帧。
        }
      };
      stream.onerror = () => {
        closeStream();
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
          reconnectAttempts += 1;
          const delay = Math.min(1000 * 2 ** reconnectAttempts, 30000);
          reconnectTimer = setTimeout(() => this.openStream(), delay);
        }
      };
    },

    /** 断开 SSE 连接（登出或离开应用时调用）。 */
    disconnectStream(): void {
      closeStream();
      reconnectAttempts = 0;
    },
  },
});
