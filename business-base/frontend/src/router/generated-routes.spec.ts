import { describe, expect, it } from "vitest";
import {
  generatedBusinessFormRegistry,
  generatedRoutes,
  resolveGeneratedBusinessForm,
} from "./generated-routes";

describe("generated route and form registry", () => {
  it("registers the entry apply route and its reusable form component", async () => {
    expect(generatedRoutes).toContainEqual(expect.objectContaining({
      path: "/generated/entry-application/apply",
      name: "generated-entry-application-apply",
      meta: expect.objectContaining({ standalone: true }),
    }));
    expect(generatedBusinessFormRegistry.entry_application).toBeTypeOf("function");
    expect(await resolveGeneratedBusinessForm("entry_application")).toBeDefined();
  });

  it("returns undefined so the detail page can use its generic fallback", async () => {
    expect(await resolveGeneratedBusinessForm("unregistered_process")).toBeUndefined();
    expect(await resolveGeneratedBusinessForm()).toBeUndefined();
  });
});
