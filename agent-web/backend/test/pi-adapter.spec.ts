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
      const adapter = new PiAdapterService(database);
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
    } finally {
      database.onModuleDestroy();
      vi.doUnmock("@earendil-works/pi-coding-agent");
      vi.unstubAllEnvs();
      rmSync(root, { recursive: true, force: true });
    }
  });
});
