const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--ignore-gpu-blocklist'] });
  const p = await b.newPage();
  p.on('console', m => console.log('console:', m.text()));
  p.on('pageerror', e => console.log('pageerror:', e.message));
  await p.goto('http://127.0.0.1:8765/page.html');
  const cars = ['muscle_blue','icecream_pink','monster_orange','police_classic','fire_red'];
  const meta = {};
  for (const c of cars) {
    const r = await p.evaluate(async (u) => await renderCar(u, 2048), c + '.glb');
    fs.writeFileSync(`out_${c}_body.png`, Buffer.from(r.bodyImg.split(',')[1], 'base64'));
    for (const k of ['F','R']) fs.writeFileSync(`out_${c}_w${k}.png`, Buffer.from(r.wimg[k].split(',')[1], 'base64'));
    meta[c] = r.meta;
    console.log(c, JSON.stringify(r.meta.frame), JSON.stringify(r.meta.body));
  }
  fs.writeFileSync('meta.json', JSON.stringify(meta, null, 1));
  await b.close();
})();
