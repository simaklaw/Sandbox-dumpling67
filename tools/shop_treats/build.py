"""Обрезает рендеры угощений (render.js + jobs_final.json), добавляет коричневую обводку и кладёт в res/drawable-nodpi как sw_<имя>.webp.
Модели: пятёрка «ice cream / donuts / pizza / sushi / chocolate» (предоставлены владельцем проекта). Исходные GLB в репозиторий не кладём."""
import os, sys
from PIL import Image, ImageFilter
IN, OUT = sys.argv[1], sys.argv[2]
INK = (142, 86, 40, 255)
for name in ('ice', 'donut', 'pizza', 'sushi', 'choco'):
    im = Image.open(os.path.join(IN, f'out_{name}.png')).convert('RGBA')
    im = im.crop(im.getchannel('A').point(lambda a: 255 if a > 8 else 0).getbbox())
    k = 256 / max(im.size)
    im = im.resize((max(1, round(im.width * k)), max(1, round(im.height * k))), Image.LANCZOS)
    pad = 6
    canvas = Image.new('RGBA', (im.width + pad * 2, im.height + pad * 2), (0, 0, 0, 0))
    canvas.alpha_composite(im, (pad, pad))
    a = canvas.getchannel('A')
    ring = Image.new('RGBA', canvas.size, INK)
    ring.putalpha(a.filter(ImageFilter.MaxFilter(5)))   # обводка ≈2 px
    ring.alpha_composite(canvas)
    ring.save(os.path.join(OUT, f'sw_{name}.webp'), 'WEBP', quality=90, method=6)
    print(name, ring.size)
