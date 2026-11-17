"""Собирает спрайты машинок для «Автомойки игрушек» из рендеров GLB (render.js).

Вход: out_<car>_body.png, out_<car>_wF.png, out_<car>_wR.png, meta.json (в каталоге IN).
Выход: app/src/main/res/drawable-nodpi/cw3_<id>_{body,wf,wr}.webp и печать таблицы для CarWashRoomScreen.kt
Модели: Popyca Studio «5 Free Toy Cars», CC0.
"""
import json, os, sys
from PIL import Image, ImageFilter

IN = sys.argv[1]
OUT = sys.argv[2]
meta = json.load(open(os.path.join(IN, 'meta.json')))

# id, файл, лицо (u, v, размер) в единицах игрушки, корона (z, y) в метрах, «тяжесть»
CARS = [
    ('muscle',  'muscle_blue',    (-0.05, 0.16, 0.45), (-0.35, 1.30), 1.0),
    ('icecream','icecream_pink',  (0.45, 0.22, 0.42), (0.60, 1.60), 0.85),
    ('monster', 'monster_orange', (0.00, -0.08, 0.38), (-0.45, 2.40), 0.8),
    ('police',  'police_classic', (0.05, 0.20, 0.45), (-0.30, 1.62), 1.0),
    ('fire',    'fire_red',       (-0.45, 0.07, 0.55), (0.95, 1.90), 0.8),
]
BODY_W = 1024
OUTLINE = 5
INK = (51, 65, 85, 255)

def outline(img):
    a = img.split()[3]
    big = a.filter(ImageFilter.MaxFilter(OUTLINE * 2 + 1))
    ring = Image.new('RGBA', img.size, INK)
    ring.putalpha(big)
    ring.alpha_composite(img)
    return ring

rows = []
for cid, fname, face, crown, heavy in CARS:
    m = meta[fname]
    b = Image.open(os.path.join(IN, f'out_{fname}_body.png')).convert('RGBA')
    h = round(b.height * BODY_W / b.width)
    b = b.resize((BODY_W, h), Image.LANCZOS)
    b = outline(b)
    b.save(os.path.join(OUT, f'cw3_{cid}_body.webp'), 'WEBP', quality=90, method=6)
    for key in 'FR':
        w = Image.open(os.path.join(IN, f'out_{fname}_w{key}.png')).convert('RGBA').resize((192, 192), Image.LANCZOS)
        w.save(os.path.join(OUT, f'cw3_{cid}_w{key.lower()}.webp'), 'WEBP', quality=90, method=6)
    bd, fr = m['body'], m['frame']
    L = bd['zmax'] - bd['zmin']
    k = 2.0 / L                       # метры -> единицы игрушки (длина = 2)
    gy = 0.75                         # линия земли в координатах игрушки
    U = lambda z: z * k
    V = lambda y: gy - y * k
    left = U(fr['zc'] - fr['Lz'] / 2)
    top = V(fr['y1'])
    wid = fr['Lz'] * k
    hei = (fr['y1'] - fr['y0']) * k
    # рамка спрайта на экране считается от нижней границы y0 (ниже земли на pad)
    hei = (fr['y1'] - fr['y0']) * k
    wf, wr = m['wheels']['wheel_FR'], m['wheels']['wheel_RR']
    def wh(w):
        r = w['r'] * 1.04 * k
        return (U(w['z']), V(w['y']), r)
    rows.append(dict(id=cid, rect=(left, top, wid, hei), wf=wh(wf), wr=wh(wr),
                     face=face, crown=(U(crown[0]), V(crown[1])), heavy=heavy,
                     contactF=U(wf['z']), contactR=U(wr['z']), w=BODY_W, h=h))

f = lambda x: f'{x:.3f}f'
print('// ---- сгенерировано tools/carwash_cars/build.py ----')
for r in rows:
    print(f'''    CwCar(R.drawable.cw3_{r['id']}_body, R.drawable.cw3_{r['id']}_wf, R.drawable.cw3_{r['id']}_wr,
        {f(r['rect'][0])}, {f(r['rect'][1])}, {f(r['rect'][2])}, {f(r['rect'][3])},
        {f(r['wf'][0])}, {f(r['wf'][1])}, {f(r['wf'][2])}, {f(r['wr'][0])}, {f(r['wr'][1])}, {f(r['wr'][2])},
        {f(r['face'][0])}, {f(r['face'][1])}, {f(r['face'][2])}, {f(r['crown'][0])}, {f(r['crown'][1])}, {f(r['heavy'])}),''')
json.dump(rows, open(os.path.join(IN, 'rows.json'), 'w'), indent=1)
