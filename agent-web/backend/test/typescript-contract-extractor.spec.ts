import { describe, expect, it } from "vitest";
import { extractTypeScriptContract } from "../src/generation/typescript-contract-extractor.js";

describe("TypeScript contract extractor", () => {
  it("extracts exported type and function signatures without implementation bodies", () => {
    const snapshot = extractTypeScriptContract("reference.ts", `
      export interface Account { accountNo: string; }
      export type Result = Account | undefined;
      export async function loadAccount(accountNo: string): Promise<Result> { throw new Error(accountNo); }
      export const searchAccounts = (keyword: string): Promise<Account[]> => Promise.resolve([]);
      function hidden(): void {}
    `);

    expect(snapshot.declarations).toEqual([
      expect.objectContaining({ kind: "INTERFACE", name: "Account", signature: expect.stringContaining("accountNo: string") }),
      expect.objectContaining({ kind: "TYPE", name: "Result" }),
      { kind: "FUNCTION", name: "loadAccount", signature: "loadAccount(accountNo: string): Promise<Result>" },
      { kind: "FUNCTION", name: "searchAccounts", signature: "searchAccounts(keyword: string): Promise<Account[]>" },
    ]);
    expect(JSON.stringify(snapshot.declarations)).not.toContain("throw new Error");
    expect(JSON.stringify(snapshot.declarations)).not.toContain("hidden");
  });

  it("extracts Vue defineProps generic contracts", () => {
    const snapshot = extractTypeScriptContract("Shell.vue", `<script setup lang="ts">
      defineProps<{ processCode: string; disabled?: boolean }>();
    </script><template><slot /></template>`);
    expect(snapshot.declarations).toContainEqual({
      kind: "PROPS",
      name: "defineProps",
      signature: "{ processCode: string; disabled?: boolean }",
    });
  });
});
