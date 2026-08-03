import { ArgumentsHost, Catch, HttpException, HttpStatus, type ExceptionFilter } from "@nestjs/common";
import type { Response } from "express";
import { randomUUID } from "node:crypto";

@Catch()
export class AgentExceptionFilter implements ExceptionFilter {
  catch(exception: unknown, host: ArgumentsHost): void {
    const response = host.switchToHttp().getResponse<Response>();
    if (exception instanceof HttpException) {
      response.status(exception.getStatus()).json(exception.getResponse());
      return;
    }
    const message = exception instanceof Error ? exception.message : "unexpected server error";
    response.status(HttpStatus.INTERNAL_SERVER_ERROR).json({
      code: "AGENT_INTERNAL_ERROR",
      message,
      requestId: `req_${randomUUID()}`,
      details: {},
    });
  }
}
