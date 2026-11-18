const { chromium } = require('playwright');
const fs = require('fs');
const jobs = JSON.parse(fs.readFileSync(process.argv[2]));
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--ignore-gpu-blocklist'] });
  const p = await b.newPage();
  p.on('pageerror', e => console.log('pageerror:', e.message));
  await p.goto('http://127.0.0.1:8767/page.html');
  for (const j of jobs) {
    const url = await p.evaluate(async (j) => await renderOne(j.file, j.px || 1200, j.yaw ?? 20, j.pitch ?? 28, j.inc, j.exc), j);
    fs.writeFileSync('out_' + j.name + '.png', Buffer.from(url.split(',')[1], 'base64'));
    console.log('done', j.name);
  }
  await b.close();
})();
