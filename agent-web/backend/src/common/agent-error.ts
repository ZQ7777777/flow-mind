import { randomUUID } from "node:crypto";
import { HttpException, type HttpStatus } from "@nestjs/common";
import type { AgentErrorBody } from "@flowmind/agent-contracts";

export class AgentError extends HttpException {
  constructor(
    status: HttpStatus,
    code: string,
    message: string,
    sessionId?: string,
    details: Record<string, unknown> = {},
  ) {
    const body: AgentErrorBody = {
      code,
      message,
      sessionId,
      requestId: `req_${randomUUID()}`,
      details,
    };
    super(body, status);
  }
}
