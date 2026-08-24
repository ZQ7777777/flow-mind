import { chromium } from "file:///C:/Users/zq/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs";
import fs from "node:fs/promises";
import path from "node:path";

const url = "http://127.0.0.1:8765/%E5%BC%A0%E7%90%A6-%E5%AE%9E%E4%B9%A0%E6%88%90%E6%9E%9C%E6%B1%87%E6%8A%A5.html";
const qaRoot = "C:/Users/zq/Desktop/resume/flow-mind/outputs/internship-report/qa";
const viewports = [
  { width: 1920, height: 1080 },
  { width: 1366, height: 768 },
];

const browser = await chromium.launch({
  headless: true,
  executablePath: "C:/Program Files/Google/Chrome/Application/chrome.exe",
});
const report = { viewports: [], interaction: {}, errors: [] };

function assert(condition, message) {
  if (!condition) report.errors.push(message);
}

for (const viewport of viewports) {
  const context = await browser.newContext({ viewport });
  const page = await context.newPage();
  const consoleErrors = [];
  page.on("console", (message) => {
    if (["error", "warning"].includes(message.type())) consoleErrors.push(`${message.type()}: ${message.text()}`);
  });
  page.on("pageerror", (error) => consoleErrors.push(`pageerror: ${error.message}`));

  await page.goto(url, { waitUntil: "networkidle" });
  await page.waitForTimeout(1100);
  const base = await page.evaluate(() => ({
    title: document.title,
    slides: document.querySelectorAll(".slide").length,
    ids: [...document.querySelectorAll(".slide")].map((slide) => slide.dataset.slideId),
    layouts: [...document.querySelectorAll(".slide")].map((slide) => slide.dataset.layout),
    dataImages: [...document.images].filter((image) => image.src.startsWith("data:image/")).length,
    externalImages: [...document.images].filter((image) => !image.src.startsWith("data:image/")).map((image) => image.src),
  }));
  assert(base.slides === 9, `${viewport.width}x${viewport.height}: expected 9 slides, got ${base.slides}`);
  assert(base.dataImages === 2, `${viewport.width}x${viewport.height}: expected 2 embedded images, got ${base.dataImages}`);
  assert(base.externalImages.length === 0, `${viewport.width}x${viewport.height}: external images remain`);

  const shotDir = path.join(qaRoot, `html-${viewport.width}x${viewport.height}`);
  await fs.mkdir(shotDir, { recursive: true });
  await page.keyboard.press("Home");
  const slides = [];
  for (let index = 0; index < 9; index += 1) {
    if (index > 0) await page.keyboard.press("ArrowRight");
    await page.waitForTimeout(2850);
    const metrics = await page.evaluate((slideIndex) => {
      const slide = document.querySelectorAll(".slide")[slideIndex];
      const slideRect = slide.getBoundingClientRect();
      const parents = new Set();
      const walker = document.createTreeWalker(slide, NodeFilter.SHOW_TEXT);
      while (walker.nextNode()) {
        if (walker.currentNode.nodeValue.trim()) parents.add(walker.currentNode.parentElement);
      }
      const textOutside = [...parents].flatMap((element) => {
        if (!element) return [];
        const style = getComputedStyle(element);
        if (style.display === "none" || style.visibility === "hidden" || Number(style.opacity) === 0) return [];
        const rect = element.getBoundingClientRect();
        const outside = rect.left < slideRect.left - 1 || rect.top < slideRect.top - 1 || rect.right > slideRect.right + 1 || rect.bottom > slideRect.bottom + 1;
        return outside ? [{ tag: element.tagName, cls: element.className, text: element.textContent.trim().replace(/\s+/g, " ").slice(0, 80), rect: { left: rect.left, top: rect.top, right: rect.right, bottom: rect.bottom } }] : [];
      });
      return {
        id: slide.dataset.slideId,
        layout: slide.dataset.layout,
        deckTransform: document.querySelector("#deck").style.transform,
        activeDot: [...document.querySelectorAll("button[aria-label^='Page ']")].findIndex((dot) => dot.classList.contains("active")),
        slideRect: { x: slideRect.x, y: slideRect.y, width: slideRect.width, height: slideRect.height },
        scrollOverflow: { x: slide.scrollWidth - slide.clientWidth, y: slide.scrollHeight - slide.clientHeight },
        textOutside,
      };
    }, index);
    assert(metrics.activeDot === index, `${viewport.width}x${viewport.height}: active dot mismatch on slide ${index + 1}`);
    assert(metrics.textOutside.length === 0, `${viewport.width}x${viewport.height}: text outside slide ${index + 1}: ${JSON.stringify(metrics.textOutside)}`);
    await page.screenshot({ path: path.join(shotDir, `slide-${index + 1}.png`) });
    slides.push(metrics);
  }

  report.viewports.push({ viewport, base, slides, consoleErrors });
  assert(consoleErrors.length === 0, `${viewport.width}x${viewport.height}: console warnings/errors: ${consoleErrors.join(" | ")}`);
  await context.close();
}

{
  const context = await browser.newContext({ viewport: { width: 1920, height: 1080 } });
  const page = await context.newPage();
  await page.goto(url, { waitUntil: "networkidle" });
  await page.waitForTimeout(1100);
  const lowPowerBefore = await page.evaluate(() => document.body.classList.contains("low-power"));
  await page.keyboard.press("b");
  const lowPowerAfter = await page.evaluate(() => document.body.classList.contains("low-power"));
  await page.keyboard.press("End");
  const afterEnd = await page.evaluate(() => document.querySelector("#deck").style.transform);
  await page.waitForTimeout(750);
  await page.keyboard.press("Home");
  const afterHome = await page.evaluate(() => document.querySelector("#deck").style.transform);
  await page.keyboard.press("Escape");
  const overviewOpen = await page.evaluate(() => getComputedStyle(document.querySelector("#overview")).display !== "none");
  await page.keyboard.press("Escape");
  const popupPromise = page.waitForEvent("popup", { timeout: 3000 }).catch(() => null);
  await page.keyboard.press("p");
  const audience = await popupPromise;
  await page.waitForTimeout(500);
  const presenter = await page.evaluate(() => ({
    active: document.body.classList.contains("ppt-presenter"),
    slideCount: document.querySelector("#ppt-page-count")?.textContent,
    plan: document.querySelector("#ppt-plan")?.textContent,
    noteTitle: document.querySelector("#ppt-notes-title")?.textContent,
    noteLength: document.querySelector("#ppt-note-editor")?.value.length,
    sync: document.querySelector("#ppt-sync")?.textContent,
  }));
  await page.locator("#ppt-timer-toggle").click();
  await page.waitForTimeout(1800);
  const timer = await page.evaluate(() => ({ clock: document.querySelector("#ppt-clock")?.textContent, button: document.querySelector("#ppt-timer-toggle")?.textContent }));
  await page.keyboard.press("b");
  await page.waitForTimeout(100);
  const audienceBlack = audience ? await audience.evaluate(() => {
    const cover = document.querySelector("#ppt-audience-cover");
    return { display: getComputedStyle(cover).display, background: getComputedStyle(cover).backgroundColor };
  }) : null;
  await page.keyboard.press("w");
  await page.waitForTimeout(100);
  const audienceWhite = audience ? await audience.evaluate(() => {
    const cover = document.querySelector("#ppt-audience-cover");
    return { display: getComputedStyle(cover).display, background: getComputedStyle(cover).backgroundColor };
  }) : null;
  await page.keyboard.press("f");
  const frozen = await page.evaluate(() => document.querySelector("#ppt-sync")?.textContent);
  await page.keyboard.press("f");
  await page.locator("#ppt-timer-toggle").click();
  report.interaction = { afterEnd, afterHome, overviewOpen, lowPowerBefore, lowPowerAfter, presenter, timer, audienceOpened: Boolean(audience), audienceBlack, audienceWhite, frozen };
  assert(afterEnd === "translateX(-800vw)", `End shortcut failed: ${afterEnd}`);
  assert(afterHome === "translateX(0vw)", `Home shortcut failed: ${afterHome}`);
  assert(overviewOpen, "Escape overview failed");
  assert(lowPowerBefore !== lowPowerAfter, "B low-power toggle failed in canvas mode");
  assert(presenter.active, "P presenter mode failed");
  assert(presenter.noteLength > 0, "Presenter notes missing");
  assert(timer.clock !== "00:00", `Timer failed: ${timer.clock}`);
  assert(frozen?.includes("已冻结"), `Freeze failed: ${frozen}`);
  assert(Boolean(audience), "Audience window did not open");
  if (audience) {
    assert(audienceBlack.display === "block" && audienceBlack.background === "rgb(0, 0, 0)", `Black screen failed: ${JSON.stringify(audienceBlack)}`);
    assert(audienceWhite.display === "block" && audienceWhite.background === "rgb(255, 255, 255)", `White screen failed: ${JSON.stringify(audienceWhite)}`);
  }
  if (audience) await audience.close();
  await context.close();
}

await browser.close();
console.log(JSON.stringify(report, null, 2));
if (report.errors.length) process.exitCode = 1;
