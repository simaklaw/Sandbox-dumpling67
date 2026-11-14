"""Генерирует app/src/main/assets/claw/claw_env.jpg: собственный равнопрямоугольный фон для автомата «Хваталка».
Мягкий светлый градиент «комнаты» и несколько световых панелей для бликов на стекле. Лицензия: авторская, без сторонних источников."""
import numpy as np
from PIL import Image, ImageFilter

W, H = 1024, 512
y = np.linspace(0, 1, H)[:, None]
x = np.linspace(0, 1, W)[None, :]
# потолок светлее, пол теплее и темнее
top = np.array([232, 240, 255], float)
mid = np.array([255, 244, 226], float)
bot = np.array([196, 168, 140], float)
img = np.zeros((H, W, 3))
for c in range(3):
    img[:, :, c] = np.where(y < 0.5, top[c] + (mid[c] - top[c]) * (y / 0.5), mid[c] + (bot[c] - mid[c]) * ((y - 0.5) / 0.5))
# световые панели (окна/лампы)
for cx, cy, w, h, k in [(0.12, 0.28, 0.07, 0.16, 1.0), (0.38, 0.22, 0.10, 0.12, 0.9), (0.63, 0.30, 0.06, 0.18, 1.0), (0.86, 0.24, 0.09, 0.13, 0.85)]:
    m = ((np.abs(x - cx) < w / 2) & (np.abs(y - cy) < h / 2)).astype(float)
    for c in range(3):
        img[:, :, c] = img[:, :, c] * (1 - m * k) + 255 * m * k
# тёплые пятна на «полу»
rng = np.random.default_rng(67)
for _ in range(14):
    cx, cy = rng.random(), 0.62 + rng.random() * 0.3
    r = 0.03 + rng.random() * 0.05
    m = np.exp(-(((x - cx) * 2) ** 2 + (y - cy) ** 2) / (2 * r * r))
    tint = rng.choice([[255, 120, 120], [120, 200, 255], [255, 220, 100], [150, 230, 150]])
    for c in range(3):
        img[:, :, c] = img[:, :, c] * (1 - 0.35 * m) + tint[c] * 0.35 * m
out = Image.fromarray(np.clip(img, 0, 255).astype('uint8')).filter(ImageFilter.GaussianBlur(3))
out.save('app/src/main/assets/claw/claw_env.jpg', quality=85)
