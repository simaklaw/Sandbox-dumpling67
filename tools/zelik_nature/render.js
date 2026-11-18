const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--ignore-gpu-blocklist'] });
  const p = await b.newPage();
  p.on('pageerror', e => console.log('pageerror:', e.message));
  await p.goto('http://127.0.0.1:8766/page.html');
  const files = fs.readdirSync('.').filter(f => f.endsWith('.glb'));
  for (const f of files) {
    const url = await p.evaluate(async (u) => await renderOne(u, 1400, 22, 14), f);
    fs.writeFileSync('out_' + f.replace('.glb', '.png'), Buffer.from(url.split(',')[1], 'base64'));
  }
  console.log('ok', files.length);
  await b.close();
})();
