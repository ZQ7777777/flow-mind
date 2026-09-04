import ts from "typescript";
import { parse as parseVueSfc } from "@vue/compiler-sfc";
import type { TypeScriptContractDeclaration, TypeScriptContractSnapshot } from "@flowmind/agent-contracts";
import { sha256 } from "./target-contract.service.js";

export function extractTypeScriptContract(relativePath: string, content: string): TypeScriptContractSnapshot {
  const script = relativePath.endsWith(".vue") ? vueScript(relativePath, content) : content;
  const source = ts.createSourceFile(relativePath, script, ts.ScriptTarget.ES2022, true, ts.ScriptKind.TS);
  const declarations: TypeScriptContractDeclaration[] = [];

  const visit = (node: ts.Node): void => {
    if (ts.isFunctionDeclaration(node) && node.name && exported(node)) {
      declarations.push({ kind: "FUNCTION", name: node.name.text, signature: functionSignature(node, source) });
    } else if (ts.isInterfaceDeclaration(node) && exported(node)) {
      declarations.push({ kind: "INTERFACE", name: node.name.text, signature: normalize(node.getText(source)) });
    } else if (ts.isTypeAliasDeclaration(node) && exported(node)) {
      declarations.push({ kind: "TYPE", name: node.name.text, signature: normalize(node.getText(source)) });
    } else if (ts.isVariableStatement(node) && exported(node)) {
      for (const declaration of node.declarationList.declarations) {
        if (!ts.isIdentifier(declaration.name) || !declaration.initializer
          || (!ts.isArrowFunction(declaration.initializer) && !ts.isFunctionExpression(declaration.initializer))) continue;
        const callable = declaration.initializer;
        const parameters = callable.parameters.map((item) => item.getText(source)).join(", ");
        const returnType = callable.type ? `: ${callable.type.getText(source)}` : "";
        declarations.push({ kind: "FUNCTION", name: declaration.name.text, signature: normalize(`${declaration.name.text}(${parameters})${returnType}`) });
      }
    } else if (ts.isCallExpression(node) && ts.isIdentifier(node.expression)
      && node.expression.text === "defineProps" && node.typeArguments?.length === 1) {
      declarations.push({ kind: "PROPS", name: "defineProps", signature: normalize(node.typeArguments[0].getText(source)) });
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return { relativePath, sha256: sha256(content), declarations: uniqueDeclarations(declarations) };
}

function vueScript(relativePath: string, content: string): string {
  const descriptor = parseVueSfc(content, { filename: relativePath }).descriptor;
  return [descriptor.script?.content, descriptor.scriptSetup?.content].filter(Boolean).join("\n");
}

function exported(node: ts.Node & { modifiers?: ts.NodeArray<ts.ModifierLike> }): boolean {
  return Boolean(node.modifiers?.some(({ kind }) => kind === ts.SyntaxKind.ExportKeyword));
}

function functionSignature(node: ts.FunctionDeclaration, source: ts.SourceFile): string {
  const typeParameters = node.typeParameters?.length ? `<${node.typeParameters.map((item) => item.getText(source)).join(", ")}>` : "";
  const parameters = node.parameters.map((item) => item.getText(source)).join(", ");
  const returnType = node.type ? `: ${node.type.getText(source)}` : "";
  return normalize(`${node.name!.text}${typeParameters}(${parameters})${returnType}`);
}

function normalize(value: string): string {
  return value.replace(/\s+/g, " ").trim();
}

function uniqueDeclarations(items: TypeScriptContractDeclaration[]): TypeScriptContractDeclaration[] {
  const seen = new Set<string>();
  return items.filter((item) => {
    const key = `${item.kind}:${item.name}:${item.signature}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}
