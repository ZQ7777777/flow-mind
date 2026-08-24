import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { Presentation, PresentationFile } from "@oai/artifact-tool";

import { deck, slides, assertDeck } from "./slides-data.mjs";

const __filename = fileURLToPath(import.meta.url);
const BUILD_DIR = path.dirname(__filename);
const REPORT_DIR = path.resolve(BUILD_DIR, "..");
const IMAGE_DIR = path.join(REPORT_DIR, "images");
const FINAL_PPTX = path.resolve(REPORT_DIR, "..", "张琦-实习成果汇报.pptx");

const W = 1280;
const H = 720;
const FONT = "Microsoft YaHei UI";

const C = {
  blue: "#002FA7",
  blue2: "#1749D1",
  blueLight: "#DCE5FF",
  bluePale: "#EDF1FF",
  ink: "#0B0D12",
  dark: "#090B10",
  paper: "#FAFAF8",
  white: "#FFFFFF",
  gray: "#65686F",
  gray2: "#95989F",
  line: "#C4C6CB",
  lineDark: "#353840",
  soft: "#E7E6E1",
};

function noLine() {
  return { style: "solid", fill: "none", width: 0 };
}

function addText(slide, text, position, options = {}) {
  const shape = slide.shapes.add({
    geometry: "textbox",
    name: options.name,
    position,
    fill: "none",
    line: noLine(),
    shadow: "shadow-none",
  });
  shape.text = text;
  shape.text.style = {
    typeface: FONT,
    fontSize: options.fontSize ?? 22,
    bold: options.bold ?? false,
    color: options.color ?? C.ink,
    alignment: options.alignment ?? "left",
    verticalAlignment: options.verticalAlignment ?? "top",
    wrap: options.wrap ?? "square",
    autoFit: options.autoFit ?? "none",
    lineSpacing: options.lineSpacing ?? 1.0,
    insets: options.insets ?? { top: 0, right: 0, bottom: 0, left: 0 },
  };
  return shape;
}

function addRect(slide, position, fill, options = {}) {
  return slide.shapes.add({
    geometry: "rect",
    name: options.name,
    position,
    fill,
    line: options.line ?? noLine(),
    shadow: "shadow-none",
  });
}

function addLine(slide, x, y, width, height = 0, color = C.line, weight = 1) {
  return slide.shapes.add({
    geometry: "line",
    position: { left: x, top: y, width, height },
    fill: "none",
    line: { style: "solid", fill: color, width: weight },
    shadow: "shadow-none",
  });
}

function addStandardHeader(slide, data, page, dark = false) {
  const fg = dark ? C.white : C.ink;
  const secondary = dark ? C.gray2 : C.gray;
  addRect(slide, { left: 72, top: 38, width: 10, height: 10 }, C.blue);
  addText(slide, data.section, { left: 94, top: 32, width: 420, height: 24 }, {
    fontSize: 16,
    bold: true,
    color: secondary,
    wrap: "none",
  });
  addText(slide, String(page).padStart(2, "0"), { left: 1150, top: 30, width: 58, height: 24 }, {
    fontSize: 16,
    bold: true,
    color: secondary,
    alignment: "right",
    wrap: "none",
  });
  addLine(slide, 72, 70, 1136, 0, dark ? C.lineDark : C.line, 1);
  addText(slide, data.title, { left: 72, top: 92, width: 1136, height: 66 }, {
    name: `slide-${page}-title`,
    fontSize: 48,
    bold: false,
    color: fg,
    wrap: "none",
  });
}

function addFooter(slide, data, page, dark = false) {
  const color = dark ? C.gray2 : C.gray;
  addLine(slide, 72, 676, 1136, 0, dark ? C.lineDark : C.line, 1);
  addText(slide, `${deck.author} · ${deck.period}`, { left: 72, top: 686, width: 340, height: 18 }, {
    fontSize: 14,
    color,
    wrap: "none",
  });
  addText(slide, data.section, { left: 850, top: 686, width: 358, height: 18 }, {
    fontSize: 14,
    color,
    alignment: "right",
    wrap: "none",
  });
}

function setNotes(slide, data, extraSources = []) {
  const notes = [
    `【${data.section}｜建议 ${data.minutes} 分钟】`,
    ...data.talk,
    `转场：${data.transition}`,
    "",
    "[Sources]",
    ...data.evidence.map((item) => `- ${item}`),
    ...extraSources.map((item) => `- ${item}`),
  ];
  slide.speakerNotes.textFrame.setText(notes);
  slide.speakerNotes.setVisible(true);
}

async function readImageBuffer(fileName) {
  const bytes = await fs.readFile(path.join(IMAGE_DIR, fileName));
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
}

function addImage(slide, blob, alt, position) {
  return slide.images.add({
    blob,
    contentType: "image/png",
    alt,
    fit: "cover",
    geometry: "rect",
    position,
  });
}

function makeCover(presentation, data) {
  const slide = presentation.slides.add();
  slide.background.fill = C.blue;

  addRect(slide, { left: 0, top: 0, width: 1280, height: 18 }, C.ink);
  addText(slide, "INTERNSHIP · OUTCOME", { left: 72, top: 54, width: 320, height: 30 }, {
    fontSize: 18,
    bold: true,
    color: C.white,
    wrap: "none",
  });

  addText(slide, data.title, { left: 72, top: 198, width: 750, height: 104 }, {
    name: "cover-title",
    fontSize: 82,
    bold: false,
    color: C.white,
    wrap: "none",
  });
  addText(slide, deck.subtitle, { left: 76, top: 318, width: 710, height: 50 }, {
    fontSize: 32,
    bold: false,
    color: C.white,
    wrap: "none",
  });
  addLine(slide, 76, 392, 692, 0, C.white, 2);

  addRect(slide, { left: 858, top: 196, width: 350, height: 314 }, C.ink);
  addText(slide, deck.author, { left: 894, top: 236, width: 278, height: 66 }, {
    fontSize: 50,
    bold: false,
    color: C.white,
    wrap: "none",
  });
  addText(slide, deck.profile.replaceAll(" · ", "\n"), { left: 894, top: 325, width: 278, height: 112 }, {
    fontSize: 23,
    color: C.white,
    lineSpacing: 1.18,
  });
  addText(slide, deck.period, { left: 894, top: 458, width: 278, height: 28 }, {
    fontSize: 18,
    bold: false,
    color: C.blueLight,
    wrap: "none",
  });

  setNotes(slide, data);
}

function makeProfile(presentation, data, page) {
  const slide = presentation.slides.add();
  slide.background.fill = C.paper;
  addStandardHeader(slide, data, page, false);

  addText(slide, "24", { left: 72, top: 190, width: 260, height: 154 }, {
    fontSize: 132,
    bold: false,
    color: C.blue,
    wrap: "none",
  });
  addText(slide, "岁", { left: 287, top: 281, width: 44, height: 36 }, {
    fontSize: 25,
    bold: true,
    color: C.blue,
    wrap: "none",
  });
  addLine(slide, 72, 365, 300, 0, C.ink, 2);
  addText(slide, "苏州大学", { left: 72, top: 390, width: 300, height: 40 }, {
    fontSize: 31,
    bold: false,
  });
  addText(slide, "计算机科学与技术", { left: 72, top: 442, width: 300, height: 36 }, {
    fontSize: 24,
    color: C.gray,
  });

  addRect(slide, { left: 430, top: 188, width: 6, height: 380 }, C.blue);
  addText(slide, "后半程重点投入", { left: 474, top: 188, width: 220, height: 30 }, {
    fontSize: 18,
    bold: true,
    color: C.gray,
    wrap: "none",
  });
  addText(slide, "Agent Web", { left: 474, top: 226, width: 500, height: 64 }, {
    fontSize: 52,
    bold: false,
    color: C.ink,
    wrap: "none",
  });
  addText(slide, "让生成结果可确认、可验证、可修复，并安全进入业务系统。", { left: 474, top: 304, width: 620, height: 66 }, {
    fontSize: 24,
    color: C.gray,
    lineSpacing: 1.08,
  });

  addText(slide, "汇报重心", { left: 474, top: 404, width: 160, height: 28 }, {
    fontSize: 18,
    bold: true,
    color: C.gray,
    wrap: "none",
  });
  addRect(slide, { left: 474, top: 448, width: 452, height: 34 }, C.blue);
  addRect(slide, { left: 926, top: 448, width: 194, height: 34 }, C.ink);
  addText(slide, "70%  AGENT WEB", { left: 490, top: 455, width: 414, height: 20 }, {
    fontSize: 16,
    bold: true,
    color: C.white,
    wrap: "none",
  });
  addText(slide, "30%  底座", { left: 944, top: 455, width: 160, height: 20 }, {
    fontSize: 16,
    bold: true,
    color: C.white,
    wrap: "none",
  });
  addText(slide, data.support, { left: 474, top: 506, width: 646, height: 50 }, {
    fontSize: 20,
    color: C.ink,
    lineSpacing: 1.08,
  });

  addFooter(slide, data, page, false);
  setNotes(slide, data);
}

function makeTrajectory(presentation, data, page) {
  const slide = presentation.slides.add();
  slide.background.fill = C.dark;
  addStandardHeader(slide, data, page, true);

  const statX = [72, 276, 480];
  data.stats.forEach((stat, index) => {
    addText(slide, stat.value, { left: statX[index], top: 184, width: 170, height: 78 }, {
      fontSize: 64,
      bold: false,
      color: index === 0 ? C.blueLight : C.white,
      wrap: "none",
    });
    addText(slide, stat.label, { left: statX[index], top: 264, width: 170, height: 28 }, {
      fontSize: 18,
      bold: false,
      color: C.gray2,
      wrap: "none",
    });
  });
  addText(slide, "Git 署名证据", { left: 720, top: 190, width: 210, height: 28 }, {
    fontSize: 18,
    bold: true,
    color: C.blueLight,
    wrap: "none",
  });
  addText(slide, "工作范围不是突然切换，\n而是从流程能力逐步走向 Agent 工程交付。", { left: 720, top: 229, width: 488, height: 90 }, {
    fontSize: 25,
    bold: false,
    color: C.white,
    lineSpacing: 1.08,
  });

  addLine(slide, 72, 370, 1136, 0, C.lineDark, 2);
  const xs = [72, 356, 640, 924];
  data.phases.forEach((phase, index) => {
    addRect(slide, { left: xs[index], top: 357, width: index === 3 ? 284 : 12, height: 26 }, index < 2 ? C.white : C.blue2);
    addText(slide, phase.date, { left: xs[index], top: 405, width: 250, height: 24 }, {
      fontSize: 16,
      bold: false,
      color: C.gray2,
      wrap: "none",
    });
    addText(slide, phase.name, { left: xs[index], top: 442, width: 250, height: 34 }, {
      fontSize: 27,
      bold: false,
      color: C.white,
      wrap: "none",
    });
    addText(slide, phase.desc, { left: xs[index], top: 490, width: 248, height: 74 }, {
      fontSize: 19,
      color: C.gray2,
      lineSpacing: 1.12,
    });
  });

  addFooter(slide, data, page, true);
  setNotes(slide, data);
}

function makeArchitecture(presentation, data, page) {
  const slide = presentation.slides.add();
  slide.background.fill = C.paper;
  addStandardHeader(slide, data, page, false);

  const ys = [186, 318, 450];
  data.layers.forEach((layer, index) => {
    const fill = index === 1 ? C.bluePale : C.soft;
    addRect(slide, { left: 72, top: ys[index], width: 846, height: 108 }, fill);
    addRect(slide, { left: 72, top: ys[index], width: 88, height: 108 }, index === 1 ? C.blue : C.ink);
    addText(slide, layer.no, { left: 92, top: ys[index] + 29, width: 48, height: 45 }, {
      fontSize: 32,
      bold: true,
      color: C.white,
      alignment: "center",
      wrap: "none",
    });
    addText(slide, layer.name, { left: 188, top: ys[index] + 18, width: 132, height: 24 }, {
      fontSize: 16,
      bold: true,
      color: C.gray,
      wrap: "none",
    });
    addText(slide, layer.headline, { left: 188, top: ys[index] + 49, width: 248, height: 36 }, {
      fontSize: 28,
      bold: false,
      color: C.ink,
      wrap: "none",
    });
    addLine(slide, 452, ys[index] + 20, 0, 68, C.line, 1);
    addText(slide, layer.body, { left: 486, top: ys[index] + 29, width: 398, height: 58 }, {
      fontSize: 21,
      color: C.ink,
      lineSpacing: 1.08,
    });
  });

  addText(slide, "三道人控门禁", { left: 970, top: 186, width: 238, height: 32 }, {
    fontSize: 24,
    bold: false,
    color: C.blue,
    wrap: "none",
  });
  addLine(slide, 984, 242, 0, 302, C.blue, 2);
  data.gates.forEach((gate, index) => {
    const y = 246 + index * 102;
    addRect(slide, { left: 973, top: y, width: 24, height: 24 }, index === 2 ? C.blue : C.ink);
    addText(slide, String(index + 1).padStart(2, "0"), { left: 1018, top: y - 1, width: 36, height: 24 }, {
      fontSize: 16,
      bold: false,
      color: C.gray,
      wrap: "none",
    });
    addText(slide, gate, { left: 1054, top: y - 1, width: 154, height: 52 }, {
      fontSize: 20,
      bold: false,
      color: C.ink,
      lineSpacing: 1.05,
    });
  });

  addFooter(slide, data, page, false);
  setNotes(slide, data);
}

async function makeQualityEvidence(presentation, data, page, imageBuffers) {
  const slide = presentation.slides.add();
  slide.background.fill = C.paper;
  addStandardHeader(slide, data, page, false);

  addText(slide, "端到端交付链路", { left: 54, top: 168, width: 240, height: 30 }, {
    fontSize: 22,
    bold: true,
    color: C.blue,
    wrap: "none",
  });
  addText(slide, "质量修复循环", { left: 666, top: 168, width: 240, height: 30 }, {
    fontSize: 22,
    bold: true,
    color: C.blue,
    wrap: "none",
  });

  const leftFrame = { left: 54, top: 204, width: 560, height: 374 };
  const rightFrame = { left: 666, top: 204, width: 560, height: 374 };
  addImage(slide, imageBuffers.endToEnd, "无标签端到端生成链路底图", leftFrame);
  addImage(slide, imageBuffers.qualityLoop, "无标签质量闭环底图", rightFrame);
  addRect(slide, leftFrame, "none", { line: { style: "solid", fill: C.ink, width: 1 } });
  addRect(slide, rightFrame, "none", { line: { style: "solid", fill: C.ink, width: 1 } });

  const stageXs = [65, 158, 252, 346, 440, 532];
  data.images[0].labels.forEach((label, index) => {
    addText(slide, label, { left: stageXs[index], top: 485, width: 75, height: 46 }, {
      fontSize: 16,
      bold: true,
      color: index >= 4 ? C.blue : C.ink,
      alignment: "center",
      verticalAlignment: "middle",
      lineSpacing: 1.0,
    });
  });

  const loopLabels = [
    { text: "静态校验", left: 895, top: 249, width: 108, height: 34 },
    { text: "类型检查\n测试 · 构建", left: 739, top: 342, width: 113, height: 52 },
    { text: "评审", left: 1055, top: 342, width: 111, height: 46 },
    { text: "修复", left: 1045, top: 496, width: 112, height: 44 },
    { text: "重验", left: 760, top: 496, width: 112, height: 44 },
  ];
  loopLabels.forEach((item, index) => {
    addText(slide, item.text, item, {
      fontSize: index === 1 ? 15 : 17,
      bold: true,
      color: index === 3 ? C.blue : C.ink,
      alignment: "center",
      verticalAlignment: "middle",
      lineSpacing: 1.0,
    });
  });
  addRect(slide, { left: 890, top: 553, width: 118, height: 28 }, C.blue);
  addText(slide, "≤ 3 轮", { left: 890, top: 557, width: 118, height: 19 }, {
    fontSize: 15,
    bold: true,
    color: C.white,
    alignment: "center",
    wrap: "none",
  });

  addText(slide, "硬门禁禁写 · 软门禁人工复核 · 无效/异常 Repair 回滚", { left: 666, top: 600, width: 560, height: 28 }, {
    fontSize: 18,
    bold: false,
    color: C.ink,
    alignment: "center",
    wrap: "none",
  });
  addText(slide, data.fileContract, { left: 54, top: 600, width: 560, height: 28 }, {
    fontSize: 20,
    bold: true,
    color: C.ink,
    alignment: "center",
    wrap: "none",
  });

  addFooter(slide, data, page, false);
  setNotes(slide, data, [
    "05-end-to-end.png — OpenAI ImageGen 生成的无标签瑞士风格底图",
    "05-quality-loop.png — OpenAI ImageGen 生成的无标签瑞士风格底图",
  ]);
}

function makeSampleEvidence(presentation, data, page) {
  const slide = presentation.slides.add();
  slide.background.fill = C.dark;
  addStandardHeader(slide, data, page, true);

  addText(slide, "金标案例", { left: 72, top: 186, width: 160, height: 28 }, {
    fontSize: 18,
    bold: true,
    color: C.blueLight,
    wrap: "none",
  });
  addText(slide, data.caseName, { left: 72, top: 226, width: 548, height: 114 }, {
    fontSize: 48,
    bold: false,
    color: C.white,
    lineSpacing: 1.0,
  });
  addLine(slide, 72, 370, 520, 0, C.blue2, 4);
  addText(slide, "覆盖流程、角色、附件、资金核查与动态数据，\n验证完整业务场景。", { left: 72, top: 402, width: 520, height: 94 }, {
    fontSize: 24,
    color: C.gray2,
    lineSpacing: 1.12,
  });
  addText(slide, data.extra, { left: 72, top: 540, width: 520, height: 34 }, {
    fontSize: 21,
    bold: true,
    color: C.blueLight,
    wrap: "none",
  });

  const mx = [672, 850, 1028];
  data.metrics.slice(0, 3).forEach((metric, index) => {
    addLine(slide, mx[index], 202, 150, 0, C.lineDark, 1);
    addText(slide, metric.value, { left: mx[index], top: 220, width: 150, height: 78 }, {
      fontSize: 62,
      bold: false,
      color: index === 0 ? C.blueLight : C.white,
      wrap: "none",
    });
    addText(slide, metric.label, { left: mx[index], top: 305, width: 150, height: 26 }, {
      fontSize: 18,
      bold: true,
      color: C.gray2,
      wrap: "none",
    });
  });
  const bottomMetrics = data.metrics.slice(3);
  bottomMetrics.forEach((metric, index) => {
    const x = 672 + index * 267;
    addRect(slide, { left: x, top: 384, width: 236, height: 162 }, index === 0 ? C.blue : C.white);
    addText(slide, metric.value, { left: x + 24, top: 409, width: 188, height: 70 }, {
      fontSize: 56,
      bold: false,
      color: index === 0 ? C.white : C.ink,
      wrap: "none",
    });
    addText(slide, metric.label, { left: x + 24, top: 490, width: 188, height: 30 }, {
      fontSize: 19,
      bold: true,
      color: index === 0 ? C.white : C.ink,
      wrap: "none",
    });
  });

  addFooter(slide, data, page, true);
  setNotes(slide, data);
}

function makePlatformBusiness(presentation, data, page) {
  const slide = presentation.slides.add();
  slide.background.fill = C.paper;
  addStandardHeader(slide, data, page, false);

  const ys = [188, 320, 452];
  data.blocks.forEach((block, index) => {
    addRect(slide, { left: 72, top: ys[index], width: 84, height: 104 }, index === 2 ? C.blue : C.ink);
    addText(slide, block.no, { left: 88, top: ys[index] + 29, width: 52, height: 44 }, {
      fontSize: 32,
      bold: true,
      color: C.white,
      alignment: "center",
      wrap: "none",
    });
    addText(slide, block.name, { left: 188, top: ys[index] + 4, width: 216, height: 24 }, {
      fontSize: 16,
      bold: true,
      color: C.gray,
      wrap: "none",
    });
    addText(slide, block.headline, { left: 188, top: ys[index] + 34, width: 278, height: 42 }, {
      fontSize: 30,
      bold: false,
      color: C.ink,
      wrap: "none",
    });
    addLine(slide, 492, ys[index] + 8, 0, 88, C.line, 1);
    addText(slide, block.body, { left: 532, top: ys[index] + 29, width: 676, height: 58 }, {
      fontSize: 22,
      color: C.ink,
      verticalAlignment: "middle",
      lineSpacing: 1.08,
    });
    if (index < data.blocks.length - 1) {
      addText(slide, "↓", { left: 102, top: ys[index] + 105, width: 26, height: 24 }, {
        fontSize: 20,
        bold: true,
        color: C.blue,
        alignment: "center",
        wrap: "none",
      });
    }
  });

  addRect(slide, { left: 72, top: 592, width: 1136, height: 48 }, C.blue);
  addText(slide, "Agent 生成  →  入口登记  →  发起流程  →  运行执行  →  知会反馈", { left: 92, top: 603, width: 1096, height: 28 }, {
    fontSize: 22,
    bold: false,
    color: C.white,
    alignment: "center",
    wrap: "none",
  });

  addFooter(slide, data, page, false);
  setNotes(slide, data);
}

function makeGrowth(presentation, data, page) {
  const slide = presentation.slides.add();
  slide.background.fill = C.paper;
  addStandardHeader(slide, data, page, false);

  addText(slide, "功能能跑，只是起点。", { left: 72, top: 186, width: 438, height: 54 }, {
    fontSize: 40,
    bold: false,
    color: C.blue,
    wrap: "none",
  });
  addText(slide, "还要追问边界是否明确、失败能否恢复、证据能否追溯。", { left: 72, top: 250, width: 910, height: 42 }, {
    fontSize: 28,
    bold: false,
    color: C.ink,
    wrap: "none",
  });

  const xs = [72, 456, 840];
  data.forces.forEach((force, index) => {
    addLine(slide, xs[index], 348, 336, 0, index === 2 ? C.blue : C.ink, 3);
    addText(slide, force.no, { left: xs[index], top: 369, width: 44, height: 28 }, {
      fontSize: 18,
      bold: false,
      color: C.gray,
      wrap: "none",
    });
    addText(slide, force.name, { left: xs[index] + 54, top: 369, width: 258, height: 28 }, {
      fontSize: 18,
      bold: true,
      color: index === 2 ? C.blue : C.gray,
      wrap: "none",
    });
    addText(slide, force.headline, { left: xs[index], top: 418, width: 336, height: 68 }, {
      fontSize: 29,
      bold: false,
      color: C.ink,
      lineSpacing: 1.02,
    });
    addText(slide, force.body, { left: xs[index], top: 512, width: 336, height: 86 }, {
      fontSize: 20,
      color: C.gray,
      lineSpacing: 1.12,
    });
  });

  addFooter(slide, data, page, false);
  setNotes(slide, data);
}

function makeClosing(presentation, data, page) {
  const slide = presentation.slides.add();
  slide.background.fill = C.paper;
  addRect(slide, { left: 0, top: 0, width: 544, height: 720 }, C.blue);
  addRect(slide, { left: 544, top: 0, width: 12, height: 720 }, C.ink);

  addText(slide, "09", { left: 72, top: 46, width: 100, height: 62 }, {
    fontSize: 48,
    bold: false,
    color: C.white,
    wrap: "none",
  });
  addText(slide, data.title, { left: 72, top: 184, width: 406, height: 214 }, {
    name: "closing-title",
    fontSize: 52,
    bold: false,
    color: C.white,
    lineSpacing: 0.98,
  });
  addLine(slide, 72, 430, 280, 0, C.white, 2);
  addText(slide, "Agent / AI 全栈", { left: 72, top: 458, width: 360, height: 38 }, {
    fontSize: 26,
    bold: false,
    color: C.white,
    wrap: "none",
  });
  addText(slide, "保持对具体业务场景与团队需求的开放", { left: 72, top: 515, width: 400, height: 68 }, {
    fontSize: 21,
    color: C.blueLight,
    lineSpacing: 1.12,
  });
  addText(slide, `${deck.author} · ${deck.profile}`, { left: 72, top: 652, width: 400, height: 22 }, {
    fontSize: 14,
    color: C.white,
    wrap: "none",
  });

  addText(slide, "三点收束", { left: 620, top: 64, width: 220, height: 34 }, {
    fontSize: 24,
    bold: true,
    color: C.blue,
    wrap: "none",
  });
  const ys = [142, 302, 462];
  data.takeaways.forEach((item, index) => {
    addLine(slide, 620, ys[index], 588, 0, index === 2 ? C.blue : C.line, 2);
    addText(slide, item.no, { left: 620, top: ys[index] + 24, width: 48, height: 32 }, {
      fontSize: 21,
      bold: true,
      color: index === 2 ? C.blue : C.gray,
      wrap: "none",
    });
    addText(slide, item.title, { left: 692, top: ys[index] + 20, width: 450, height: 40 }, {
      fontSize: 30,
      bold: false,
      color: C.ink,
      wrap: "none",
    });
    addText(slide, item.body, { left: 692, top: ys[index] + 70, width: 492, height: index === 2 ? 94 : 58 }, {
      fontSize: index === 2 ? 19 : 21,
      color: C.gray,
      lineSpacing: 1.1,
    });
  });
  addText(slide, "谢谢", { left: 1112, top: 660, width: 96, height: 30 }, {
    fontSize: 22,
    bold: true,
    color: C.blue,
    alignment: "right",
    wrap: "none",
  });

  setNotes(slide, data);
}

async function main() {
  assertDeck();
  const presentation = Presentation.create({ slideSize: { width: W, height: H } });
  presentation.view.showGuides = false;
  presentation.view.showGrid = false;

  const imageBuffers = {
    endToEnd: await readImageBuffer("05-end-to-end.png"),
    qualityLoop: await readImageBuffer("05-quality-loop.png"),
  };

  makeCover(presentation, slides[0]);
  makeProfile(presentation, slides[1], 2);
  makeTrajectory(presentation, slides[2], 3);
  makeArchitecture(presentation, slides[3], 4);
  await makeQualityEvidence(presentation, slides[4], 5, imageBuffers);
  makeSampleEvidence(presentation, slides[5], 6);
  makePlatformBusiness(presentation, slides[6], 7);
  makeGrowth(presentation, slides[7], 8);
  makeClosing(presentation, slides[8], 9);

  const pptx = await PresentationFile.exportPptx(presentation);
  await pptx.save(FINAL_PPTX);
  console.log(`Saved ${FINAL_PPTX}`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
