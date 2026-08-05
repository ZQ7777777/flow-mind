<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import type { ArtifactFile, CodeGenerationSummary } from "@flowmind/agent-contracts";
import { ElMessage } from "element-plus";
import "monaco-editor/esm/vs/base/browser/ui/codicons/codicon/codicon.css";
import { useWorkflowStore } from "../stores/workflow";
import QualityPanel from "./QualityPanel.vue";

interface TreeNode { label: string; path?: string; children?: TreeNode[] }

const props = defineProps<{ generation: CodeGenerationSummary }>();
const store = useWorkflowStore();
const selectedPath = ref("");
const mode = ref<"edit" | "diff">("edit");
const editorHost = ref<HTMLElement>();
const diffHost = ref<HTMLElement>();
const dirty = ref(false);
let editor: any;
let diffEditor: any;
let monaco: any;

const treeData = computed(() => buildTree(props.generation.manifest?.files || []));
const selectedMeta = computed(() => props.generation.manifest?.files.find((file) => file.relativePath === selectedPath.value));

async function select(node: TreeNode): Promise<void> {
  if (!node.path) return;
  await selectPath(node.path);
}

async function selectPath(relativePath: string): Promise<void> {
  selectedPath.value = relativePath;
  dirty.value = false;
  await store.loadGeneratedFile(relativePath);
  await ensureEditors();
  updateModels();
}

async function ensureEditors(): Promise<void> {
  if (editor || !editorHost.value || !diffHost.value) return;
  const [editorApi] = await Promise.all([
    import("monaco-editor/esm/vs/editor/editor.api.js"),
    import("monaco-editor/esm/vs/basic-languages/java/java.contribution.js"),
    import("monaco-editor/esm/vs/language/typescript/monaco.contribution.js"),
    import("monaco-editor/esm/vs/language/html/monaco.contribution.js"),
  ]);
  monaco = editorApi;
  editor = monaco.editor.create(editorHost.value, {
    value: "", language: "plaintext", automaticLayout: true, minimap: { enabled: false },
    fontSize: 13, scrollBeyondLastLine: false,
  });
  editor.onDidChangeModelContent(() => { dirty.value = true; });
  diffEditor = monaco.editor.createDiffEditor(diffHost.value, {
    automaticLayout: true, readOnly: true, minimap: { enabled: false }, fontSize: 13,
  });
}

function updateModels(): void {
  if (!editor || !monaco || !store.generatedFile || !store.generatedDiff) return;
  const language = languageFor(selectedPath.value);
  monaco.editor.setModelLanguage(editor.getModel(), language);
  editor.setValue(store.generatedFile.content);
  const original = monaco.editor.createModel(store.generatedDiff.originalContent, language);
  const modified = monaco.editor.createModel(store.generatedDiff.stagedContent, language);
  const previous = diffEditor.getModel();
  diffEditor.setModel({ original, modified });
  previous?.original?.dispose(); previous?.modified?.dispose();
  dirty.value = false;
}

async function save(): Promise<void> {
  if (!selectedPath.value || !editor) return;
  await store.saveGeneratedFile(selectedPath.value, editor.getValue());
  updateModels();
  ElMessage.success("暂存文件已保存，生成版本已更新");
}

watch(mode, () => nextTick(() => mode.value === "edit" ? editor?.layout() : diffEditor?.layout()));
watch(() => props.generation.generationRevision, () => {
  if (selectedPath.value && !dirty.value) void store.loadGeneratedFile(selectedPath.value).then(updateModels);
});

onBeforeUnmount(() => {
  editor?.dispose();
  const model = diffEditor?.getModel(); model?.original?.dispose(); model?.modified?.dispose();
  diffEditor?.dispose();
});

function buildTree(files: ArtifactFile[]): TreeNode[] {
  const roots: TreeNode[] = [];
  for (const file of files) {
    let level = roots;
    const parts = file.relativePath.split("/");
    parts.forEach((part, index) => {
      let node = level.find((item) => item.label === part);
      if (!node) { node = { label: part, ...(index === parts.length - 1 ? { path: file.relativePath } : { children: [] }) }; level.push(node); }
      if (node.children) level = node.children;
    });
  }
  return roots;
}

function languageFor(path: string): string {
  if (path.endsWith(".java")) return "java";
  if (path.endsWith(".vue")) return "html";
  if (path.endsWith(".ts")) return "typescript";
  return "plaintext";
}
</script>

<template>
  <div class="generation-panel">
    <aside class="code-tree">
      <div class="generation-meta">
        <strong>候选代码</strong>
        <el-tag size="small">revision {{ generation.generationRevision }}</el-tag>
      </div>
      <div class="code-tree-scroll">
        <el-tree :data="treeData" node-key="path" default-expand-all highlight-current @node-click="select">
          <template #default="{ data }"><span class="tree-label">{{ data.label }}</span></template>
        </el-tree>
      </div>
    </aside>
    <section class="code-editor">
      <div v-if="selectedPath" class="editor-toolbar">
        <div class="editor-toolbar-main">
          <el-tag v-if="selectedMeta" class="file-change-type" size="small" :type="selectedMeta.changeType === 'ADD' ? 'success' : 'warning'">{{ selectedMeta.changeType }}</el-tag>
          <el-radio-group v-model="mode" class="editor-mode-switch" size="small"><el-radio-button value="edit">编辑</el-radio-button><el-radio-button value="diff">Diff</el-radio-button></el-radio-group>
          <code class="editor-file-path">{{ selectedPath }}</code>
          <el-tag v-if="store.generatedDiff?.stale" size="small" type="danger">基线已变化</el-tag>
        </div>
        <el-button class="editor-save" type="primary" size="small" :disabled="!dirty || store.busy" @click="save">保存</el-button>
      </div>
      <div v-if="!selectedPath" class="editor-empty">从左侧代码树选择一个文件查看内容和差异。</div>
      <div v-show="selectedPath && mode === 'edit'" ref="editorHost" class="monaco-host"></div>
      <div v-show="selectedPath && mode === 'diff'" ref="diffHost" class="monaco-host"></div>
    </section>
    <QualityPanel :generation="generation" @select-diagnostic="selectPath" />
  </div>
</template>

<style scoped>
.generation-panel { display: grid; grid-template-columns: 240px minmax(360px, 1fr) 320px; height: 590px; min-height: 0; border: 1px solid #dfe5ed; border-radius: 12px; overflow: hidden; }
.code-tree { min-width: 0; min-height: 0; height: 100%; padding: 14px; border-right: 1px solid #dfe5ed; overflow: hidden; background: #f8fafc; display: flex; flex-direction: column; box-sizing: border-box; }
.code-tree-scroll { position: relative; width: 100%; min-width: 0; min-height: 0; flex: 1 1 0; overflow-x: auto; overflow-y: scroll; scrollbar-gutter: stable; }
.code-tree-scroll :deep(.el-tree) { width: max-content; min-width: 100%; background: transparent; }
.code-tree-scroll :deep(.el-tree-node) { width: max-content; min-width: 100%; }
.code-tree-scroll :deep(.el-tree-node__content) { width: max-content; min-width: 100%; box-sizing: border-box; padding-right: 14px; }
.generation-meta, .editor-toolbar, .editor-toolbar-main { display: flex; align-items: center; gap: 10px; }
.generation-meta { margin-bottom: 12px; }
.tree-label { font-size: 12px; }
.code-editor { min-width: 0; background: white; }
.editor-toolbar { min-height: 52px; padding: 0 12px; border-bottom: 1px solid #dfe5ed; justify-content: space-between; }
.editor-toolbar-main { min-width: 0; flex: 1; }
.editor-toolbar-main > :not(code), .editor-toolbar > .el-button { flex-shrink: 0; }
.editor-toolbar code { min-width: 0; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.monaco-host { height: 535px; }
.editor-empty { height: 535px; display: grid; place-items: center; color: #7b8794; }
@media (max-width: 1200px) { .generation-panel { grid-template-columns: 220px minmax(360px, 1fr); height: auto; } :deep(.quality-panel) { grid-column: 1 / -1; min-height: 360px; border-left: 0; border-top: 1px solid #dfe5ed; } }
</style>
