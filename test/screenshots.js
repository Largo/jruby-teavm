#!/usr/bin/env node
// screenshots.js [dist-dir] [out-dir]: open web/visual.html (from dist/) in headless Chromium, run both presets,
// save screenshots (build/screenshots/*.png) and print what the page reports: run output, frames per second and
// the time per frame spent in the Ruby animation block (Ruby + JBox2D), before and after dropping boxes by clicking.
// The page is served on 127.0.0.1 only (random port). Needs Playwright + Chromium (setup/fetch.sh --test).
//   node test/screenshots.js            # dist/ -> build/screenshots/
'use strict';
const fs = require('fs');
const path = require('path');
const http = require('http');

const here = path.resolve(__dirname, '..');
const dist = path.resolve(process.argv[2] || path.join(here, 'dist'));
const out = path.resolve(process.argv[3] || path.join(here, 'build', 'screenshots'));
function loadPlaywright() {
  for (const c of ['playwright', '/usr/local/lib/node_modules/playwright', path.join(here, 'shared', 'tools', 'node_modules', 'playwright')]) {
    try { return require(c); } catch (e) { /* next */ }
  }
  console.error('screenshots.js: cannot find the "playwright" node module (setup/fetch.sh --test installs it)');
  process.exit(3);
}
const playwright = loadPlaywright();
const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.zip': 'application/zip', '.txt': 'text/plain; charset=utf-8' };

function serve() {
  const server = http.createServer((req, res) => {
    const p = decodeURIComponent(new URL(req.url, 'http://x').pathname);
    const f = path.normalize(path.join(dist, p === '/' ? 'index.html' : p));
    if (!f.startsWith(dist)) { res.writeHead(403); return res.end(); }
    fs.readFile(f, (err, data) => {
      if (err) { res.writeHead(404); return res.end('not found'); }
      res.writeHead(200, { 'Content-Type': MIME[path.extname(f)] || 'application/octet-stream', 'Cache-Control': 'no-store' });
      res.end(data);
    });
  });
  return new Promise((ok) => server.listen(0, '127.0.0.1', () => ok(server)));
}

async function open(browser, base, query, viewport) {
  const page = await browser.newPage({ viewport });
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  await page.goto(base + 'visual.html' + query);
  await page.waitForFunction(() => window.__done, null, { timeout: 180000 });
  return { page, errors };
}

const state = (page) => page.evaluate(() => ({
  run: window.__lastRun && { preset: window.__lastRun.preset, ms: window.__lastRun.ms, out: window.__lastRun.out, error: window.__lastRun.error },
  stats: window.__canvasStats || null, animationError: window.__visualError || null,
}));

(async () => {
  fs.mkdirSync(out, { recursive: true });
  const server = await serve();
  const base = `http://127.0.0.1:${server.address().port}/`;
  const browser = await playwright.chromium.launch();
  const report = {};
  let failed = false;
  try {
    // 1. physics: the preset as it starts, then after 25 clicks (one box each)
    let { page, errors } = await open(browser, base, '', { width: 1400, height: 980 });
    await page.waitForTimeout(3500);
    report.physics = await state(page);
    await page.screenshot({ path: path.join(out, 'visual-physics.png') });
    await page.locator('#stage').screenshot({ path: path.join(out, 'visual-physics-canvas.png') });
    const box = await page.locator('#stage').boundingBox();
    for (let i = 0; i < 25; i++) {
      await page.mouse.click(box.x + box.width * (0.2 + 0.6 * ((i * 7) % 25) / 25), box.y + box.height * 0.12);
      await page.waitForTimeout(120);
    }
    await page.waitForTimeout(2500);
    report.physicsAfterClicks = await state(page);   // 48 + 25 bodies
    await page.locator('#stage').screenshot({ path: path.join(out, 'visual-physics-clicked.png') });
    report.physicsErrors = errors;
    await page.close();

    // 2. QR code: the preset, then another text typed into the input box (the page runs the code again)
    ({ page, errors } = await open(browser, base, '?preset=qr', { width: 1400, height: 980 }));
    await page.waitForTimeout(500);
    report.qr = await state(page);
    await page.screenshot({ path: path.join(out, 'visual-qr.png') });
    await page.locator('#stage').screenshot({ path: path.join(out, 'visual-qr-canvas.png') });
    await page.fill('#qr-text', 'JRuby 10 + ZXing 3.5 in TeaVM: 日本語もOK ✓');
    await page.waitForFunction(() => window.__lastRun && window.__lastRun.out.includes('日本語'), null, { timeout: 60000 });
    report.qrTyped = await state(page);
    await page.locator('#stage').screenshot({ path: path.join(out, 'visual-qr-typed.png') });
    report.qrErrors = errors;
    await page.close();

    // 3. a phone-sized viewport (layout check)
    ({ page, errors } = await open(browser, base, '', { width: 400, height: 860 }));
    await page.waitForTimeout(1500);
    report.phone = await state(page);
    report.phoneOverflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
    await page.screenshot({ path: path.join(out, 'visual-phone.png'), fullPage: true });
    report.phoneErrors = errors;
    await page.close();
  } catch (e) {
    failed = true;
    report.exception = String(e && e.stack || e);
  }
  await browser.close();
  server.close();
  console.log(JSON.stringify(report, null, 1));
  const errs = [].concat(report.physicsErrors || [], report.qrErrors || [], report.phoneErrors || []);
  const ok = !failed && !errs.length && report.physics && report.physics.stats && report.physics.stats.fps > 0
    && !report.physics.animationError && report.qr && /round trip OK/.test(report.qr.run.out)
    && report.qrTyped && /round trip OK/.test(report.qrTyped.run.out);
  console.log((ok ? 'OK' : 'FAILED') + '  screenshots in ' + out + ': ' + fs.readdirSync(out).filter((f) => f.endsWith('.png')).join(' '));
  process.exit(ok ? 0 : 1);
})();
