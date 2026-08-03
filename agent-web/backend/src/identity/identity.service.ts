import { HttpStatus, Injectable } from "@nestjs/common";
import type { MockUser } from "@flowmind/agent-contracts";
import { loadConfig } from "../config.js";
import { AgentError } from "../common/agent-error.js";

@Injectable()
export class IdentityService {
  private readonly users = loadConfig().mockUsers;

  listUsers(): MockUser[] {
    return this.users.map((user) => ({ ...user }));
  }

  resolve(userId?: string, userName?: string): MockUser {
    const user = this.users.find((item) => item.userId === userId);
    if (!user) {
      throw new AgentError(HttpStatus.FORBIDDEN, "AGENT_USER_FORBIDDEN", "unknown mock user");
    }
    return { ...user, userName: userName?.trim() || user.userName };
  }
}
