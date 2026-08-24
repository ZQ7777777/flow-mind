import { Buffer } from "node:buffer";
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, extname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { assertDeck, deck, slides } from "./slides-data.mjs";

const buildDir = dirname(fileURLToPath(import.meta.url));
const reportDir = resolve(buildDir, "..");
const repoRoot = resolve(reportDir, "..", "..");
const skillRoot = "C:\\Users\\zq\\.claude\\skills\\guizang-ppt-skill";
const templatePath = join(skillRoot, "assets", "template-swiss.html");
const motionPath = join(skillRoot, "assets", "motion.min.js");
const outputPath = join(repoRoot, "outputs", "张琦-实习成果汇报.html");

assertDeck();

const esc = (value) => String(value ?? "")
  .replaceAll("&", "&amp;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;")
  .replaceAll('"', "&quot;")
  .replaceAll("'", "&#39;");

const splitAfter = (value, marker) => {
  const text = String(value);
  const at = text.indexOf(marker);
  if (at < 0) return esc(text);
  const cut = at + marker.length;
  return `${esc(text.slice(0, cut))}<br>${esc(text.slice(cut))}`;
};

const pageNo = (index) => String(index + 1).padStart(2, "0");
const totalNo = String(slides.length).padStart(2, "0");
const slide = (id) => {
  const found = slides.find((item) => item.id === id);
  if (!found) throw new Error(`Missing slide data: ${id}`);
  return found;
};

const chrome = (item, index, right = `${pageNo(index)} / ${totalNo}`) => `
  <div class="chrome-min">
    <div class="l">${esc(item.section)} · ${esc(deck.title)}</div>
    <div class="r">${esc(right)}</div>
  </div>`;

const imageDataUri = (file) => {
  const filePath = join(reportDir, "images", file);
  const mime = extname(file).toLowerCase() === ".png" ? "image/png" : "application/octet-stream";
  return `data:${mime};base64,${Buffer.from(readFileSync(filePath)).toString("base64")}`;
};

const endToEndImage = imageDataUri("05-end-to-end.png");
const qualityLoopImage = imageDataUri("05-quality-loop.png");

function renderCover() {
  const item = slide("cover");
  return `<section class="slide accent" data-animate="hero" data-slide-id="${esc(item.id)}" data-layout="${esc(item.layout)}">
  <div class="canvas-card">
    <canvas class="ascii-bg" aria-hidden="true"></canvas>${chrome(item, 0, `INTERNSHIP · ${pageNo(0)} / ${totalNo}`)}
    <div style="flex:1;padding:0 0 4.5vh 0;display:grid;grid-template-rows:auto 1fr auto;gap:2.6vh">
      <div data-anim="kicker" class="t-meta" style="color:rgba(255,255,255,.78);letter-spacing:.22em">INTERNSHIP OUTCOME · ${esc(deck.period)}</div>
      <h1 data-anim="title" style="align-self:center;font-family:var(--sans),var(--sans-zh);font-weight:200;font-size:min(11.6vw,19vh);line-height:.94;letter-spacing:-.025em;color:#fff">实习成果<br><span style="font-style:italic;font-weight:300">汇报</span></h1>
      <div data-anim="bottom" style="display:grid;grid-template-rows:auto auto;gap:1.6vh;border-top:1px solid rgba(255,255,255,.22);padding-top:2vh">
        <div data-anim="lead" class="lead" style="max-width:52ch;color:rgba(255,255,255,.86)">${esc(deck.subtitle)}</div>
        <div style="display:flex;justify-content:space-between;align-items:end">
          <div class="t-meta" style="color:rgba(255,255,255,.68)">${esc(deck.author)} · ${esc(deck.profile)}</div>
          <div class="t-meta" style="color:rgba(255,255,255,.6)">FLOW-MIND · ${esc(deck.period)}</div>
        </div>
      </div>
    </div>
  </div>
</section>`;
}

function renderProfile() {
  const item = slide("profile");
  return `<section class="slide hero light" data-animate="statement" data-slide-id="${esc(item.id)}" data-layout="${esc(item.layout)}">
  <div class="canvas-card" style="background:rgba(var(--paper-rgb),.88)">${chrome(item, 1)}
    <div style="flex:1;padding:0 0 4.5vh 0;display:flex;flex-direction:column;justify-content:space-between">
      <div data-anim="line" class="t-cat accent">PROFILE · ROLE · FOCUS</div>
      <h1 data-anim="statement" class="h-statement" style="margin-left:15vw;max-width:72vw;font-family:var(--sans),var(--sans-zh);font-weight:200;font-size:min(5.8vw,10.2vh);line-height:1.05;letter-spacing:-.035em">${splitAfter(item.title, "把").replace("工程闭环", '<span style="color:var(--accent);font-style:italic;font-weight:300">工程闭环</span>')}</h1>
      <div data-anim="anchor" style="margin-left:15vw;display:grid;grid-template-columns:1fr 1fr;gap:3vw;border-top:2px solid var(--accent);padding-top:2vh;max-width:72vw">
        <div>
          <div class="t-meta" style="margin-bottom:1vh">张琦 · PROFILE</div>
          <div class="t-body">${esc(item.bio.join(" · "))}</div>
        </div>
        <div>
          <div class="t-meta" style="margin-bottom:1vh">WORK SCOPE · 70 / 30</div>
          <div class="t-body">${esc(item.focus)}；${esc(item.support)}</div>
        </div>
      </div>
    </div>
  </div>
</section>`;
}

function renderTrajectory() {
  const item = slide("trajectory");
  const nodes = item.phases.map((phase, index) => `<div class="th-node ${index === item.phases.length - 1 ? "accent" : ""} ${index % 2 ? "down" : "up"}" data-anim="step">
          <span class="dot" style="${index === item.phases.length - 1 ? "background:var(--accent)" : "background:rgba(255,255,255,.82)"}"></span>
          <div class="label" style="width:19vw">
            <span class="yr" style="color:${index === item.phases.length - 1 ? "var(--accent-bright)" : "rgba(255,255,255,.62)"}">${esc(phase.date)}</span>
            <span class="name" style="color:${index === item.phases.length - 1 ? "var(--accent-bright)" : "var(--paper)"}">${esc(phase.name)}</span>
            <span class="desc" style="font-size:max(16px,.84vw);color:rgba(255,255,255,.7)">${esc(phase.desc)}</span>
          </div>
        </div>`).join("\n");
  const stats = item.stats.map((stat) => `<div style="border-top:1px solid rgba(255,255,255,.3);padding-top:1.2vh">
          <div style="font-family:var(--sans);font-weight:250;font-size:min(3.2vw,5.6vh);line-height:1;color:var(--paper)">${esc(stat.value)}</div>
          <div class="t-meta" style="color:rgba(255,255,255,.58);margin-top:.7vh">${esc(stat.label)}</div>
        </div>`).join("\n");
  return `<section class="slide dark" data-animate="timeline-walk" data-slide-id="${esc(item.id)}" data-layout="${esc(item.layout)}">
  <div class="canvas-card">${chrome(item, 2)}
    <div data-anim="line" style="display:flex;flex-direction:column;gap:1.5vh">
      <div class="t-cat on-dark">GIT TRAJECTORY · 7 NATURAL WEEKS</div>
      <h2 style="font-family:var(--sans),var(--sans-zh);font-weight:200;font-size:min(5.2vw,9.2vh);line-height:1.03;letter-spacing:-.03em;color:var(--paper)">${splitAfter(item.title, "，")}</h2>
      <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:2vw;max-width:52vw;margin-top:1.4vh">${stats}</div>
    </div>
    <div class="timeline-h nav-safe-bottom-tight" style="margin-top:3vh;color:var(--paper)">
      <div class="tl-row" style="grid-template-columns:repeat(4,1fr)">${nodes}</div>
    </div>
  </div>
</section>`;
}

function renderArchitecture() {
  const item = slide("agent-architecture");
  const descriptions = item.layers.map((layer) => `<div style="display:grid;grid-template-columns:3.6vw 1fr;gap:1.2vw;padding:1.5vh 0;border-top:1px solid var(--border-subtle)">
          <div style="font-family:var(--sans);font-weight:250;font-size:min(3vw,5.2vh);line-height:1;color:${layer.no === "02" ? "var(--accent)" : "var(--text-primary)"}">${esc(layer.no)}</div>
          <div>
            <div class="t-meta" style="margin-bottom:.5vh">${esc(layer.name)}</div>
            <div class="t-h-prod" style="margin-bottom:1.6vh">${esc(layer.headline)}</div>
            <div class="t-body-sm">${esc(layer.body)}</div>
          </div>
        </div>`).join("\n");
  const gateLabels = item.gates.map((gate, index) => `<div style="display:flex;align-items:center;gap:.7vw;padding:.8vh .8vw;background:${index === 1 ? "var(--accent)" : "var(--paper)"};color:${index === 1 ? "var(--accent-on)" : "var(--text-primary)"}">
          <span class="t-meta" style="color:inherit">G${index + 1}</span><span style="font-size:max(16px,.88vw);font-weight:500">${esc(gate)}</span>
        </div>`).join("\n");
  return `<section class="slide light" data-animate="system-diagram" data-slide-id="${esc(item.id)}" data-layout="${esc(item.layout)}">
  <div class="canvas-card">${chrome(item, 3)}
    <div data-anim="line" style="display:flex;flex-direction:column;gap:1.4vh">
      <div class="t-cat accent">AGENT WEB · RESPONSIBILITY BOUNDARY</div>
      <h2 style="font-family:var(--sans),var(--sans-zh);font-weight:200;font-size:min(4.8vw,8.5vh);line-height:1.04;letter-spacing:-.03em;max-width:84vw">${splitAfter(item.title, "，")}</h2>
    </div>
    <div data-anim="up" class="frame grid-2-6-6 nav-safe-bottom-tight" style="margin-top:4vh;gap:4vw">
      <div style="display:flex;flex-direction:column;justify-content:center">${descriptions}</div>
      <div style="position:relative;display:grid;place-items:center;min-height:47vh;background:var(--grey-1)">
        <svg viewBox="0 0 520 520" aria-label="Agent Web 三层同心架构" style="width:min(38vw,50vh);height:min(38vw,50vh);overflow:visible">
          <circle data-anim="ring" cx="260" cy="260" r="218" fill="none" stroke="#0a0a0a" stroke-width="2" opacity=".34"></circle>
          <circle data-anim="ring" cx="260" cy="260" r="148" fill="none" stroke="#002FA7" stroke-width="4"></circle>
          <circle data-anim="ring" cx="260" cy="260" r="78" fill="#002FA7" stroke="#002FA7" stroke-width="2"></circle>
          <line x1="414" y1="106" x2="482" y2="62" stroke="#0a0a0a" stroke-width="2"></line>
          <line x1="366" y1="184" x2="482" y2="184" stroke="#002FA7" stroke-width="2"></line>
          <line x1="260" y1="260" x2="482" y2="306" stroke="#002FA7" stroke-width="2"></line>
        </svg>
        <div style="position:absolute;right:1.6vw;top:6vh;display:flex;flex-direction:column;gap:.3vh"><span class="t-meta">OUTER · 01</span><strong style="font-size:max(16px,1vw);font-weight:600">交互层</strong></div>
        <div style="position:absolute;right:1.6vw;top:20vh;display:flex;flex-direction:column;gap:.3vh"><span class="t-meta" style="color:var(--accent)">MIDDLE · 02</span><strong style="font-size:max(16px,1vw);font-weight:600;color:var(--accent)">编排层</strong></div>
        <div style="position:absolute;right:1.6vw;top:34vh;display:flex;flex-direction:column;gap:.3vh"><span class="t-meta">CORE · 03</span><strong style="font-size:max(16px,1vw);font-weight:600">确定性服务</strong></div>
        <div style="position:absolute;left:1.4vw;right:1.4vw;bottom:1.8vh;display:grid;grid-template-columns:repeat(3,1fr);gap:.8vw">${gateLabels}</div>
      </div>
    </div>
  </div>
</section>`;
}

function renderQualityEvidence() {
  const item = slide("agent-quality-evidence");
  return `<section class="slide light" data-animate="matrix-fill" data-slide-id="${esc(item.id)}" data-layout="${esc(item.layout)}">
  <div class="canvas-card">${chrome(item, 4)}
    <div data-anim="line" style="display:grid;grid-template-columns:1fr auto;gap:3vw;align-items:end">
      <div style="display:flex;flex-direction:column;gap:1.3vh">
        <div class="t-cat accent">GENERATION PIPELINE · QUALITY LOOP</div>
        <h2 style="font-family:var(--sans),var(--sans-zh);font-weight:200;font-size:min(4.8vw,8.5vh);line-height:1.04;letter-spacing:-.03em">${splitAfter(item.title, "，")}</h2>
      </div>
      <div style="border-top:2px solid var(--accent);padding-top:1vh;min-width:18vw">
        <div class="t-meta">TARGET CONTRACT</div>
        <div style="font-size:max(18px,1.15vw);font-weight:500;margin-top:.8vh">${esc(item.fileContract)}</div>
      </div>
    </div>
    <div data-anim="up" style="display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:2vw;margin-top:4vh">
      <article>
        <div class="frame-img r-21x9" style="background:#fff">
          <img src="${endToEndImage}" data-image-slot="s15-grid-21x9" alt="从业务需求到业务入口的端到端生成链路" loading="eager">
        </div>
        <div style="display:flex;justify-content:space-between;gap:1vw;border-top:1px solid var(--border-subtle);padding-top:1.2vh;margin-top:1.2vh">
          <div class="t-h-prod">端到端交付链路</div><div class="t-meta">REQUIREMENT → ENTRY</div>
        </div>
      </article>
      <article>
        <div class="frame-img r-21x9" style="background:#fff">
          <img src="${qualityLoopImage}" data-image-slot="s15-grid-21x9" alt="静态校验、构建、评审、修复与重验的质量循环" loading="eager">
        </div>
        <div style="display:flex;justify-content:space-between;gap:1vw;border-top:1px solid var(--border-subtle);padding-top:1.2vh;margin-top:1.2vh">
          <div class="t-h-prod">质量与 Repair 循环</div><div class="t-meta">VALIDATE → REPAIR → RECHECK</div>
        </div>
      </article>
    </div>
    <div data-anim="up" class="nav-safe-bottom-tight" style="display:grid;grid-template-columns:1.15fr 1fr 1.35fr;gap:0;margin-top:3vh;border-top:1px solid var(--ink)">
      <div style="padding:1.6vh 1.4vw 0 0;border-right:1px solid var(--border-subtle)">
        <div style="font-family:var(--sans);font-weight:250;font-size:min(4vw,6.8vh);line-height:1;color:var(--accent)">5 / 7</div>
        <div class="t-meta" style="margin-top:.8vh">普通 / 动态数据文件契约</div>
      </div>
      <div style="padding:1.6vh 1.4vw;border-right:1px solid var(--border-subtle)">
        <div class="t-meta" style="color:var(--accent);margin-bottom:.8vh">≤ 3 ROUNDS</div>
        <div class="t-body-sm">自动 Repair 硬上限；耗尽后停止自动修复。</div>
      </div>
      <div style="padding:1.6vh 0 0 1.4vw">
        <div class="t-meta" style="margin-bottom:.8vh">HARD / SOFT / ROLLBACK</div>
        <div class="t-body-sm">硬门禁禁止写入；软门禁人工受限覆盖；异常 Repair 恢复轮次前快照。</div>
      </div>
    </div>
  </div>
</section>`;
}

function renderSampleEvidence() {
  const item = slide("sample-evidence");
  const rows = item.metrics.map((metric, index) => `<div class="ledger-row" style="display:grid;grid-template-columns:minmax(0,2fr) minmax(0,7fr) 3vw;gap:2vw;align-items:center;flex:1;padding:1vh 0;border-bottom:1px solid rgba(255,255,255,.18)">
        <div class="ledger-num" data-anim="number" style="font-family:var(--sans);font-weight:200;font-size:min(4.4vw,7.2vh);line-height:.9;color:${index === 1 ? "var(--accent-bright)" : "var(--paper)"}">${esc(metric.value)}</div>
        <div class="ledger-label" data-anim="label">
          <div style="font-size:max(18px,1.35vw);font-weight:500;color:var(--paper)">${esc(metric.label)}</div>
          <div class="t-meta" style="color:rgba(255,255,255,.5);margin-top:.5vh">GOLD SAMPLE · METRIC ${String(index + 1).padStart(2, "0")}</div>
        </div>
        <div class="ledger-icon" data-anim="icon" aria-hidden="true" style="width:2.2vw;height:2.2vw;border:1px solid ${index === 1 ? "var(--accent-bright)" : "rgba(255,255,255,.5)"};display:grid;place-items:center"><span style="width:.65vw;height:.65vw;background:${index === 1 ? "var(--accent-bright)" : "rgba(255,255,255,.78)"}"></span></div>
      </div>`).join("\n");
  return `<section class="slide hero dark" data-animate="stacked-ledger" data-slide-id="${esc(item.id)}" data-layout="${esc(item.layout)}">
  <div class="canvas-card" style="background:rgba(var(--ink-rgb),.91)">${chrome(item, 5)}
    <div style="display:grid;grid-template-columns:1fr auto;gap:3vw;align-items:end;margin-bottom:2.8vh">
      <div>
        <div class="t-cat on-dark" style="margin-bottom:1.2vh">GOLD SAMPLE · RUNTIME EVIDENCE</div>
        <h2 style="font-family:var(--sans),var(--sans-zh);font-weight:200;font-size:min(4.4vw,7.8vh);line-height:1.04;letter-spacing:-.03em;color:var(--paper)">${esc(item.title)}</h2>
      </div>
      <div style="border-top:2px solid var(--accent-bright);padding-top:1vh;max-width:27vw">
        <div class="t-meta" style="color:var(--accent-bright)">CASE</div>
        <div style="font-size:max(18px,1.15vw);font-weight:500;color:var(--paper);margin-top:.8vh">${esc(item.caseName)}</div>
      </div>
    </div>
    <div class="stacked-ledger nav-safe-bottom-tight" data-anim="ledger" style="display:flex;flex-direction:column;flex:1;min-height:0">${rows}
      <div style="padding-top:1.4vh;display:flex;justify-content:space-between;gap:2vw">
        <div class="t-body-sm" style="color:rgba(255,255,255,.78)">${esc(item.extra)}</div>
        <div class="t-meta" style="color:rgba(255,255,255,.48)">EVIDENCE · e7b1e9a · PROVISION SAMPLE</div>
      </div>
    </div>
  </div>
</section>`;
}

function renderPlatformBusinessBase() {
  const item = slide("platform-business-base");
  const cards = item.blocks.map((block, index) => `<article class="sub-card" data-anim="card" style="border-radius:0;display:grid;grid-template-columns:3.8vw minmax(0,1.15fr) minmax(0,1.8fr);gap:1.5vw;align-items:center;padding:1.8vh 1.5vw;background:${index === 1 ? "var(--grey-1)" : "#f5f5f4"}">
        <div style="font-family:var(--sans);font-weight:250;font-size:min(3.5vw,6vh);line-height:1;color:${index === 2 ? "var(--accent)" : "var(--text-primary)"}">${esc(block.no)}</div>
        <div>
          <div class="t-meta" style="margin-bottom:.7vh">${esc(block.name)}</div>
          <div class="t-h-prod">${esc(block.headline)}</div>
        </div>
        <div class="t-body-sm">${esc(block.body)}</div>
      </article>`).join("\n");
  return `<section class="slide light" data-animate="grid-reveal" data-slide-id="${esc(item.id)}" data-layout="${esc(item.layout)}">
  <div class="canvas-card">${chrome(item, 6)}
    <div data-anim="line" style="display:flex;flex-direction:column;gap:1.4vh">
      <div class="t-cat accent">PLATFORM + BUSINESS BASE · 30%</div>
      <h2 style="font-family:var(--sans),var(--sans-zh);font-weight:200;font-size:min(4.8vw,8.5vh);line-height:1.04;letter-spacing:-.03em">${splitAfter(item.title, "Agent 产物")}</h2>
    </div>
    <div class="frame grid-2-4-8 nav-safe-bottom-tight" style="margin-top:4vh;gap:4vw;align-items:stretch">
      <div style="display:flex;flex-direction:column;justify-content:space-between;min-height:0">
        <div>
          <div class="t-meta" style="margin-bottom:1vh">ROLE IN THE LOOP</div>
          <p class="lead" style="font-size:max(18px,1.25vw);line-height:1.55">底座承担可靠运行与业务承接，Agent 负责把可验证产物接入既有链路。</p>
        </div>
        <div style="border-top:2px solid var(--accent);padding-top:1.4vh">
          <div class="t-meta" style="margin-bottom:.8vh">TRACEABLE COMMITS</div>
          <div class="t-body-sm">Platform · 6302bb7 / c471a1b / 9c256cc / 5b53c75 / a05d69a</div>
          <div class="t-body-sm" style="margin-top:.7vh">Business Base · 02fe89f / b7fb91b / 9415704 / ef3f0d0 / 364667a</div>
          <div class="t-body-sm" style="margin-top:.7vh;color:var(--accent)">入口登记 · 573aae2</div>
        </div>
      </div>
      <div style="display:grid;grid-template-rows:repeat(3,1fr);gap:1.6vh;min-height:0;height:100%;align-self:stretch">${cards}</div>
    </div>
  </div>
</section>`;
}

function renderGrowthReflection() {
  const item = slide("growth-reflection");
  const cards = item.forces.map((force) => `<div class="card-fill" style="display:grid;grid-template-columns:5.4vw minmax(0,1fr);gap:1.4vw;align-items:center;padding:2vh 1.6vw;min-height:0">
          <div data-anim="number" style="font-family:var(--sans);font-weight:200;font-size:min(5.2vw,9vh);line-height:.9;color:var(--accent)">${esc(force.no)}</div>
          <div>
            <div class="t-meta" style="margin-bottom:.7vh">${esc(force.name)}</div>
            <div class="t-h-prod" style="margin-bottom:1.6vh">${esc(force.headline)}</div>
            <div class="t-body-sm">${esc(force.body)}</div>
          </div>
        </div>`).join("\n");
  return `<section class="slide light" data-animate="three-forces" data-slide-id="${esc(item.id)}" data-layout="${esc(item.layout)}">
  <div class="canvas-card">${chrome(item, 7)}
    <div data-anim="line" class="t-cat accent">GROWTH · VALUE · NEXT ITERATION</div>
    <div data-anim="up" class="nav-safe-bottom-tight" style="display:grid;grid-template-columns:31.25% 68.75%;gap:0;flex:1;margin-top:2.8vh;min-height:0">
      <div style="background:var(--ink);color:var(--paper);padding:3vh 2.4vw;display:flex;flex-direction:column;justify-content:space-between;position:relative;overflow:hidden">
        <div class="dot-mat" aria-hidden="true" style="position:absolute;right:-4vw;bottom:-3vh;width:17vw;height:17vw;color:var(--paper);opacity:.14"></div>
        <div>
          <div class="t-meta" style="color:rgba(255,255,255,.58);margin-bottom:2vh">ENGINEERING JUDGMENT</div>
          <h2 style="font-family:var(--sans),var(--sans-zh);font-weight:200;font-size:min(4.1vw,7.2vh);line-height:1.07;letter-spacing:-.03em;color:var(--paper)">${splitAfter(item.title, "，")}</h2>
        </div>
        <p class="t-body" style="color:rgba(255,255,255,.78);max-width:23ch">从“功能能否跑”，走向“边界是否明确、失败是否可恢复、证据是否可追溯”。</p>
      </div>
      <div style="display:grid;grid-template-rows:repeat(3,1fr);gap:1.5vh;padding-left:1.5vw;min-height:0">${cards}</div>
    </div>
  </div>
</section>`;
}

function renderClosing() {
  const item = slide("future-closing");
  const takeaways = item.takeaways.map((takeaway, index) => `<li data-anim="takeaway" style="display:grid;grid-template-columns:4.6vw 1fr;gap:1.5vw;align-items:start;padding:2.1vh 0;border-top:1px solid var(--border-subtle);${index === item.takeaways.length - 1 ? "border-bottom:2px solid var(--accent)" : ""}">
          <div style="font-family:var(--sans);font-weight:200;font-size:min(4vw,7vh);line-height:.9;color:${index === item.takeaways.length - 1 ? "var(--accent)" : "var(--text-primary)"}">${esc(takeaway.no)}</div>
          <div>
            <div style="font-family:var(--sans),var(--sans-zh);font-weight:500;font-size:max(18px,1.35vw);line-height:1.2;color:${index === item.takeaways.length - 1 ? "var(--accent)" : "var(--text-primary)"};margin-bottom:1.6vh">${esc(takeaway.title)}</div>
            <p style="font-family:var(--sans),var(--sans-zh);font-size:max(16px,.94vw);line-height:1.5;color:var(--text-secondary);font-weight:400">${esc(takeaway.body)}</p>
          </div>
        </li>`).join("\n");
  return `<section class="slide split" data-animate="split-statement" data-slide-id="${esc(item.id)}" data-layout="${esc(item.layout)}">
  <div class="canvas-card">
    <div class="split-half">
      <div class="half b-accent" style="padding:5.6vh 3.6vw 7.5vh;justify-content:space-between;position:relative;overflow:hidden">
        <canvas class="ascii-bg" aria-hidden="true"></canvas>
        <div class="chrome-min" style="margin-bottom:0;position:relative;z-index:1"><div class="l">${totalNo} / ${totalNo}</div><div class="r">CLOSING · 张琦</div></div>
        <div style="display:flex;flex-direction:column;gap:1.2vh;position:relative;z-index:1">
          <div class="t-meta" style="color:rgba(255,255,255,.78);letter-spacing:.22em;margin-bottom:1.4vh">MANIFESTO · VERIFIED OUTCOME</div>
          <div class="kpi-thin" data-anim="manifesto" style="font-family:var(--sans),var(--sans-zh);font-size:min(5.2vw,9.2vh);font-weight:200;line-height:1;letter-spacing:-.03em;color:#fff">让 AI 能力</div>
          <div class="kpi-thin" data-anim="manifesto" style="font-family:var(--sans),var(--sans-zh);font-size:min(5.2vw,9.2vh);font-weight:200;line-height:1;letter-spacing:-.03em;color:#fff">落在<span style="font-style:italic;font-weight:300">可验证</span>的</div>
          <div class="kpi-thin" data-anim="manifesto" style="font-family:var(--sans),var(--sans-zh);font-size:min(5.2vw,9.2vh);font-weight:200;line-height:1;letter-spacing:-.03em;color:#fff">业务结果上</div>
        </div>
        <div style="display:flex;justify-content:space-between;align-items:end;border-top:1px solid rgba(255,255,255,.22);padding-top:2vh;position:relative;z-index:1">
          <div class="t-meta" style="color:rgba(255,255,255,.62)">${esc(deck.author)} · ${esc(deck.profile)}</div>
          <div class="t-meta" style="color:rgba(255,255,255,.62)">${esc(deck.period)}</div>
        </div>
      </div>
      <div class="half" style="padding:5.6vh 3.6vw 7.5vh;justify-content:space-between">
        <div class="chrome-min"><div class="l">TAKEAWAYS</div><div class="r">03 RULES</div></div>
        <ul class="takeaway-list nav-safe-bottom-tight" style="list-style:none;display:flex;flex-direction:column;gap:0;margin:0;padding-left:0">${takeaways}</ul>
        <div class="t-meta" style="color:var(--text-helper);text-align:right">→ 汇报结束 · Q&amp;A</div>
      </div>
    </div>
  </div>
</section>`;
}

const slideHtml = [
  renderCover(),
  renderProfile(),
  renderTrajectory(),
  renderArchitecture(),
  renderQualityEvidence(),
  renderSampleEvidence(),
  renderPlatformBusinessBase(),
  renderGrowthReflection(),
  renderClosing(),
].join("\n\n");

const speakerNotes = slides.map(({ id, title, section, minutes, purpose, talk, transition }) => ({
  id,
  title,
  section,
  minutes,
  purpose,
  talk,
  transition,
}));

let html = readFileSync(templatePath, "utf8");
html = html.replace(/<title>[\s\S]*?<\/title>/, `<title>${esc(deck.title)}</title>`);
html = html.replace(/<link rel="preconnect" href="https:\/\/fonts\.googleapis\.com">\s*/g, "");
html = html.replace(/<link rel="preconnect" href="https:\/\/fonts\.gstatic\.com" crossorigin>\s*/g, "");
html = html.replace(/<link href="https:\/\/fonts\.googleapis\.com\/[^"]+" rel="stylesheet">\s*/g, "");
html = html.replace("</title>", "</title>\n<link rel=\"icon\" href=\"data:,\">");
html = html.replace(/\s*<script src="https:\/\/unpkg\.com\/lucide@latest\/dist\/umd\/lucide\.min\.js"><\/script>\s*<script>lucide\.createIcons\(\);<\/script>/, "");

const deckRegion = /<div id="deck">[\s\S]*?<\/div>\s*<div id="nav"><\/div>/;
if (!deckRegion.test(html)) throw new Error("Swiss template deck insertion region not found");
html = html.replace(deckRegion, `<div id="deck">\n${slideHtml}\n</div>\n\n<div id="nav"></div>`);

const notesRegion = /const SPEAKER_NOTES = \[[\s\S]*?\];\s*window\.__SPEAKER_NOTES__ = SPEAKER_NOTES;/;
if (!notesRegion.test(html)) throw new Error("Swiss template speaker notes region not found");
html = html.replace(notesRegion, `const SPEAKER_NOTES = ${JSON.stringify(speakerNotes, null, 2)};\nwindow.__SPEAKER_NOTES__ = SPEAKER_NOTES;`);

const localMotionImport = "await import('./assets/motion.min.js')";
if (!html.includes(localMotionImport)) throw new Error("Swiss template Motion One import not found");
const motionDataUri = `data:text/javascript;base64,${Buffer.from(readFileSync(motionPath)).toString("base64")}`;
html = html.replace(localMotionImport, `await import('${motionDataUri}')`);
html = html.replace(
  "motion = await import('https://cdn.jsdelivr.net/npm/motion@11.11.17/+esm');",
  "throw e1;",
);

if (html.includes("[必填]")) throw new Error("Unresolved [必填] placeholder remains in output");
if ((html.match(/<section\b[^>]*class="[^"]*\bslide\b/g) ?? []).length !== slides.length) {
  throw new Error("Rendered slide count does not match shared slide data");
}

writeFileSync(outputPath, html, "utf8");
console.log(`Built ${slides.length} slides -> ${outputPath}`);
console.log(`Planned talk time: ${deck.plannedMinutes.toFixed(1)} / ${deck.targetMinutes.toFixed(1)} minutes`);
