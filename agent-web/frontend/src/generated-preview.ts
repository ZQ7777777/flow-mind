export interface GeneratedPreviewDocument {
  srcdoc: string;
}

const PREVIEW_SCRIPT_NONCE = "flowmind-static-preview";
const PREVIEW_INTERACTION_SCRIPT = `(function () {
  var warning = document.getElementById("preview-interaction-warning");
  function showWarning() {
    if (warning) warning.hidden = false;
  }
  function hideWarning() {
    if (warning) warning.hidden = true;
  }
  function relatedForm(control) {
    return control.closest("form") || document.querySelector("form");
  }
  document.addEventListener("submit", function (event) {
    event.preventDefault();
    showWarning();
  });
  document.addEventListener("click", function (event) {
    var target = event.target;
    if (!(target instanceof Element)) return;
    var control = target.closest("[data-preview-action]");
    if (!control) return;
    event.preventDefault();
    if (control.getAttribute("data-preview-action") === "submit") {
      showWarning();
      return;
    }
    var form = relatedForm(control);
    if (form) form.reset();
    hideWarning();
  });
})();`;

const PREVIEW_BASE_STYLES = `
:root {
  color: #17202a;
  background: #f5f7fb;
  font-family: "Segoe UI", "Microsoft YaHei", Arial, sans-serif;
}
* { box-sizing: border-box; }
body { margin: 0; padding: 18px; background: #f5f7fb; }
[hidden] { display: none !important; }
form, section, main { max-width: 100%; }
.preview-form-item { display: grid; gap: 6px; min-width: 0; }
.preview-label { color: #334155; font-size: 13px; font-weight: 650; }
input, textarea, select, button {
  min-height: 36px;
  border: 1px solid #d8dee8;
  border-radius: 6px;
  padding: 7px 10px;
  background: #fff;
  color: #17202a;
  font: inherit;
}
textarea { min-height: 88px; resize: vertical; }
button { cursor: default; font-weight: 650; }
.preview-primary-button { border-color: #2563eb; background: #2563eb; color: #fff; }
.preview-upload { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.preview-choice { display: inline-flex; align-items: center; gap: 6px; }
.preview-choice input { min-height: auto; }
.preview-alert { border: 1px solid #d8dee8; border-radius: 6px; padding: 9px 10px; background: #f8fafc; }
.preview-interaction-warning {
  position: sticky;
  top: 0;
  z-index: 10;
  margin: 0 0 12px;
  border: 1px solid #f59e0b;
  border-radius: 6px;
  padding: 9px 12px;
  background: #fffbeb;
  color: #92400e;
  font-size: 13px;
  font-weight: 650;
}
.preview-divider { height: 1px; margin: 12px 0; background: #d8dee8; }
.preview-unknown { min-height: 1px; }
`;

const DANGEROUS_TAGS = new Set([
  "script", "iframe", "object", "embed", "link", "meta", "base", "portal",
]);
const URL_ATTRIBUTES = new Set([
  "action", "formaction", "href", "poster", "src", "srcdoc", "srcset", "xlink:href",
]);
const VOID_TAGS = new Set([
  "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr",
]);

export function buildGeneratedPreviewDocument(source: string): GeneratedPreviewDocument {
  if (!source.trim()) throw new Error("暂存的 Vue 页面内容为空");
  const document = new DOMParser().parseFromString(expandSelfClosingComponents(source), "text/html");
  const template = document.querySelector("template");
  if (!(template instanceof HTMLTemplateElement)) throw new Error("暂存的 Vue 文件缺少 template 区块");

  const container = document.createElement("div");
  container.className = "generated-preview-root";
  container.append(template.content.cloneNode(true));
  transformElementPlusComponents(container, document);
  sanitizePreviewTree(container);
  replaceInterpolations(container);

  const sourceStyles = Array.from(document.querySelectorAll("style"))
    .map((style) => style.textContent || "")
    .join("\n");
  const css = sanitizeCss(sourceStyles);
  return {
    srcdoc: `<!doctype html><html lang="zh-CN"><head><meta charset="UTF-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data: blob:; font-src data:; style-src 'unsafe-inline'; script-src 'nonce-${PREVIEW_SCRIPT_NONCE}'; connect-src 'none'; form-action 'none'; base-uri 'none';"><meta name="viewport" content="width=device-width, initial-scale=1"><style>${PREVIEW_BASE_STYLES}\n${css}</style></head><body><div id="preview-interaction-warning" class="preview-interaction-warning" role="alert" hidden>当前仅为界面预览，内容未真实提交。</div>${container.outerHTML}<script nonce="${PREVIEW_SCRIPT_NONCE}">${PREVIEW_INTERACTION_SCRIPT}</script></body></html>`,
  };
}

function expandSelfClosingComponents(source: string): string {
  return source.replace(/<([A-Za-z][\w.-]*)(\s[^<>]*?)?\s*\/>/g, (match, tag: string, attributes = "") => {
    if (VOID_TAGS.has(tag.toLowerCase())) return match;
    return `<${tag}${attributes}></${tag}>`;
  });
}

function transformElementPlusComponents(root: HTMLElement, document: Document): void {
  const elements = Array.from(root.querySelectorAll("*")).reverse();
  for (const element of elements) {
    const tag = element.tagName.toLowerCase().replace(/-/g, "");
    let replacement: HTMLElement | undefined;
    if (tag === "elform") replacement = copyAs(element, document, "form");
    else if (tag === "elformitem") replacement = formItem(element, document);
    else if (tag === "elinput") replacement = inputControl(element, document);
    else if (tag === "elinputnumber") replacement = inputControl(element, document, "number");
    else if (tag === "elselect") replacement = copyAs(element, document, "select");
    else if (tag === "eloption") replacement = selectOption(element, document);
    else if (tag === "eldatepicker" || tag === "eltimepicker") replacement = dateControl(element, document, tag);
    else if (tag === "elupload") replacement = uploadControl(element, document);
    else if (tag === "elbutton") replacement = buttonControl(element, document);
    else if (tag === "elcheckbox" || tag === "elradio" || tag === "elswitch") replacement = choiceControl(element, document, tag);
    else if (tag === "elalert" || tag === "elresult") replacement = copyAs(element, document, "div", "preview-alert");
    else if (tag === "eldivider") replacement = copyAs(element, document, "div", "preview-divider");
    else if (["elrow", "elcol", "elspace", "elcard", "eltag", "eltext"].includes(tag)) {
      replacement = copyAs(element, document, tag === "eltag" || tag === "eltext" ? "span" : "div");
    } else if (isCustomElement(element)) {
      replacement = copyAs(element, document, "div", "preview-unknown");
    }
    if (replacement) element.replaceWith(replacement);
  }
}

function copyAs(
  source: Element,
  document: Document,
  tag: keyof HTMLElementTagNameMap,
  extraClass = "",
): HTMLElement {
  const target = document.createElement(tag);
  copyAttributes(source, target);
  while (source.firstChild) target.append(source.firstChild);
  if (extraClass) target.classList.add(extraClass);
  return target;
}

function formItem(source: Element, document: Document): HTMLElement {
  const target = copyAs(source, document, "div", "preview-form-item");
  const label = source.getAttribute("label") || source.getAttribute("aria-label");
  target.removeAttribute("label");
  if (label) {
    const labelElement = document.createElement("span");
    labelElement.className = "preview-label";
    labelElement.textContent = label;
    target.prepend(labelElement);
  }
  return target;
}

function inputControl(source: Element, document: Document, forcedType?: string): HTMLElement {
  const requestedType = forcedType || source.getAttribute("type") || "text";
  const target = copyAs(source, document, requestedType === "textarea" ? "textarea" : "input");
  if (target instanceof HTMLInputElement) target.type = requestedType === "password" ? "password" : requestedType;
  return target;
}

function selectOption(source: Element, document: Document): HTMLElement {
  const target = copyAs(source, document, "option");
  const label = source.getAttribute("label");
  if (!target.textContent?.trim()) target.textContent = label || "选项";
  return target;
}

function dateControl(source: Element, document: Document, tag: string): HTMLElement {
  const target = copyAs(source, document, "input") as HTMLInputElement;
  const requestedType = source.getAttribute("type") || "";
  target.type = tag === "eltimepicker" || requestedType.includes("time") ? "time" : "date";
  return target;
}

function uploadControl(source: Element, document: Document): HTMLElement {
  const target = document.createElement("div");
  target.className = `${source.getAttribute("class") || ""} preview-upload`.trim();
  const input = document.createElement("input");
  input.type = "file";
  if (source.hasAttribute("multiple")) input.multiple = true;
  const accept = source.getAttribute("accept");
  if (accept) input.accept = accept;
  const label = document.createElement("span");
  label.textContent = source.textContent?.trim() || "选择文件";
  target.append(input, label);
  return target;
}

function buttonControl(source: Element, document: Document): HTMLElement {
  const action = previewActionForControl(source);
  const target = copyAs(source, document, "button") as HTMLButtonElement;
  target.type = "button";
  if (action) target.dataset.previewAction = action;
  if (source.getAttribute("type") === "primary") target.classList.add("preview-primary-button");
  return target;
}

function choiceControl(source: Element, document: Document, tag: string): HTMLElement {
  const target = document.createElement("label");
  target.className = `${source.getAttribute("class") || ""} preview-choice`.trim();
  const input = document.createElement("input");
  input.type = tag === "elradio" ? "radio" : "checkbox";
  const text = document.createElement("span");
  text.textContent = source.textContent?.trim() || source.getAttribute("label") || "";
  target.append(input, text);
  return target;
}

function copyAttributes(source: Element, target: Element): void {
  for (const attribute of Array.from(source.attributes)) {
    const name = attribute.name.toLowerCase();
    if (["v-if", "v-else-if", "v-else", "v-show"].includes(name)) {
      target.setAttribute("hidden", "");
      continue;
    }
    if (shouldRemoveAttribute(name)) continue;
    target.setAttribute(attribute.name, attribute.value);
  }
}

function isCustomElement(element: Element): boolean {
  return element.localName.includes("-") || element.localName.startsWith("el");
}

function sanitizePreviewTree(root: HTMLElement): void {
  for (const element of Array.from(root.querySelectorAll("*"))) {
    const tag = element.tagName.toLowerCase();
    if (DANGEROUS_TAGS.has(tag)) {
      element.remove();
      continue;
    }
    if (tag === "button" || tag === "input") {
      const defaultSubmit = tag === "button" && !element.hasAttribute("type") && Boolean(element.closest("form"));
      const action = element.getAttribute("data-preview-action") || previewActionForControl(element, defaultSubmit);
      if (action) element.setAttribute("data-preview-action", action);
    }
    let conditional = false;
    for (const attribute of Array.from(element.attributes)) {
      const name = attribute.name.toLowerCase();
      if (["v-if", "v-else-if", "v-else", "v-show"].includes(name)) conditional = true;
      if (shouldRemoveAttribute(name)) {
        element.removeAttribute(attribute.name);
      } else if (attribute.value.includes("{{")) {
        element.setAttribute(attribute.name, replaceInterpolationText(attribute.value));
      }
    }
    if (conditional) element.setAttribute("hidden", "");
    if (tag === "form") {
      element.setAttribute("novalidate", "");
      element.removeAttribute("method");
      element.removeAttribute("target");
    }
    if (tag === "button" || (tag === "input" && ["submit", "reset"].includes((element.getAttribute("type") || "").toLowerCase()))) {
      element.setAttribute("type", "button");
    }
  }
}

function previewActionForControl(element: Element, defaultSubmit = false): "submit" | "reset" | undefined {
  const isElementPlusButton = element.localName.replace(/-/g, "") === "elbutton";
  const nativeType = (
    element.getAttribute("native-type") ||
    (!isElementPlusButton ? element.getAttribute("type") : "") ||
    ""
  ).toLowerCase();
  const handler = [
    element.getAttribute("@click"),
    element.getAttribute("v-on:click"),
  ].filter(Boolean).join(" ").toLowerCase();
  const label = [
    element.getAttribute("value"),
    element.textContent,
  ].filter(Boolean).join(" ").toLowerCase();
  if (nativeType === "reset" || /(reset|clear)/.test(handler) || /\b(reset|clear)\b|重置|清空/.test(label)) return "reset";
  if (nativeType === "submit" || defaultSubmit || /(submit|apply|start)/.test(handler) || /\b(submit|apply|start)\b|提交|发起|确认提交/.test(label)) return "submit";
  return undefined;
}

function shouldRemoveAttribute(name: string): boolean {
  return name.startsWith("on") || name.startsWith("@") || name.startsWith(":") ||
    name.startsWith("#") || name.startsWith("v-") || URL_ATTRIBUTES.has(name);
}

function replaceInterpolations(root: HTMLElement): void {
  const showText = root.ownerDocument.defaultView?.NodeFilter.SHOW_TEXT ?? 4;
  const walker = root.ownerDocument.createTreeWalker(root, showText);
  const nodes: Text[] = [];
  while (walker.nextNode()) nodes.push(walker.currentNode as Text);
  for (const node of nodes) node.textContent = replaceInterpolationText(node.textContent || "");
}

function replaceInterpolationText(value: string): string {
  return value.replace(/{{[\s\S]*?}}/g, "—");
}

function sanitizeCss(css: string): string {
  return css
    .replace(/@import\s+(?:url\([^)]*\)|["'][^"']*["'])[^;]*;/gi, "")
    .replace(/url\(\s*(["']?)(?!data:|blob:)[^)]*\1\s*\)/gi, "none")
    .replace(/<\/style/gi, "<\\/style");
}
