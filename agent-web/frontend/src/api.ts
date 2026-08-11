export class ApiError extends Error {
  constructor(public readonly status: number, public readonly code: string, message: string) {
    super(message);
  }
}

export const AUTHENTICATION_REQUIRED_EVENT = "flowmind:agent-authentication-required";

export async function apiRequest<T>(
  path: string,
  init: RequestInit & { rowVersion?: number; idempotencyKey?: string } = {},
): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body !== undefined) headers.set("Content-Type", "application/json");
  if (init.rowVersion !== undefined) headers.set("If-Match", String(init.rowVersion));
  if (init.idempotencyKey) headers.set("Idempotency-Key", init.idempotencyKey);
  const response = await fetch(path, { ...init, credentials: init.credentials ?? "same-origin", headers });
  const text = await response.text();
  const body = text ? JSON.parse(text) : undefined;
  if (!response.ok) {
    if (response.status === 401) window.dispatchEvent(new Event(AUTHENTICATION_REQUIRED_EVENT));
    throw new ApiError(response.status, body?.code || "HTTP_ERROR", body?.message || response.statusText);
  }
  return body as T;
}

export interface SseMessage {
  event: string;
  data: unknown;
}

export async function streamEvents(
  url: string,
  signal: AbortSignal,
  onMessage: (message: SseMessage) => void,
): Promise<void> {
  const response = await fetch(url, {
    signal,
    credentials: "same-origin",
    headers: {
      Accept: "text/event-stream",
    },
  });
  if (!response.ok || !response.body) {
    if (response.status === 401) window.dispatchEvent(new Event(AUTHENTICATION_REQUIRED_EVENT));
    throw new ApiError(response.status, "SSE_CONNECTION_FAILED", `SSE connection failed: ${response.status}`);
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  while (!signal.aborted) {
    const { done, value } = await reader.read();
    if (done) return;
    buffer += decoder.decode(value, { stream: true });
    const frames = buffer.split(/\r?\n\r?\n/);
    buffer = frames.pop() || "";
    for (const frame of frames) {
      if (!frame || frame.startsWith(":")) continue;
      let event = "message";
      const data: string[] = [];
      for (const line of frame.split(/\r?\n/)) {
        if (line.startsWith("event:")) event = line.slice(6).trim();
        if (line.startsWith("data:")) data.push(line.slice(5).trim());
      }
      if (data.length) onMessage({ event, data: JSON.parse(data.join("\n")) });
    }
  }
}
