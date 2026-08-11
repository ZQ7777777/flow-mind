export interface ApiErrorBody {
  code?: string;
  message?: string;
  requestId?: string;
}

export const AUTHENTICATION_REQUIRED_EVENT = "flowmind:authentication-required";

export function notifyAuthenticationRequired(status: number): void {
  if (status === 401) {
    window.dispatchEvent(new Event(AUTHENTICATION_REQUIRED_EVENT));
  }
}

export class WorkflowApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly requestId?: string;

  constructor(status: number, body: ApiErrorBody) {
    super(body.message ?? "请求失败");
    this.name = "WorkflowApiError";
    this.status = status;
    this.code = body.code ?? "UNKNOWN_ERROR";
    this.requestId = body.requestId;
  }
}

export function buildQuery(params: object): string {
  const searchParams = new URLSearchParams();
  for (const [key, value] of Object.entries(params) as Array<[string, unknown]>) {
    if (value !== undefined && value !== "") {
      searchParams.set(key, String(value));
    }
  }
  const query = searchParams.toString();
  return query ? `?${query}` : "";
}

export async function requestJson<T>(url: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(url, {
    ...init,
    credentials: init.credentials ?? "same-origin",
    headers: {
      Accept: "application/json",
      ...(init.body instanceof FormData ? {} : { "Content-Type": "application/json" }),
      ...(init.headers ?? {}),
    },
  });

  const body = await response.json().catch(() => null);
  if (!response.ok) {
    notifyAuthenticationRequired(response.status);
    throw new WorkflowApiError(response.status, (body ?? {}) as ApiErrorBody);
  }
  return body as T;
}

export async function requestBlob(url: string, init: RequestInit = {}): Promise<Blob> {
  const response = await fetch(url, {
    ...init,
    credentials: init.credentials ?? "same-origin",
    headers: {
      Accept: "application/octet-stream",
      ...(init.headers ?? {}),
    },
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    notifyAuthenticationRequired(response.status);
    throw new WorkflowApiError(response.status, (body ?? {}) as ApiErrorBody);
  }
  return response.blob();
}
