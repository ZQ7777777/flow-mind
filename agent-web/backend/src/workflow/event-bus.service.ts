import { Injectable } from "@nestjs/common";

export interface WorkflowEvent {
  type: string;
  data: unknown;
}

@Injectable()
export class EventBusService {
  private readonly listeners = new Map<string, Set<(event: WorkflowEvent) => void>>();

  subscribe(sessionId: string, listener: (event: WorkflowEvent) => void): () => void {
    const sessionListeners = this.listeners.get(sessionId) || new Set();
    sessionListeners.add(listener);
    this.listeners.set(sessionId, sessionListeners);
    return () => {
      sessionListeners.delete(listener);
      if (sessionListeners.size === 0) this.listeners.delete(sessionId);
    };
  }

  publish(sessionId: string, event: WorkflowEvent): void {
    for (const listener of this.listeners.get(sessionId) || []) listener(event);
  }
}
