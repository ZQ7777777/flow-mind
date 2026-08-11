import { HttpStatus, Injectable } from "@nestjs/common";
import type { MockUser } from "@flowmind/agent-contracts";
import type { AgentAuthenticatedUser } from "@flowmind/agent-contracts";
import { loadConfig } from "../config.js";
import { AgentError } from "../common/agent-error.js";

@Injectable()
export class IdentityService {
  private readonly users = loadConfig().mockUsers;
  private readonly config = loadConfig();
  private readonly authenticatedUsers = new Map<string, MockUser>();

  listUsers(): MockUser[] {
    return this.users.map((user) => ({ ...user }));
  }

  resolve(userId?: string, userName?: string): MockUser {
    if (this.config.platformAuthMode === "session") {
      const authenticated = userId ? this.authenticatedUsers.get(userId) : undefined;
      if (!authenticated) {
        throw new AgentError(HttpStatus.FORBIDDEN, "AGENT_USER_FORBIDDEN", "unverified agent user");
      }
      return { ...authenticated };
    }
    const user = this.users.find((item) => item.userId === userId);
    if (!user) {
      throw new AgentError(HttpStatus.FORBIDDEN, "AGENT_USER_FORBIDDEN", "unknown mock user");
    }
    return { ...user, userName: userName?.trim() || user.userName };
  }

  rememberAuthenticated(user: AgentAuthenticatedUser): void {
    this.authenticatedUsers.set(user.userId, {
      userId: user.userId,
      userName: user.realName,
      departmentId: user.departmentId,
      departmentName: user.departmentName,
    });
  }
}
