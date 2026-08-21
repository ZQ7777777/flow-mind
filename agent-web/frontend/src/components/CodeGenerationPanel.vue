<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import type { ArtifactFile, CodeGenerationSummary } from "@flowmind/agent-contracts";
import { ElMessage } from "element-plus";
import "monaco-editor/esm/vs/base/browser/ui/codicons/codicon/codicon.css";
import { useWorkflowStore } from "../stores/workflow";
import { useResizableCodePanels, type CodePanelSide } from "../composables/useResizableCodePanels";
import QualityPanel from "./QualityPanel.vue";
import QualityProgress from "./QualityProgress.vue";

interface TreeNode { label: string; path?: string; children?: TreeNode[] }

const props = defineProps<{ generation: CodeGenerationSummary }>();
const store = useWorkflowStore();
const pipelineActive = computed(() => ["CODE_VERIFYING", "CODE_REVIEWING", "CODE_REPAIRING"].includes(store.state || ""));
const selectedPath = ref("");
const mode = ref<"edit" | "diff">("edit");
const editorHost = ref<HTMLElement>();
const diffHost = ref<HTMLElement>();
const dirty = ref(false);
const {
  panel,
  resizingSide,
  gridStyle,
  separatorValueNow,
  startResize,
  resizeByKeyboard,
} = useResizableCodePanels();
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
  mode.value = "edit";
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
    import("monaco-editor/esm/vs/basic-languages/typescript/typescript.contribution.js"),
    import("monaco-editor/esm/vs/basic-languages/html/html.contribution.js"),
    import("monaco-editor/esm/vs/language/typescript/monaco.contribution.js"),
    import("monaco-editor/esm/vs/language/html/monaco.contribution.js"),
  ]);
  monaco = editorApi;
  registerVueLanguage(monaco);
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

watch(mode, () => nextTick(() => {
  if (mode.value === "edit") editor?.layout();
  else if (mode.value === "diff") diffEditor?.layout();
}));
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
  if (path.endsWith(".vue")) return "vue";
  if (path.endsWith(".ts")) return "typescript";
  return "plaintext";
}

let vueRegistered = false;
function registerVueLanguage(m: any): void {
  if (vueRegistered || !m.languages?.register) return;
  vueRegistered = true;
  m.languages.register({ id: "vue", extensions: [".vue"], aliases: ["Vue", "vue"] });
  m.languages.setMonarchTokensProvider("vue", {
    defaultToken: "",
    tokenPostfix: "",
    tokenizer: {
      root: [
        [/<\?[\s\S]*?\?>/, "comment"],
        [/<!--[\s\S]*?-->/, "comment"],
        [/<(template|script|style)\b/, { token: "type.identifier", next: "@sfcBlock.$1" }],
        [/<\/?[a-zA-Z][\w-]*/, { token: "type.identifier", next: "@tag" }],
        [/[^<]+/, ""],
      ],
      tag: [
        [/[a-zA-Z-]+/, "attribute.name"],
        [/=/, "delimiter"],
        [/"[^"]*"/, "string.value"],
        [/'[^']*'/, "string.value"],
        [/`[^`]*`/, "string.value"],
        [/>/, { token: "type.identifier", next: "@pop" }],
        [/\s+/, ""],
      ],
      "sfcBlock.template": [
        [/<\/template>/, { token: "type.identifier", next: "@pop" }],
        [/<!--[\s\S]*?-->/, "comment"],
        [/<\/?[a-zA-Z][\w-]*/, { token: "type.identifier", next: "@tag" }],
        [/[^<]+/, ""],
      ],
      "sfcBlock.script": [
        [/<\/script>/, { token: "type.identifier", next: "@pop" }],
        [/\/\/.*$/, "comment"],
        [/\/\*[\s\S]*?\*\//, "comment"],
        [/"[^"]*"|'[^']*'|`[^`]*`/, "string"],
        [/\b(import|from|export|default|const|let|var|function|return|if|else|for|while|class|extends|new|interface|type|enum|public|private|readonly|async|await|void|number|string|boolean|any|true|false|null|undefined)\b/, "keyword"],
        [/[A-Z][\w]*/, "type.identifier"],
        [/[^<]/, ""],
      ],
      "sfcBlock.style": [
        [/<\/style>/, { token: "type.identifier", next: "@pop" }],
        [/\/\*[\s\S]*?\*\//, "comment"],
        [/[.#:][\w-]+/, "attribute.name"],
        [/[^<]/, ""],
      ],
    },
  });
}

function resize(side: CodePanelSide, event: PointerEvent): void {
  startResize(side, event);
}

function resizeWithKeyboard(side: CodePanelSide, event: KeyboardEvent): void {
  resizeByKeyboard(side, event);
}
</script>

<template>
  <div ref="panel" class="generation-panel" :style="gridStyle">
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
    <div
      :class="['code-panel-divider', { dragging: resizingSide === 'tree' }]"
      role="separator"
      aria-label="调整代码树宽度"
      aria-orientation="vertical"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-valuenow="separatorValueNow('tree')"
      tabindex="0"
      @pointerdown="resize('tree', $event)"
      @keydown="resizeWithKeyboard('tree', $event)"
    ></div>
    <section class="code-editor">
      <div class="editor-toolbar">
        <div class="editor-toolbar-main">
          <el-tag v-if="selectedMeta" class="file-change-type" size="small" :type="selectedMeta.changeType === 'ADD' ? 'success' : 'warning'">{{ selectedMeta.changeType }}</el-tag>
          <el-radio-group v-model="mode" class="editor-mode-switch" size="small">
            <el-radio-button value="edit" :disabled="!selectedPath">编辑</el-radio-button>
            <el-radio-button value="diff" :disabled="!selectedPath">Diff</el-radio-button>
          </el-radio-group>
          <code v-if="selectedPath" class="editor-file-path">{{ selectedPath }}</code>
          <el-tag v-if="store.generatedDiff?.stale" size="small" type="danger">基线已变化</el-tag>
        </div>
        <el-button v-if="mode === 'edit' && selectedPath" class="editor-save" type="primary" size="small" :disabled="!dirty || store.busy" @click="save">保存</el-button>
      </div>
      <div v-if="!selectedPath" class="editor-empty">从左侧代码树选择一个文件查看内容和差异。</div>
      <div v-show="selectedPath && mode === 'edit'" ref="editorHost" class="monaco-host"></div>
      <div v-show="selectedPath && mode === 'diff'" ref="diffHost" class="monaco-host"></div>
    </section>
    <div
      :class="['code-panel-divider', { dragging: resizingSide === 'quality' }]"
      role="separator"
      aria-label="调整质量门禁宽度"
      aria-orientation="vertical"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-valuenow="separatorValueNow('quality')"
      tabindex="0"
      @pointerdown="resize('quality', $event)"
      @keydown="resizeWithKeyboard('quality', $event)"
    ></div>
    <QualityProgress v-if="pipelineActive" />
    <QualityPanel v-else :generation="generation" @select-diagnostic="selectPath" />
  </div>
</template>

<style scoped>
.generation-panel { display: grid; grid-template-columns: 240px 12px minmax(250px, 1fr) 12px 320px; grid-template-rows: minmax(0, 1fr); height: 590px; min-height: 0; border: 1px solid #dfe5ed; border-radius: 12px; overflow: hidden; }
.code-tree { min-width: 0; min-height: 0; height: 100%; padding: 14px; overflow: hidden; background: #f8fafc; display: flex; flex-direction: column; box-sizing: border-box; }
.code-tree-scroll { position: relative; width: 100%; min-width: 0; min-height: 0; flex: 1 1 0; overflow-x: auto; overflow-y: auto; scrollbar-gutter: stable; }
.code-tree-scroll :deep(.el-tree) { width: max-content; min-width: 100%; background: transparent; }
.code-tree-scroll :deep(.el-tree-node) { width: max-content; min-width: 100%; }
.code-tree-scroll :deep(.el-tree-node__content) { width: max-content; min-width: 100%; box-sizing: border-box; padding-right: 14px; }
.generation-meta, .editor-toolbar, .editor-toolbar-main { display: flex; align-items: center; gap: 10px; }
.generation-meta { margin-bottom: 12px; }
.tree-label { font-size: 12px; }
.code-editor { min-width: 0; min-height: 0; overflow: hidden; background: white; display: flex; flex-direction: column; }
.editor-toolbar { min-height: 52px; padding: 0 12px; border-bottom: 1px solid #dfe5ed; justify-content: space-between; }
.editor-toolbar-main { min-width: 0; flex: 1; }
.editor-toolbar-main > :not(code), .editor-toolbar > .el-button { flex-shrink: 0; }
.editor-toolbar code { min-width: 0; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.monaco-host { min-height: 0; flex: 1; }
.editor-empty { min-height: 0; flex: 1; display: grid; place-items: center; color: #7b8794; }
:deep(.quality-panel) { min-height: 0; height: 100%; overflow: hidden; border-left: 0; }
.code-panel-divider { position: relative; cursor: col-resize; touch-action: none; outline: none; background: #f8fafc; }
.code-panel-divider::before { content: ""; position: absolute; inset: 0 4px; background: #dfe5ed; transition: background .15s ease, box-shadow .15s ease; }
.code-panel-divider:hover::before, .code-panel-divider:focus-visible::before, .code-panel-divider.dragging::before { background: #275de7; box-shadow: 0 0 0 2px rgba(39,93,231,.12); }
:global(body.is-resizing-code-panels) { cursor: col-resize; user-select: none; }
</style>
