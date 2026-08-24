import fs from "node:fs/promises";
import { Presentation, PresentationFile } from "@oai/artifact-tool";

const OUT = "C:/Users/zq/Desktop/resume/flow-mind/outputs/张琦-实习成果汇报.pptx";
const PREVIEW_DIR = "C:/Users/zq/Desktop/resume/flow-mind/outputs/intern_report_build/artifact-preview";

const W = 1280;
const H = 720;
const C = {
  paper: "#FAFAF8",
  ink: "#0A0A0A",
  grey1: "#F0F0EE",
  grey2: "#D4D4D2",
  grey3: "#737373",
  accent: "#002FA7",
  white: "#FFFFFF",
};
const FONT = "Microsoft YaHei UI";
const LATIN = "Arial";
const MONO = "Consolas";

const deck = Presentation.create({ slideSize: { width: W, height: H } });

function addShape(slide, { x, y, w, h, fill = "none", line = "none", lineWidth = 0, name, geometry = "rect" }) {
  return slide.shapes.add({
    geometry,
    name,
    position: { left: x, top: y, width: w, height: h },
    fill,
    line: { style: "solid", fill: line, width: lineWidth },
  });
}

function addLine(slide, x1, y1, x2, y2, color = C.grey2, width = 1, name) {
  return slide.shapes.add({
    geometry: "line",
    name,
    position: { left: x1, top: y1, width: x2 - x1, height: y2 - y1 },
    fill: "none",
    line: { style: "solid", fill: color, width },
  });
}

function addText(slide, text, { x, y, w, h, size = 24, color = C.ink, bold = false, font = FONT, align = "left", valign = "top", lineSpacing = 1.15, name, fill = "none", insets = { top: 0, right: 0, bottom: 0, left: 0 } }) {
  const box = slide.shapes.add({
    geometry: "textbox",
    name,
    position: { left: x, top: y, width: w, height: h },
    fill,
    line: { style: "solid", fill: "none", width: 0 },
  });
  box.text = text;
  box.text.style = {
    fontSize: size,
    color,
    bold,
    typeface: font,
    alignment: align,
    verticalAlignment: valign,
    lineSpacing,
    autoFit: "shrinkText",
    insets,
  };
  return box;
}

function addRichText(slide, runs, opts) {
  const box = addText(slide, "", opts);
  box.text.set([runs]);
  box.text.style = {
    fontSize: opts.size ?? 24,
    color: opts.color ?? C.ink,
    bold: opts.bold ?? false,
    typeface: opts.font ?? FONT,
    alignment: opts.align ?? "left",
    verticalAlignment: opts.valign ?? "top",
    lineSpacing: opts.lineSpacing ?? 1.15,
    autoFit: "shrinkText",
    insets: opts.insets ?? { top: 0, right: 0, bottom: 0, left: 0 },
  };
  return box;
}

function addChrome(slide, page, total, section, dark = false) {
  const color = dark ? "#FFFFFFB3" : C.grey3;
  addText(slide, section.toUpperCase(), { x: 64, y: 35, w: 520, h: 22, size: 14, color, bold: true, font: MONO, name: `chrome-${page}` });
  addText(slide, `${String(page).padStart(2, "0")} / ${String(total).padStart(2, "0")}`, { x: 1060, y: 35, w: 156, h: 22, size: 14, color, bold: true, font: MONO, align: "right", name: `page-${page}` });
}

function addSectionTitle(slide, kicker, title, { color = C.ink, titleSize = 50, y = 82, h = 120, w = 1080 } = {}) {
  addText(slide, kicker.toUpperCase(), { x: 64, y, w: 520, h: 24, size: 14, color: color === C.ink ? C.grey3 : "#FFFFFFB3", bold: true, font: MONO });
  addText(slide, title, { x: 64, y: y + 36, w, h, size: titleSize, color, font: FONT, lineSpacing: 0.96 });
}

function addNotes(slide, { purpose, talk, transition, minutes, sources = [] }) {
  const lines = [
    `【本页目的】${purpose}`,
    `【建议时长】${minutes.toFixed(1)} 分钟`,
    "【讲述要点】",
    ...talk.map((x) => `- ${x}`),
    `【转场】${transition}`,
  ];
  if (sources.length) {
    lines.push("", "[Sources]", ...sources.map((x) => `- ${x}`));
  }
  slide.speakerNotes.textFrame.setText(lines.join("\n"));
  slide.speakerNotes.setVisible(true);
}

function addBulletList(slide, items, { x, y, w, size = 22, gap = 40, color = C.ink, bulletColor = C.accent, name = "bullets" }) {
  items.forEach((item, i) => {
    const yy = y + i * gap;
    addShape(slide, { x, y: yy + 10, w: 9, h: 9, fill: bulletColor, name: `${name}-sq-${i}` });
    addText(slide, item, { x: x + 22, y: yy, w: w - 22, h: gap - 3, size, color, name: `${name}-${i}` });
  });
}

// 01 · Cover / S01
{
  const slide = deck.slides.add();
  slide.background.fill = C.accent;
  addChrome(slide, 1, 10, "Internship Review · Suzhou Branch", true);
  addText(slide, "实习成果汇报", { x: 64, y: 154, w: 1040, h: 100, size: 82, color: C.white, font: FONT, name: "cover-title" });
  addRichText(slide, [
    { run: "把 Agent 能力", textStyle: { color: C.white, typeface: FONT } },
    { run: "变成可交付的工程闭环", textStyle: { color: C.white, italic: true, typeface: FONT } },
  ], { x: 64, y: 286, w: 1120, h: 78, size: 42, color: C.white, font: FONT, name: "cover-subtitle" });
  addLine(slide, 64, 536, 1216, 536, "#FFFFFF66", 1);
  addText(slide, "张琦  /  实习生 B", { x: 64, y: 562, w: 430, h: 34, size: 23, color: C.white, bold: true });
  addText(slide, "苏州大学 · 计算机科学与技术 · 24岁", { x: 64, y: 606, w: 620, h: 32, size: 19, color: "#FFFFFFD9" });
  addText(slide, "2026.08", { x: 1030, y: 604, w: 186, h: 30, size: 18, color: "#FFFFFFB3", font: MONO, align: "right" });
  addNotes(slide, {
    purpose: "以工程化闭环定调，不从功能清单开场。",
    minutes: 0.5,
    talk: [
      "大家好，我是张琦，本次汇报聚焦实习期间做成的工程闭环。",
      "主线是 Agent Web，Platform 与 Business Base 作为运行时和业务落地支撑。",
      "我会重点讲结果、关键判断，以及下一步可以继续优化的方向。",
    ],
    transition: "先用一页说明我的背景和这段实习中的角色定位。",
    sources: ["User-provided profile; repository scope in agent-web/AGENTS.md and platform/AGENTS.md."],
  });
}

// 02 · Split Statement / S03
{
  const slide = deck.slides.add();
  slide.background.fill = C.paper;
  addChrome(slide, 2, 10, "Profile · Role");
  addShape(slide, { x: 0, y: 0, w: 474, h: 720, fill: C.ink, name: "profile-left" });
  addText(slide, "张琦", { x: 64, y: 132, w: 340, h: 84, size: 66, color: C.white, font: FONT });
  addText(slide, "24", { x: 64, y: 250, w: 280, h: 145, size: 118, color: C.white, font: LATIN });
  addText(slide, "岁 / AGE", { x: 276, y: 324, w: 120, h: 32, size: 17, color: "#FFFFFF99", bold: true, font: MONO });
  addLine(slide, 64, 492, 410, 492, "#FFFFFF55", 1);
  addText(slide, "苏州大学", { x: 64, y: 518, w: 300, h: 36, size: 25, color: C.white });
  addText(slide, "计算机科学与技术", { x: 64, y: 566, w: 340, h: 34, size: 20, color: "#FFFFFFCC" });

  addText(slide, "实习角色不是“写一个模块”", { x: 538, y: 120, w: 640, h: 62, size: 42, color: C.ink });
  addText(slide, "而是把模型能力、平台能力和业务落地串成一条可验证的路径。", { x: 538, y: 202, w: 620, h: 92, size: 27, color: C.grey3, lineSpacing: 1.28 });
  addLine(slide, 538, 350, 1184, 350, C.grey2, 1);
  addText(slide, "主责", { x: 538, y: 382, w: 108, h: 30, size: 14, color: C.accent, bold: true, font: MONO });
  addText(slide, "Agent Web：需求 → 流程 → 生成 → 验证 → 写入", { x: 660, y: 378, w: 520, h: 38, size: 22, color: C.ink });
  addText(slide, "协同", { x: 538, y: 442, w: 108, h: 30, size: 14, color: C.grey3, bold: true, font: MONO });
  addText(slide, "Platform 运行时 + Business Base 业务底座", { x: 660, y: 438, w: 520, h: 38, size: 22, color: C.ink });
  addText(slide, "方法", { x: 538, y: 502, w: 108, h: 30, size: 14, color: C.grey3, bold: true, font: MONO });
  addText(slide, "契约先行、测试兜底、失败可恢复", { x: 660, y: 498, w: 520, h: 38, size: 22, color: C.ink });
  addNotes(slide, {
    purpose: "完成自我介绍，并把个人角色定义为跨层工程闭环建设者。",
    minutes: 0.7,
    talk: [
      "我来自苏州大学计算机科学与技术专业，今年24岁。",
      "实习中主责 Agent Web，同时参与 Platform 与 Business Base 的接口、运行时和业务落地。",
      "这段经历让我从单模块开发转向跨前后端、跨系统的交付视角。",
    ],
    transition: "下面用一张工作版图说明汇报重点和各条线之间的关系。",
    sources: ["User-provided profile; agent-web/AGENTS.md; platform/AGENTS.md."],
  });
}

// 03 · Four Cards / S19
{
  const slide = deck.slides.add();
  slide.background.fill = C.grey1;
  addChrome(slide, 3, 10, "Scope · Contribution Map");
  addSectionTitle(slide, "Work Map", "一条主线，三类支撑", { titleSize: 50 });
  addShape(slide, { x: 64, y: 233, w: 82, h: 4, fill: C.accent });
  const cols = [
    { n: "70%", t: "Agent Web", d: "自然语言需求、流程定义、代码生成与质量闭环", accent: true },
    { n: "01", t: "Platform", d: "运行时契约、幂等事务、任务与实例能力" },
    { n: "02", t: "Business Base", d: "业务大厅、通用发起、管理端与参考数据" },
    { n: "03", t: "Integration", d: "跨层契约对齐、失败回退与端到端验收" },
  ];
  const x0 = 64;
  const colW = 288;
  cols.forEach((c, i) => {
    const x = x0 + i * colW;
    if (i > 0) addLine(slide, x, 286, x, 632, C.grey2, 1);
    addText(slide, c.n, { x: x + 18, y: 282, w: 240, h: 70, size: 51, color: c.accent ? C.accent : C.ink, font: LATIN });
    addText(slide, c.t, { x: x + 18, y: 376, w: 242, h: 44, size: 27, color: C.ink });
    addText(slide, c.d, { x: x + 18, y: 450, w: 242, h: 108, size: 20, color: C.grey3, lineSpacing: 1.35 });
  });
  addText(slide, "注：70% / 30% 为本次汇报篇幅分配，非代码量统计。", { x: 64, y: 648, w: 720, h: 26, size: 14, color: C.grey3, font: MONO });
  addNotes(slide, {
    purpose: "明确70/30叙事结构，并展示工作并非三个孤立模块。",
    minutes: 0.8,
    talk: [
      "本次汇报约70%放在 Agent Web，因为它是我的主要工作主线。",
      "Platform 提供流程运行时，Business Base 提供业务使用面，Integration 负责把跨层问题真正收口。",
      "我不把提交数量直接当成果，后面会用可交付能力和关键设计判断说明价值。",
    ],
    transition: "先进入主线：Agent Web 如何从自然语言走到可验证产物。",
    sources: ["Git log author=zhangqi@test.com; agent-web/README.md; business-base/.flowmind/generation-target.json."],
  });
}

// 04 · Horizontal Timeline / S11
{
  const slide = deck.slides.add();
  slide.background.fill = C.paper;
  addChrome(slide, 4, 10, "Agent Web · End-to-End");
  addSectionTitle(slide, "Core Result 01", "把自然语言需求推进到可交付代码", { titleSize: 48 });
  const yAxis = 408;
  addLine(slide, 108, yAxis, 1172, yAxis, C.grey2, 2);
  const nodes = [
    ["01", "需求对话", "结构化需求"],
    ["02", "人工门禁", "确认 revision"],
    ["03", "流程预览", "创建/校验"],
    ["04", "发布激活", "失败可回退"],
    ["05", "前端生成", "固定 5 / 7 文件"],
    ["06", "质量确认", "diff 后安全写入"],
  ];
  nodes.forEach((n, i) => {
    const x = 110 + i * 212;
    addShape(slide, { x: x - 6, y: yAxis - 6, w: 12, h: 12, fill: i === 5 ? C.accent : C.ink, name: `timeline-node-${i}` });
    const above = i % 2 === 0;
    addText(slide, n[0], { x: x - 44, y: above ? 310 : 441, w: 88, h: 22, size: 14, color: i === 5 ? C.accent : C.grey3, bold: true, font: MONO, align: "center" });
    addText(slide, n[1], { x: x - 82, y: above ? 338 : 470, w: 164, h: 34, size: 21, color: i === 5 ? C.accent : C.ink, bold: true, align: "center" });
    addText(slide, n[2], { x: x - 92, y: above ? 371 : 507, w: 184, h: 30, size: 16, color: C.grey3, align: "center" });
  });
  addShape(slide, { x: 64, y: 593, w: 1152, h: 58, fill: C.ink });
  addText(slide, "结果：模型不直接改平台、不越过人工门禁、不直接写真实工程。", { x: 88, y: 607, w: 1104, h: 32, size: 21, color: C.white, align: "center", bold: true });
  addNotes(slide, {
    purpose: "说明 Agent Web 已形成可运行的端到端开发期闭环。",
    minutes: 1.2,
    talk: [
      "我首先完成并迭代了 M0-M2：需求会话、结构化预览、流程创建与发布激活。",
      "之后把链路延伸到前端代码生成、质量验证、人工 diff 与安全写入。",
      "每个关键动作都由确定性状态机和人工门禁控制，模型只在受限范围内推理和生成。",
      "发布、激活或门禁失败时可以回退到需求预览，避免会话卡死或错误状态继续传播。",
    ],
    transition: "链路走通只是第一步，真正难点是让生成结果可以被验证和修复。",
    sources: ["agent-web/README.md; doc/agent-web-tech-design.md; commits ad33fb5, f745a6d, 0650293, ff2d7e3."],
  });
}

// 05 · Loop Form / S14
{
  const slide = deck.slides.add();
  slide.background.fill = C.ink;
  addChrome(slide, 5, 10, "Agent Web · Quality Loop", true);
  addText(slide, "质量不是最后一关", { x: 64, y: 112, w: 560, h: 66, size: 51, color: C.white });
  addText(slide, "而是一条可观测、可诊断、限次修复的闭环。", { x: 64, y: 190, w: 565, h: 60, size: 25, color: "#FFFFFFB8", lineSpacing: 1.2 });
  const leftItems = [
    ["01", "静态边界", "路径、文件集合、契约与安全规则"],
    ["02", "工程验证", "typecheck / test / build"],
    ["03", "独立审核", "Reviewer 输出证据与修复建议"],
    ["04", "限次修复", "最多三轮，每轮全量重验"],
  ];
  leftItems.forEach((it, i) => {
    const y = 322 + i * 76;
    addText(slide, it[0], { x: 64, y, w: 56, h: 24, size: 14, color: C.accent, bold: true, font: MONO });
    addText(slide, it[1], { x: 130, y: y - 3, w: 168, h: 30, size: 22, color: C.white, bold: true });
    addText(slide, it[2], { x: 316, y: y - 1, w: 300, h: 34, size: 17, color: "#FFFFFFA8" });
    addLine(slide, 64, y + 45, 614, y + 45, "#FFFFFF2E", 1);
  });

  // Connectors first, then nodes.
  const pts = [
    { x: 800, y: 228, w: 210, h: 74 },
    { x: 984, y: 362, w: 210, h: 74 },
    { x: 800, y: 506, w: 210, h: 74 },
    { x: 626, y: 362, w: 210, h: 74 },
  ];
  const ghosts = pts.map((p, i) => addShape(slide, { ...p, fill: "none", line: "none", name: `ghost-${i}` }));
  const links = [
    [0, 1, "bottom", "top"],
    [1, 2, "left", "right"],
    [2, 3, "top", "bottom"],
    [3, 0, "right", "left"],
  ];
  links.forEach(([a, b, fromSide, toSide], i) => {
    slide.shapes.connect(ghosts[a], ghosts[b], {
      kind: "elbow",
      fromSide,
      toSide,
      line: { style: "solid", fill: "#FFFFFF66", width: 2 },
      head: { type: "arrow", width: "sm", length: "sm" },
      name: `loop-link-${i}`,
    });
  });
  const labels = [
    ["STATIC", "规则先行"],
    ["RUN", "工程验证"],
    ["REVIEW", "独立审核"],
    ["REPAIR", "≤ 3 轮"],
  ];
  pts.forEach((p, i) => {
    addShape(slide, { ...p, fill: i === 3 ? C.accent : C.grey1, name: `loop-node-${i}` });
    addText(slide, labels[i][0], { x: p.x + 18, y: p.y + 14, w: p.w - 36, h: 20, size: 14, color: i === 3 ? C.white : C.grey3, bold: true, font: MONO, align: "center" });
    addText(slide, labels[i][1], { x: p.x + 14, y: p.y + 37, w: p.w - 28, h: 28, size: 20, color: i === 3 ? C.white : C.ink, bold: true, align: "center" });
  });
  addText(slide, "LOOP", { x: 842, y: 372, w: 136, h: 48, size: 31, color: C.white, font: LATIN, align: "center", bold: true });
  addNotes(slide, {
    purpose: "突出技术含量最高的质量闭环：规则、工程验证、Reviewer 与 repair。",
    minutes: 1.3,
    talk: [
      "我把质量拆成静态边界、工程验证和独立审核三层，而不是只看测试是否变绿。",
      "static-validator 处理文件范围、生成契约和常见越界问题；Worker 执行前端 typecheck、test、build。",
      "Reviewer 输出带证据的诊断，再由 repair coordinator 组织最多三轮修复。",
      "每轮修复后重新走完整验证，基础设施失败不消耗修复轮次，避免无效循环。",
    ],
    transition: "这套闭环最终改变了交付标准：不再满足于‘能生成’，而是要求‘可交付’。",
    sources: ["agent-web/backend/src/verification/quality-gates.ts; quality-pipeline.service.ts; repair-coordinator.service.ts; commits ee69a44, 540f684, 7c998cf, f2d4e8f."],
  });
}

// 06 · Duo Compare / S08
{
  const slide = deck.slides.add();
  slide.background.fill = C.paper;
  addChrome(slide, 6, 10, "Agent Web · Delivery Standard");
  addSectionTitle(slide, "Core Result 03", "从“能生成”到“可交付”", { titleSize: 51 });
  addLine(slide, 640, 266, 640, 648, C.grey2, 1);
  addText(slide, "BEFORE", { x: 76, y: 276, w: 190, h: 24, size: 14, color: C.grey3, bold: true, font: MONO });
  addText(slide, "能生成", { x: 76, y: 316, w: 480, h: 62, size: 48, color: C.ink });
  addBulletList(slide, ["输出范围容易漂移", "参考上下文会随时间变化", "失败后难以定位和恢复"], { x: 76, y: 418, w: 500, size: 21, gap: 56, color: C.grey3, bulletColor: C.ink, name: "before" });

  addText(slide, "AFTER", { x: 704, y: 276, w: 190, h: 24, size: 14, color: C.accent, bold: true, font: MONO });
  addText(slide, "可交付", { x: 704, y: 316, w: 480, h: 62, size: 48, color: C.accent });
  addBulletList(slide, ["2.1 前端专用契约：固定 5 / 7 文件", "冻结 Skill、金标源码与 SHA-256", "暂存 + diff + 门禁确认 + 安全写入"], { x: 704, y: 418, w: 500, size: 21, gap: 56, color: C.ink, bulletColor: C.accent, name: "after" });
  addShape(slide, { x: 704, y: 601, w: 478, h: 47, fill: C.accent });
  addText(slide, "受保护文件不变，允许输出范围可审计。", { x: 720, y: 612, w: 446, h: 25, size: 18, color: C.white, align: "center", bold: true });
  addNotes(slide, {
    purpose: "把生成契约、安全边界和上下文冻结转译为清晰的交付标准升级。",
    minutes: 1.0,
    talk: [
      "GenerationTargetContract 2.1 把生成范围收敛到前端：常规五个文件，存在动态数据源时七个文件。",
      "生成前冻结 Skill、流程文档和金标源码哈希，保证 repair 与 Reviewer 使用同一上下文。",
      "产物先进入 staging，用户看代码树和 diff；只有门禁通过后才写目标工程。",
      "写入成功后还能自动登记并启用业务入口，让生成结果真正出现在业务使用面。",
    ],
    transition: "Agent Web 能闭环，离不开 Platform 和 Business Base 提供稳定的运行时与业务承载。",
    sources: ["agent-web/README.md; business-base/.flowmind/generation-target.json; commits 12d7ae2, 573aae2, ef3f0d0."],
  });
}

// 07 · Three Forces / S13
{
  const slide = deck.slides.add();
  slide.background.fill = C.grey1;
  addChrome(slide, 7, 10, "Platform + Business Base · 30%");
  addShape(slide, { x: 0, y: 0, w: 410, h: 720, fill: C.accent });
  addText(slide, "运行时与\n业务底座", { x: 64, y: 150, w: 300, h: 170, size: 56, color: C.white, lineSpacing: 0.96 });
  addText(slide, "30% 的工作支撑了\n70% 的 Agent Web 主线", { x: 64, y: 386, w: 286, h: 88, size: 25, color: "#FFFFFFD9", lineSpacing: 1.24 });
  addText(slide, "PLATFORM / BUSINESS / INTEGRATION", { x: 64, y: 618, w: 302, h: 30, size: 14, color: "#FFFFFF99", bold: true, font: MONO });

  const rows = [
    { n: "01", t: "Platform 运行时", d: "从 DTO 契约、定义校验与缓存，推进到持久化/幂等、事务与 outbox、实例管理、附件、增强任务动作和会签。" },
    { n: "02", t: "Business Base", d: "完善通用发起、附件与幂等；补齐业务大厅、管理端、参考数据、知会与示例流程的落地链路。" },
    { n: "03", t: "跨层收口", d: "修复 instanceTitle、路由、状态显示、附件配置主键等联调问题，把契约差异前移到自动化测试。" },
  ];
  rows.forEach((r, i) => {
    const y = 105 + i * 184;
    addText(slide, r.n, { x: 470, y, w: 88, h: 60, size: 46, color: C.accent, font: LATIN });
    addText(slide, r.t, { x: 582, y: y + 4, w: 590, h: 42, size: 28, color: C.ink, bold: true });
    addText(slide, r.d, { x: 582, y: y + 58, w: 590, h: 90, size: 19, color: C.grey3, lineSpacing: 1.35 });
    if (i < 2) addLine(slide, 470, y + 162, 1172, y + 162, C.grey2, 1);
  });
  addNotes(slide, {
    purpose: "概括 Platform 与 Business Base 的关键支撑成果，保持篇幅约30%。",
    minutes: 1.1,
    talk: [
      "Platform 侧参与了从契约到运行时的多阶段建设，包括幂等、事务、outbox、实例和任务动作。",
      "Business Base 侧把能力放到真实业务入口：通用发起、业务大厅、管理端、参考数据与示例流程。",
      "更重要的是跨层联调：很多问题不是单个模块能发现，必须通过端到端验收暴露并收口。",
      "这些支撑让 Agent Web 生成的结果有稳定目标工程和可运行场景。",
    ],
    transition: "这些工作也推动了我的能力结构从技术栈学习走向工程方法和价值交付。",
    sources: ["Git commits by zhangqi@test.com across platform and business-base, including c471a1b, 9c256cc, a05d69a, 9415704, ef3f0d0, e7b1e9a."],
  });
}

// 08 · Three Layers / S05
{
  const slide = deck.slides.add();
  slide.background.fill = C.paper;
  addChrome(slide, 8, 10, "Growth · Value · Contribution");
  addSectionTitle(slide, "Growth", "成长不只体现在技术栈", { titleSize: 52 });
  const cards = [
    { x: 64, fill: C.accent, color: C.white, n: "01", t: "技术能力", items: ["NestJS / Vue / Pinia", "Java / Spring / SQLite", "Agent 状态机与上下文压缩"] },
    { x: 448, fill: C.grey1, color: C.ink, n: "02", t: "工程方法", items: ["契约与边界先行", "测试驱动联调", "诊断、回退、幂等与安全写入"] },
    { x: 832, fill: C.ink, color: C.white, n: "03", t: "价值输出", items: ["打通端到端链路", "把失败变成可定位状态", "沉淀可复用契约与金标参考"] },
  ];
  cards.forEach((c) => {
    addShape(slide, { x: c.x, y: 278, w: 352, h: 344, fill: c.fill });
    addText(slide, c.n, { x: c.x + 24, y: 300, w: 92, h: 44, size: 32, color: c.color, font: LATIN });
    addText(slide, c.t, { x: c.x + 24, y: 365, w: 292, h: 42, size: 29, color: c.color, bold: true });
    c.items.forEach((it, i) => {
      addLine(slide, c.x + 24, 447 + i * 52, c.x + 40, 447 + i * 52, c.color === C.white ? "#FFFFFFAA" : C.accent, 2);
      addText(slide, it, { x: c.x + 54, y: 435 + i * 52, w: 270, h: 37, size: 18, color: c.color === C.white ? "#FFFFFFD9" : C.grey3 });
    });
  });
  addNotes(slide, {
    purpose: "把成长、价值和贡献总结为三层，不重复前面的功能成果。",
    minutes: 0.8,
    talk: [
      "技术上从前后端开发扩展到 Agent 编排、质量验证与 Java 流程运行时。",
      "方法上最大的变化是先定义契约和失败边界，再实现功能。",
      "价值输出不止是交付代码，还包括让失败可诊断、流程可回退、生成上下文可复现。",
    ],
    transition: "基于这些实践，我也形成了几条对后续迭代的具体建议。",
    sources: ["Repository source and Git history under agent-web, platform, business-base."],
  });
}

// 09 · Multi-card Brief / S16
{
  const slide = deck.slides.add();
  slide.background.fill = C.grey1;
  addChrome(slide, 9, 10, "Reflection · Suggestions");
  addSectionTitle(slide, "Reflection", "下一步，把工程闭环变成可持续指标", { titleSize: 46 });
  const items = [
    ["01", "建立金标场景集", "按典型流程沉淀可重复的端到端验收"],
    ["02", "量化质量闭环", "跟踪首轮通过率、修复轮次、验证耗时"],
    ["03", "统一契约版本", "明确兼容矩阵与升级错误，减少跨层漂移"],
    ["04", "一键环境诊断", "启动前检查端口、凭据、数据库与目标契约"],
    ["05", "缩短反馈周期", "用小 PR + 周期性 Demo 提前暴露方向偏差"],
    ["06", "沉淀业务反馈", "将真实使用问题回流到 prompt、规则与测试"],
  ];
  items.forEach((it, i) => {
    const col = i % 3;
    const row = Math.floor(i / 3);
    const x = 64 + col * 384;
    const y = 286 + row * 168;
    const accent = i === 1;
    addShape(slide, { x, y, w: 352, h: 144, fill: accent ? C.accent : C.paper });
    addText(slide, it[0], { x: x + 18, y: y + 16, w: 54, h: 25, size: 14, color: accent ? C.white : C.grey3, bold: true, font: MONO });
    addText(slide, it[1], { x: x + 18, y: y + 51, w: 308, h: 34, size: 23, color: accent ? C.white : C.ink, bold: true });
    addText(slide, it[2], { x: x + 18, y: y + 91, w: 308, h: 42, size: 16, color: accent ? "#FFFFFFD9" : C.grey3, lineSpacing: 1.26 });
  });
  addText(slide, "体会：Prompt 决定上限，契约、测试和反馈闭环决定稳定下限。", { x: 64, y: 636, w: 1152, h: 32, size: 20, color: C.ink, bold: true, align: "center" });
  addNotes(slide, {
    purpose: "给出心得和可执行优化建议，体现思考而非只汇报完成项。",
    minutes: 0.8,
    talk: [
      "我最大的体会是 Prompt 只能提高上限，稳定下限必须靠契约、测试和反馈闭环。",
      "建议先建立金标场景集，再量化首轮通过率、修复轮次和验证耗时。",
      "跨层协作上可以维护契约兼容矩阵，并把启动、凭据、数据库和目标契约做成一键诊断。",
      "流程上用更小的 PR 和更频繁的 Demo，能更早发现方向偏差。",
    ],
    transition: "最后，用一页说明我希望继续发展的方向和对机会的态度。",
    sources: ["Inferences from repository implementation patterns; agent-web/AGENTS.md testing and safety requirements."],
  });
}

// 10 · Split Closing / S10
{
  const slide = deck.slides.add();
  slide.background.fill = C.paper;
  addShape(slide, { x: 0, y: 0, w: 566, h: 720, fill: C.accent });
  addText(slide, "继续做深", { x: 64, y: 126, w: 430, h: 64, size: 52, color: C.white });
  addText(slide, "Agent / AI\n全栈开发", { x: 64, y: 222, w: 430, h: 176, size: 67, color: C.white, lineSpacing: 0.94 });
  addText(slide, "把模型能力落到真实业务、工程约束与用户体验里。", { x: 64, y: 478, w: 420, h: 82, size: 23, color: "#FFFFFFD9", lineSpacing: 1.35 });
  addText(slide, "张琦 · 2026.08", { x: 64, y: 640, w: 280, h: 26, size: 14, color: "#FFFFFF99", bold: true, font: MONO });

  addText(slide, "CLOSING · NEXT STEP", { x: 64, y: 35, w: 430, h: 22, size: 14, color: "#FFFFFF99", bold: true, font: MONO });
  addText(slide, "10 / 10", { x: 1060, y: 35, w: 156, h: 22, size: 14, color: C.grey3, bold: true, font: MONO, align: "right" });
  const x = 628;
  const rows = [
    ["01", "秋招意向", "Agent 工程 / AI 应用 / 全栈开发"],
    ["02", "选择标准", "真实业务场景、工程深度与持续成长空间"],
    ["03", "加入意愿", "对匹配的团队与岗位保持开放，期待结合具体职责继续沟通"],
  ];
  rows.forEach((r, i) => {
    const y = 156 + i * 154;
    addText(slide, r[0], { x, y, w: 70, h: 36, size: 26, color: i === 2 ? C.accent : C.ink, font: LATIN });
    addText(slide, r[1], { x: x + 96, y: y + 2, w: 200, h: 34, size: 24, color: C.ink, bold: true });
    addText(slide, r[2], { x: x + 96, y: y + 48, w: 490, h: 64, size: 20, color: C.grey3, lineSpacing: 1.32 });
    if (i < 2) addLine(slide, x, y + 126, 1190, y + 126, C.grey2, 1);
  });
  addShape(slide, { x: 628, y: 620, w: 562, h: 48, fill: C.ink });
  addText(slide, "谢谢 · 欢迎交流", { x: 648, y: 631, w: 522, h: 26, size: 20, color: C.white, bold: true, align: "center" });
  addNotes(slide, {
    purpose: "清晰表达方向，同时对加入机会保持开放和克制。",
    minutes: 0.5,
    talk: [
      "后续我希望继续往 Agent / AI 全栈方向发展，重点是工程化落地而不是只做模型调用。",
      "秋招会关注真实业务场景、工程深度和成长空间。",
      "对于团队机会，我保持开放，希望结合具体岗位职责和双方匹配度继续沟通。",
      "谢谢大家，欢迎提问。",
    ],
    transition: "结束汇报，进入交流。",
    sources: ["User-provided career preference."],
  });
}

await fs.mkdir(PREVIEW_DIR, { recursive: true });
for (const [i, slide] of deck.slides.items.entries()) {
  const stem = `slide-${String(i + 1).padStart(2, "0")}`;
  const png = await deck.export({ slide, format: "png", scale: 1 });
  await fs.writeFile(`${PREVIEW_DIR}/${stem}.png`, new Uint8Array(await png.arrayBuffer()));
  const layout = await slide.export({ format: "layout" });
  await fs.writeFile(`${PREVIEW_DIR}/${stem}.layout.json`, await layout.text());
}
const montage = await deck.export({ format: "webp", montage: true, scale: 1 });
await fs.writeFile(`${PREVIEW_DIR}/montage.webp`, new Uint8Array(await montage.arrayBuffer()));
const pptx = await PresentationFile.exportPptx(deck);
await pptx.save(OUT);
console.log(`Wrote ${OUT}`);
