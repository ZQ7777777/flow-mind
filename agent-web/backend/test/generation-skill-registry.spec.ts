import { afterEach, describe, expect, it } from "vitest";
import { mkdirSync, mkdtempSync, rmSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { GenerationSkillRegistry } from "../src/generation/generation-skill-registry.service.js";
import { assertRequiredGenerationContextRead } from "../src/pi/pi-adapter.service.js";
import { assertRequiredGenerationSkillsRead } from "../src/pi/pi-adapter.service.js";

describe("GenerationSkillRegistry", () => {
  const temporaryRoots: string[] = [];
  afterEach(() => {
    delete process.env.AGENT_GENERATION_SKILL_ROOT;
    for (const root of temporaryRoots.splice(0)) rmSync(root, { recursive: true, force: true });
  });

  it("loads an immutable allowlisted project skill snapshot", () => {
    process.env.AGENT_GENERATION_SKILL_ROOT = resolve(process.cwd(), "../skills");
    const registry = new GenerationSkillRegistry();
    const snapshots = registry.loadRequired("session-1");

    expect(snapshots).toHaveLength(1);
    expect(snapshots[0]).toMatchObject({
      name: "flowmind-business-generation",
      required: true,
      priority: 100,
    });
    expect(snapshots[0].sha256).toMatch(/^[a-f0-9]{64}$/);
    expect(registry.read(snapshots, snapshots[0].name, "SKILL.md")).toContain("BusinessForm.vue");
    expect(registry.read(snapshots, snapshots[0].name, "references/generated-form-contract.md"))
      .toContain("compact, single-line collapsed selector");
    expect(registry.read(snapshots, snapshots[0].name, "references/generated-form-contract.md"))
      .toContain("square checkbox");
    expect(registry.read(snapshots, snapshots[0].name, "references/backend-api-contract.md"))
      .toContain("Generated code never creates a business-specific submit API");
  });

  it("rejects non-allowlisted skills and path traversal", () => {
    process.env.AGENT_GENERATION_SKILL_ROOT = resolve(process.cwd(), "../skills");
    const registry = new GenerationSkillRegistry();
    const snapshots = registry.loadRequired("session-2");
    expect(() => registry.read(snapshots, "unknown", "SKILL.md", "session-2")).toThrow(/not allowlisted/);
    expect(() => registry.read(snapshots, snapshots[0].name, "../SKILL.md", "session-2")).toThrow(/normalized relative path/);
    expect(() => registry.read(snapshots, snapshots[0].name, "C:\\outside.md", "session-2")).toThrow(/normalized relative path/);
    expect(() => registry.read(snapshots, snapshots[0].name, "agents/openai.yaml", "session-2")).toThrow(/only SKILL.md and references/);
  });

  it("keeps snapshot contents immutable after the source file changes", () => {
    const root = createSkillRoot("original rules");
    process.env.AGENT_GENERATION_SKILL_ROOT = root;
    const registry = new GenerationSkillRegistry();
    const snapshots = registry.loadRequired("session-snapshot");
    const reference = join(root, "flowmind-business-generation", "references", "rules.md");
    writeFileSync(reference, "new rules", "utf8");

    expect(registry.read(snapshots, "flowmind-business-generation", "references/rules.md"))
      .toBe("original rules");
    expect(snapshots[0].sha256).toMatch(/^[a-f0-9]{64}$/);
  });

  it("rejects a symbolic-link reference directory", () => {
    const root = mkdtempSync(join(tmpdir(), "flowmind-skill-link-"));
    temporaryRoots.push(root);
    const skill = join(root, "flowmind-business-generation");
    const outside = join(root, "outside");
    mkdirSync(skill, { recursive: true });
    mkdirSync(outside, { recursive: true });
    writeFileSync(join(skill, "SKILL.md"), skillFile(), "utf8");
    writeFileSync(join(outside, "rules.md"), "escaped", "utf8");
    symlinkSync(outside, join(skill, "references"), "junction");
    process.env.AGENT_GENERATION_SKILL_ROOT = root;

    expect(() => new GenerationSkillRegistry().loadRequired("session-link"))
      .toThrow(/symbolic links are forbidden/);
  });

  it("blocks completion until every required skill SKILL.md was read", () => {
    expect(() => assertRequiredGenerationSkillsRead(
      ["flowmind-business-generation"],
      new Set(),
    )).toThrow(/were not read/);
    expect(() => assertRequiredGenerationSkillsRead(
      ["flowmind-business-generation"],
      new Set(["flowmind-business-generation"]),
    )).not.toThrow();
  });

  it("blocks generation completion until every mandatory context key was read", () => {
    const required = ["skill:flowmind-business-generation:SKILL.md", "reference:REPOSITORY:golden.md"];
    expect(() => assertRequiredGenerationContextRead(required, new Set([required[0]])))
      .toThrow(/reference:REPOSITORY:golden\.md/);
    expect(() => assertRequiredGenerationContextRead(required, new Set(required))).not.toThrow();
  });

  function createSkillRoot(reference: string): string {
    const root = mkdtempSync(join(tmpdir(), "flowmind-skill-snapshot-"));
    temporaryRoots.push(root);
    const skill = join(root, "flowmind-business-generation");
    mkdirSync(join(skill, "references"), { recursive: true });
    writeFileSync(join(skill, "SKILL.md"), skillFile(), "utf8");
    writeFileSync(join(skill, "references", "rules.md"), reference, "utf8");
    return root;
  }

  function skillFile(): string {
    return "---\nname: flowmind-business-generation\ndescription: Test generation rules.\n---\n\n# Test\n";
  }
});
