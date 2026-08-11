import { Injectable } from "@nestjs/common";

@Injectable()
export class PlatformSessionRegistry {
  private readonly sessions = new Map<string, string>();

  set(userId: string, cookie: string): void {
    this.sessions.set(userId, cookie);
  }

  get(userId: string): string | undefined {
    return this.sessions.get(userId);
  }

  delete(userId: string): void {
    this.sessions.delete(userId);
  }

  deleteCookie(cookie: string): void {
    for (const [userId, registered] of this.sessions) {
      if (registered === cookie) this.sessions.delete(userId);
    }
  }
}
