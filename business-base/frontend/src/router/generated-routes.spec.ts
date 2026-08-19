import { describe, expect, it } from "vitest";
import {
  generatedBusinessFormRegistry,
  generatedRoutes,
  resolveGeneratedBusinessForm,
} from "./generated-routes";

describe("generated route and form registry", () => {
  it("preserves entry application and registers the warehouse pledge gold route", async () => {
    expect(generatedRoutes).toContainEqual(expect.objectContaining({
      path: "/generated/entry-application/apply",
      name: "generated-entry-application-apply",
      meta: expect.objectContaining({ standalone: true }),
    }));
    expect(generatedBusinessFormRegistry.entry_application).toBeTypeOf("function");
    expect(await resolveGeneratedBusinessForm("entry_application")).toBeDefined();
    expect(generatedRoutes).toContainEqual(expect.objectContaining({
      path: "/generated/warehouse-pledge/apply",
      name: "generated-warehouse-pledge-apply",
      meta: expect.objectContaining({ standalone: true }),
    }));
    expect(generatedBusinessFormRegistry.warehouse_pledge).toBeTypeOf("function");
    expect(await resolveGeneratedBusinessForm("warehouse_pledge")).toBeDefined();
  }, 15_000);

  it("returns undefined so the detail page can use its generic fallback", async () => {
    expect(await resolveGeneratedBusinessForm("unregistered_process")).toBeUndefined();
    expect(await resolveGeneratedBusinessForm()).toBeUndefined();
  });
});
