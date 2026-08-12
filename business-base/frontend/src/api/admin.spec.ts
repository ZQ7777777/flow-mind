import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  deleteInstance, fetchAdminInstances, fetchInstanceTrace, fetchProcessDefinitionOptions, operateDefinition, saveDefinitionGraph,
} from "./admin";

describe("admin api", () => {
  beforeEach(() => vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ records: [], total: 0 }), {
    status: 200, headers: { "Content-Type": "application/json" },
  }))));

  it("serializes definition graph and lifecycle requests under the admin namespace", async () => {
    await saveDefinitionGraph("definition / 1", { operationId: "op-save", nodes: [], edges: [], formFields: [], attachmentConfigs: [] });
    await operateDefinition("definition / 1", "activate", "op-activate");
    const calls = vi.mocked(fetch).mock.calls;
    expect(calls[0][0]).toBe("/api/admin/process-definitions/definition%20%2F%201/graph");
    expect(calls[0][1]).toMatchObject({ method: "PUT", body: expect.stringContaining("op-save") });
    expect(calls[1][0]).toBe("/api/admin/process-definitions/definition%20%2F%201/activate");
  });

  it("scopes instance queries and destructive requests to the selected instance", async () => {
    await fetchAdminInstances({ pageNo: 2, pageSize: 20, processCode: "entry" });
    await fetchInstanceTrace("instance/1", "audit-logs", 3, 20);
    await deleteInstance("instance/1", "op-delete");
    const calls = vi.mocked(fetch).mock.calls;
    expect(calls[0][0]).toContain("/api/admin/process-instances?pageNo=2&pageSize=20&processCode=entry");
    expect(calls[1][0]).toBe("/api/admin/process-instances/instance%2F1/audit-logs?pageNo=3&pageSize=20");
    expect(calls[2][1]).toMatchObject({ method: "DELETE", body: '{"operationId":"op-delete"}' });
  });

  it("loads administrator process-definition organization options", async () => {
    await fetchProcessDefinitionOptions();
    expect(vi.mocked(fetch).mock.calls[0][0]).toBe("/api/admin/process-definition-options");
  });
});
