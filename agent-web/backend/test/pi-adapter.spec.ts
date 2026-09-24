import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it, vi } from "vitest";
import { DatabaseService } from "../src/persistence/database.service.js";
import { PiAdapterService } from "../src/pi/pi-adapter.service.js";

describe("Pi adapter repair sessions", () => {
  it("starts repair in a fresh in-memory session instead of reopening generation history", async () => {
    vi.resetModules();
    const root = mkdtempSync(join(tmpdir(), "flowmind-pi-adapter-"));
    const open = vi.fn();
    const inMemory = vi.fn(() => ({ kind: "memory" }));
    const prompt = vi.fn().mockResolvedValue(undefined);
    const dispose = vi.fn();
    const session = {
      sessionId: "repair-session",
      sessionFile: undefined,
      subscribe: vi.fn(),
      prompt,
      dispose,
      abort: vi.fn(),
    };
    vi.doMock("@earendil-works/pi-coding-agent", () => ({
      ModelRuntime: {
        create: vi.fn().mockResolvedValue({
          getModel: vi.fn(() => ({ contextWindow: 128_000 })),
        }),
      },
      SessionManager: {
        open,
        create: vi.fn(),
        inMemory,
      },
      SettingsManager: { inMemory: vi.fn(() => ({ kind: "settings" })) },
      DefaultResourceLoader: class {
        async reload(): Promise<void> {}
      },
      defineTool: vi.fn(() => ({})),
      createAgentSession: vi.fn().mockResolvedValue({ session }),
    }));
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("AGENT_FAKE_PI", "false");
    vi.stubEnv("PI_MODEL", "mock/model");
    vi.stubEnv("AGENT_DATA_DIR", join(root, "data"));
    vi.stubEnv("AGENT_DB_PATH", join(root, "agent.db"));

    const database = new DatabaseService();
    try {
      const now = new Date().toISOString();
      database.db.prepare(`
        INSERT INTO agent_session (id, owner_user_id, owner_user_name, state, row_version, created_at, updated_at)
        VALUES ('session-1', 'user-sales', 'Sales User', 'CODE_REPAIRING', 0, ?, ?)
      `).run(now, now);
      database.db.prepare(`
        INSERT INTO agent_process_definition (
          id, session_id, requirement_revision, process_code, process_name, status, saga_step,
          requirement_snapshot_json, create_operation_id, save_operation_id, publish_operation_id,
          activate_operation_id, created_by, created_at, updated_at
        ) VALUES ('process-1', 'session-1', 1, 'sample', 'Sample', 'ACTIVE', 'ACTIVE', '{}',
          'create-1', 'save-1', 'publish-1', 'activate-1', 'user-sales', ?, ?)
      `).run(now, now);
      database.db.prepare(`
        INSERT INTO agent_code_generation (
          id, session_id, process_definition_record_id, requirement_revision, requirement_snapshot_json,
          business_code, business_name, status, artifact_manifest_json, generation_revision,
          created_by, created_at, updated_at
        ) VALUES ('generation-1', 'session-1', 'process-1', 1, '{}', 'sample', 'Sample',
          'REPAIRING', '{}', 1, 'user-sales', ?, ?)
      `).run(now, now);
      const modelBudget = {
        reserve: vi.fn(() => ({ reservationId: "reservation-1", reservedCny: 0.01 })),
        markBillingOutcomeUnknown: vi.fn(),
      };
      const adapter = new PiAdapterService(database, modelBudget as any);
      await adapter.runRepair(
        "generation-1",
        join(root, "staging"),
        join(root, "generation-session.jsonl"),
        "repair prompt",
        {} as any,
      );

      expect(open).not.toHaveBeenCalled();
      expect(inMemory).toHaveBeenCalledOnce();
      expect(prompt).toHaveBeenCalledWith("repair prompt");
      expect(dispose).toHaveBeenCalledOnce();
      expect(modelBudget.reserve).toHaveBeenCalledWith("user-sales", "REPAIR", "generation-1", "repair prompt");
      expect(modelBudget.markBillingOutcomeUnknown).toHaveBeenCalledWith("reservation-1");
    } finally {
      database.onModuleDestroy();
      vi.doUnmock("@earendil-works/pi-coding-agent");
      vi.unstubAllEnvs();
      rmSync(root, { recursive: true, force: true });
    }
  });
});
