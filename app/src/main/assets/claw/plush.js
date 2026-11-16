/**
 * Плюшевые зверята для «Хваталки»: собираются из простых фигур three.js и склеиваются в один меш
 * (цвета лежат в вершинах), поэтому каждый приз — один вызов отрисовки. Внешних моделей и картинок нет.
 */
import * as THREE from './three.module.min.js';

const UNIT_SPHERE = new THREE.SphereGeometry(1, 14, 10);
const UNIT_CONE = new THREE.ConeGeometry(1, 1, 10, 1);
const UNIT_CYL = new THREE.CylinderGeometry(1, 1, 1, 10, 1);
const _q = new THREE.Quaternion(), _e = new THREE.Euler(), _s = new THREE.Vector3(), _p = new THREE.Vector3();

class Builder {
  constructor() { this.parts = []; }
  add(geo, x, y, z, sx, sy, sz, color, rot) {
    _e.set(rot ? rot[0] : 0, rot ? rot[1] : 0, rot ? rot[2] : 0);
    _q.setFromEuler(_e); _p.set(x, y, z); _s.set(sx, sy, sz);
    this.parts.push({ geo, m: new THREE.Matrix4().compose(_p, _q, _s), color: new THREE.Color(color) });
    return this;
  }
  /** эллипсоид */
  e(x, y, z, rx, ry, rz, color, rot) { return this.add(UNIT_SPHERE, x, y, z, rx, ry, rz, color, rot); }
  /** конус: r — радиус основания, h — высота, остриё вверх (+y) */
  cone(x, y, z, r, h, color, rot) { return this.add(UNIT_CONE, x, y, z, r, h, r, color, rot); }
  /** цилиндр вдоль y */
  cyl(x, y, z, r, h, color, rot) { return this.add(UNIT_CYL, x, y, z, r, h, r, color, rot); }
  /** чёрные глазки с бликом */
  eyes(y, z, dx, r) {
    for (const s of [-1, 1]) {
      this.e(s * dx, y, z, r, r * 1.15, r * 0.8, '#1b1b1f');
      this.e(s * dx + r * 0.3, y + r * 0.45, z + r * 0.5, r * 0.32, r * 0.32, r * 0.25, '#ffffff');
    }
    return this;
  }
}

/** Общая «плюшевая» основа: тело, голова, лапки, глаза. Мордой зверь смотрит в +z. */
function base(a, o) {
  const fur = o.fur, hr = o.hr || 0.4;
  const limb = o.limb || fur, foot = o.foot || limb;
  a.e(0, -0.2, 0, 0.46, 0.5, 0.4, fur);
  if (o.belly) a.e(0, -0.2, 0.2, 0.3, 0.36, 0.26, o.belly);
  a.e(0, 0.42, 0.08, hr, hr * 0.92, hr * 0.95, o.headC || fur);
  if (!o.noArms) for (const s of [-1, 1]) a.e(s * 0.47, -0.1, 0.1, 0.13, 0.25, 0.13, limb, [0, 0, s * 0.35]);
  if (!o.noFeet) for (const s of [-1, 1]) a.e(s * 0.22, -0.68, 0.2, 0.17, 0.1, 0.25, foot);
  if (!o.noEyes) {
    const dx = hr * 0.37, ey = 0.42 + hr * 0.15;
    const ez = 0.08 + Math.sqrt(Math.max(0.01, (hr * 0.97) ** 2 - dx * dx - (hr * 0.15) ** 2)) - 0.01;
    a.eyes(ey, ez, dx, 0.055);
  }
}

// ───────────────────────── виды ─────────────────────────
// rarity: 1 — обычные, 2 — редкие, 3 — очень редкие, 4 — легенда. r — радиус для физики.
// snd — файл звука (res/raw) или '' (тогда играет общий «писк»).
export const SPECIES = [
  { id: 'bunny', name: 'Зайка Прыг-Скок', emoji: '🐰', rarity: 1, r: 0.55, snd: 'animal_bunny_happy', fact: 'Зайки отлично прыгают и любят морковку.',
    build(a) {
      base(a, { fur: '#f4efe9', belly: '#ffffff', hr: 0.38 });
      for (const s of [-1, 1]) {
        a.e(s * 0.17, 1.0, -0.02, 0.09, 0.4, 0.07, '#f4efe9', [0, 0, s * 0.12]);
        a.e(s * 0.17, 1.0, 0.03, 0.05, 0.3, 0.04, '#f6a9ba', [0, 0, s * 0.12]);
      }
      a.e(0, 0.38, 0.47, 0.06, 0.045, 0.04, '#f08aa0');
      a.e(0, -0.3, -0.42, 0.14, 0.14, 0.14, '#ffffff');
    } },
  { id: 'cat', name: 'Котик Мурчик', emoji: '🐱', rarity: 1, r: 0.55, snd: 'animal_cat_happy', fact: 'Котики мурлычут, когда им хорошо.',
    build(a) {
      base(a, { fur: '#f0a055', belly: '#fde6c8' });
      for (const s of [-1, 1]) {
        a.cone(s * 0.24, 0.8, 0.04, 0.15, 0.28, '#f0a055', [0, 0, -s * 0.25]);
        a.cone(s * 0.24, 0.78, 0.08, 0.08, 0.2, '#f6a9ba', [0, 0, -s * 0.25]);
        a.cyl(s * 0.28, 0.37, 0.42, 0.008, 0.34, '#ffffff', [0, 0, Math.PI / 2]);
        a.cyl(s * 0.28, 0.32, 0.41, 0.008, 0.34, '#ffffff', [0, 0, Math.PI / 2 + s * 0.15]);
      }
      for (const x of [-0.07, 0, 0.07]) a.e(x, 0.78, 0.3, 0.025, 0.08, 0.02, '#c97a30');
      a.e(0, 0.38, 0.47, 0.05, 0.035, 0.035, '#f08aa0');
      a.cyl(0, -0.15, -0.55, 0.06, 0.7, '#f0a055', [0.9, 0, 0]);
    } },
  { id: 'dog', name: 'Щенок Бублик', emoji: '🐶', rarity: 1, r: 0.56, snd: 'animal_dog_happy', fact: 'Собачки виляют хвостом, когда рады.',
    build(a) {
      base(a, { fur: '#c99a62', belly: '#f3e0c3' });
      for (const s of [-1, 1]) a.e(s * 0.38, 0.5, 0.02, 0.11, 0.26, 0.15, '#7b4f27', [0, 0, s * 0.3]);
      a.e(0.17, 0.5, 0.38, 0.11, 0.1, 0.06, '#7b4f27');
      a.e(0, 0.33, 0.44, 0.17, 0.13, 0.14, '#f6e7cf');
      a.e(0, 0.4, 0.56, 0.07, 0.05, 0.05, '#222222');
      a.e(0, -0.05, -0.5, 0.07, 0.22, 0.07, '#c99a62', [-0.7, 0, 0]);
    } },
  { id: 'pig', name: 'Поросёнок Хрюша', emoji: '🐷', rarity: 1, r: 0.56, snd: 'animal_pig_1', fact: 'Поросята очень умные и любят купаться.',
    build(a) {
      base(a, { fur: '#f8aab9', belly: '#fbc3cd', hr: 0.42 });
      a.cyl(0, 0.36, 0.49, 0.15, 0.1, '#f48fa6', [Math.PI / 2, 0, 0]);
      for (const s of [-1, 1]) {
        a.e(s * 0.05, 0.36, 0.545, 0.025, 0.035, 0.015, '#b85a70');
        a.cone(s * 0.27, 0.78, 0.0, 0.14, 0.2, '#f48fa6', [0.3, 0, -s * 0.5]);
      }
      a.e(0, -0.25, -0.42, 0.06, 0.06, 0.06, '#f48fa6');
    } },
  { id: 'duck', name: 'Утёнок Кряк', emoji: '🦆', rarity: 1, r: 0.52, snd: 'animal_duck_1', fact: 'Утята отлично плавают, а перья у них не промокают.',
    build(a) {
      base(a, { fur: '#ffd83d', belly: '#ffe98a', foot: '#ff9a2e', noArms: true });
      for (const s of [-1, 1]) a.e(s * 0.45, -0.15, -0.02, 0.1, 0.28, 0.2, '#f2c21e', [0, 0, s * 0.3]);
      a.e(0, 0.34, 0.52, 0.17, 0.06, 0.15, '#ff9a2e');
      a.e(0, 0.3, 0.5, 0.15, 0.04, 0.12, '#f08a1e');
      a.cone(0, 0.82, 0.05, 0.05, 0.18, '#ffd83d', [0.3, 0, 0]);
      a.cone(0, -0.2, -0.5, 0.14, 0.25, '#ffd83d', [-1.2, 0, 0]);
    } },
  { id: 'frog', name: 'Лягушонок Квак', emoji: '🐸', rarity: 1, r: 0.52, snd: 'animal_frog_1', fact: 'Лягушата умеют высоко прыгать и громко квакать.',
    build(a) {
      base(a, { fur: '#6dc24b', belly: '#dff5b0', hr: 0.42, noEyes: true });
      for (const s of [-1, 1]) {
        a.e(s * 0.22, 0.78, 0.15, 0.14, 0.14, 0.14, '#6dc24b');
        a.e(s * 0.22, 0.8, 0.26, 0.1, 0.1, 0.07, '#ffffff');
        a.e(s * 0.22, 0.8, 0.31, 0.055, 0.065, 0.04, '#1b1b1f');
        a.e(s * 0.27, 0.3, 0.4, 0.06, 0.04, 0.03, '#f6a9a0');
      }
      a.e(0, 0.28, 0.46, 0.22, 0.025, 0.05, '#2f6b22');
    } },
  { id: 'sheep', name: 'Барашек Облачко', emoji: '🐑', rarity: 2, r: 0.6, snd: 'animal_sheep_1', fact: 'У барашка тёплая кудрявая шерсть.',
    build(a) {
      a.e(0, -0.15, 0, 0.55, 0.55, 0.5, '#f7f5ef');
      for (const [x, y, z] of [[-0.4, 0.1, 0.1], [0.4, 0.1, 0.1], [-0.35, -0.45, 0.15], [0.35, -0.45, 0.15], [0, 0.25, -0.3], [-0.3, -0.1, -0.4], [0.3, -0.1, -0.4], [0, 0.45, 0.1]]) a.e(x, y, z, 0.28, 0.28, 0.28, '#fbfaf5');
      a.e(0, 0.38, 0.3, 0.27, 0.3, 0.27, '#4a4650');
      a.e(0, 0.66, 0.2, 0.22, 0.15, 0.2, '#fbfaf5');
      for (const s of [-1, 1]) {
        a.e(s * 0.3, 0.42, 0.28, 0.17, 0.07, 0.1, '#4a4650', [0, 0, -s * 0.3]);
        a.e(s * 0.11, 0.42, 0.53, 0.055, 0.06, 0.04, '#ffffff');
        a.e(s * 0.11, 0.42, 0.565, 0.03, 0.035, 0.02, '#111111');
        a.e(s * 0.22, -0.68, 0.15, 0.1, 0.14, 0.1, '#4a4650');
        a.e(s * 0.22, -0.68, -0.2, 0.1, 0.14, 0.1, '#4a4650');
      }
      a.e(0, 0.3, 0.55, 0.05, 0.035, 0.035, '#8a8493');
    } },
  { id: 'chick', name: 'Цыплёнок Пип', emoji: '🐥', rarity: 2, r: 0.5, snd: '', fact: 'Цыплята вылупляются из яичек.',
    build(a) {
      a.e(0, 0, 0, 0.5, 0.52, 0.46, '#ffe04a');
      a.e(0, -0.1, 0.25, 0.3, 0.34, 0.2, '#fff1a0');
      a.eyes(0.15, 0.42, 0.17, 0.06);
      a.cone(0, 0.05, 0.5, 0.09, 0.18, '#ff9a2e', [Math.PI / 2, 0, 0]);
      for (const s of [-1, 1]) {
        a.e(s * 0.5, -0.05, 0, 0.07, 0.22, 0.17, '#f3c92a', [0, 0, s * 0.35]);
        a.e(s * 0.15, -0.55, 0.2, 0.1, 0.04, 0.14, '#ff9a2e');
        a.e(s * 0.3, 0.05, 0.38, 0.07, 0.045, 0.03, '#ff9fa0');
      }
      for (const x of [-0.08, 0, 0.08]) a.cone(x, 0.52, 0.05, 0.04, 0.14, '#ffe04a', [0.2, 0, -x * 3]);
    } },
  { id: 'cow', name: 'Коровка Бурёнка', emoji: '🐮', rarity: 2, r: 0.62, snd: 'animal_cow_1', fact: 'Коровки дают молоко.',
    build(a) {
      base(a, { fur: '#fafafa', belly: '#ffffff', hr: 0.42, foot: '#3a3a3f' });
      for (const [x, y, z, rx, ry, rz] of [[-0.25, -0.05, 0.32, 0.16, 0.14, 0.07], [0.3, -0.4, 0.27, 0.15, 0.13, 0.07], [0, -0.1, -0.38, 0.2, 0.16, 0.06], [0.2, 0.62, 0.22, 0.12, 0.1, 0.08]]) a.e(x, y, z, rx, ry, rz, '#2e2e33');
      a.e(0, 0.28, 0.42, 0.2, 0.14, 0.12, '#f6aab4');
      for (const s of [-1, 1]) {
        a.e(s * 0.07, 0.3, 0.52, 0.02, 0.03, 0.015, '#b8606e');
        a.e(s * 0.45, 0.55, 0.0, 0.15, 0.07, 0.1, '#fafafa', [0, 0, -s * 0.3]);
        a.cone(s * 0.22, 0.82, 0.05, 0.045, 0.16, '#efe3c0', [0, 0, -s * 0.35]);
      }
      a.cyl(0, -0.2, -0.45, 0.025, 0.5, '#fafafa', [0.8, 0, 0]);
    } },
  { id: 'bear', name: 'Мишка Топтыжка', emoji: '🐻', rarity: 2, r: 0.58, snd: '', fact: 'Мишки любят мёд и хорошо плавают.',
    build(a) {
      base(a, { fur: '#a06b3f', belly: '#e9cfa4', hr: 0.42 });
      for (const s of [-1, 1]) {
        a.e(s * 0.3, 0.76, 0.02, 0.14, 0.14, 0.08, '#a06b3f');
        a.e(s * 0.3, 0.76, 0.07, 0.08, 0.08, 0.04, '#e9cfa4');
      }
      a.e(0, 0.33, 0.42, 0.18, 0.14, 0.14, '#e9cfa4');
      a.e(0, 0.4, 0.55, 0.07, 0.05, 0.05, '#2a1c12');
      a.e(0, -0.35, -0.4, 0.1, 0.1, 0.1, '#a06b3f');
    } },
  { id: 'fox', name: 'Лисичка Рыжик', emoji: '🦊', rarity: 2, r: 0.56, snd: '', fact: 'Пушистый хвост греет лисичку зимой.',
    build(a) {
      base(a, { fur: '#ea7a2c', belly: '#fff4e6', hr: 0.38, foot: '#3a2a22' });
      for (const s of [-1, 1]) {
        a.e(s * 0.18, 0.28, 0.32, 0.14, 0.09, 0.1, '#fff4e6');
        a.cone(s * 0.24, 0.82, 0, 0.15, 0.34, '#ea7a2c', [0, 0, -s * 0.2]);
        a.cone(s * 0.26, 0.95, 0, 0.09, 0.16, '#3a2a22', [0, 0, -s * 0.2]);
      }
      a.e(0, 0.36, 0.5, 0.1, 0.09, 0.12, '#fff4e6');
      a.e(0, 0.38, 0.62, 0.045, 0.04, 0.04, '#222222');
      a.e(0, -0.25, -0.62, 0.17, 0.17, 0.34, '#ea7a2c', [0.3, 0, 0]);
      a.e(0, -0.18, -0.92, 0.13, 0.13, 0.15, '#fff4e6');
    } },
  { id: 'monkey', name: 'Обезьянка Бананчик', emoji: '🐵', rarity: 2, r: 0.56, snd: '', fact: 'Обезьянки ловко лазают по деревьям.',
    build(a) {
      base(a, { fur: '#8a5a3a', belly: '#e8c9a0', noEyes: true });
      a.e(0, 0.38, 0.37, 0.26, 0.22, 0.14, '#f1d4ac');
      a.eyes(0.5, 0.49, 0.12, 0.05);
      for (const s of [-1, 1]) {
        a.e(s * 0.4, 0.45, 0.02, 0.12, 0.12, 0.07, '#8a5a3a');
        a.e(s * 0.4, 0.45, 0.06, 0.075, 0.075, 0.04, '#f1d4ac');
      }
      a.e(0, 0.3, 0.5, 0.1, 0.07, 0.06, '#e6bd90');
      a.cyl(0, -0.3, -0.5, 0.05, 0.7, '#8a5a3a', [1.0, 0, 0]);
    } },
  { id: 'penguin', name: 'Пингвинёнок Лёдик', emoji: '🐧', rarity: 2, r: 0.56, snd: '', fact: 'Пингвины не летают, зато отлично плавают.',
    build(a) {
      a.e(0, -0.1, 0, 0.5, 0.62, 0.44, '#2b2f3a');
      a.e(0, -0.08, 0.2, 0.34, 0.46, 0.3, '#f7f7f7');
      a.e(0, 0.52, 0.05, 0.36, 0.33, 0.34, '#2b2f3a');
      a.e(0, 0.48, 0.25, 0.25, 0.2, 0.1, '#f7f7f7');
      a.eyes(0.56, 0.36, 0.12, 0.05);
      a.cone(0, 0.46, 0.46, 0.08, 0.18, '#ff9a2e', [Math.PI / 2, 0, 0]);
      for (const s of [-1, 1]) {
        a.e(s * 0.52, -0.1, 0.02, 0.08, 0.34, 0.17, '#232733', [0, 0, s * 0.3]);
        a.e(s * 0.2, -0.72, 0.25, 0.17, 0.05, 0.22, '#ff9a2e');
      }
    } },
  { id: 'owl', name: 'Сова Умница', emoji: '🦉', rarity: 3, r: 0.58, snd: '', fact: 'Совы не спят по ночам и хорошо видят в темноте.',
    build(a) {
      a.e(0, -0.05, 0, 0.5, 0.6, 0.44, '#8b6b4a');
      a.e(0, -0.05, 0.22, 0.34, 0.44, 0.26, '#d9c09a');
      a.e(0, 0.5, 0.05, 0.42, 0.36, 0.36, '#8b6b4a');
      for (const s of [-1, 1]) {
        a.e(s * 0.2, 0.52, 0.35, 0.17, 0.17, 0.06, '#f3e6c8');
        a.e(s * 0.2, 0.52, 0.4, 0.1, 0.1, 0.04, '#f2b632');
        a.e(s * 0.2, 0.52, 0.43, 0.055, 0.055, 0.03, '#111111');
        a.cone(s * 0.28, 0.86, 0, 0.07, 0.2, '#6e5338', [0, 0, -s * 0.3]);
        a.e(s * 0.52, -0.05, -0.02, 0.08, 0.4, 0.2, '#6e5338', [0, 0, s * 0.15]);
        a.e(s * 0.18, -0.68, 0.25, 0.12, 0.05, 0.14, '#e8902a');
      }
      a.cone(0, 0.42, 0.43, 0.06, 0.16, '#e8902a', [Math.PI / 2 + 0.3, 0, 0]);
    } },
  { id: 'panda', name: 'Панда Бамбук', emoji: '🐼', rarity: 3, r: 0.64, snd: '', fact: 'Панды любят есть бамбук.',
    build(a) {
      base(a, { fur: '#f8f8f8', limb: '#26262b', foot: '#26262b', hr: 0.42, noEyes: true });
      for (const s of [-1, 1]) {
        a.e(s * 0.3, 0.78, 0.02, 0.13, 0.13, 0.08, '#26262b');
        a.e(s * 0.15, 0.46, 0.4, 0.1, 0.13, 0.06, '#26262b', [0, 0, s * 0.35]);
        a.e(s * 0.15, 0.47, 0.44, 0.04, 0.04, 0.03, '#ffffff');
        a.e(s * 0.15, 0.47, 0.46, 0.022, 0.025, 0.02, '#000000');
      }
      a.e(0, 0.36, 0.45, 0.12, 0.09, 0.07, '#ffffff');
      a.e(0, 0.38, 0.5, 0.06, 0.045, 0.04, '#26262b');
    } },
  { id: 'lion', name: 'Львёнок Гривик', emoji: '🦁', rarity: 3, r: 0.66, snd: '', fact: 'У льва пышная грива.',
    build(a) {
      a.e(0, 0.42, 0.0, 0.55, 0.55, 0.34, '#b5651f');
      for (let i = 0; i < 10; i++) {
        const t = (i / 10) * Math.PI * 2;
        a.e(Math.cos(t) * 0.48, 0.42 + Math.sin(t) * 0.48, 0.0, 0.15, 0.15, 0.2, '#a65a1a');
      }
      base(a, { fur: '#e9b049', belly: '#f6dca0', hr: 0.36 });
      for (const s of [-1, 1]) a.e(s * 0.3, 0.74, 0.1, 0.1, 0.1, 0.06, '#e9b049');
      a.e(0, 0.33, 0.4, 0.17, 0.12, 0.12, '#f6dca0');
      a.e(0, 0.4, 0.5, 0.06, 0.04, 0.04, '#7a3b25');
      a.cyl(0, -0.25, -0.5, 0.04, 0.6, '#e9b049', [0.9, 0, 0]);
      a.e(0, -0.05, -0.78, 0.09, 0.09, 0.11, '#a65a1a');
    } },
  { id: 'elephant', name: 'Слонёнок Хоботок', emoji: '🐘', rarity: 3, r: 0.68, snd: '', fact: 'Слоны умеют пить воду хоботом.',
    build(a) {
      base(a, { fur: '#9fb6cb', belly: '#b9cde0', hr: 0.42 });
      for (const s of [-1, 1]) {
        a.e(s * 0.5, 0.5, -0.02, 0.07, 0.3, 0.27, '#9fb6cb', [0, 0, s * 0.25]);
        a.e(s * 0.52, 0.5, 0.02, 0.04, 0.22, 0.2, '#f0b9c3', [0, 0, s * 0.25]);
      }
      a.e(0, 0.2, 0.5, 0.1, 0.15, 0.12, '#9fb6cb', [0.4, 0, 0]);
      a.e(0, 0.02, 0.58, 0.085, 0.16, 0.1, '#9fb6cb', [0.2, 0, 0]);
      a.e(0, -0.14, 0.62, 0.07, 0.1, 0.08, '#8ea7bd');
      a.e(0, -0.3, -0.42, 0.06, 0.1, 0.06, '#9fb6cb');
    } },
  { id: 'unicorn', name: 'Единорожка Радуга', emoji: '🦄', rarity: 4, r: 0.66, snd: '', fact: 'Единорог — волшебный зверь из сказок.',
    build(a) {
      base(a, { fur: '#ffffff', belly: '#fdf3ff', foot: '#ffd04a' });
      a.cone(0, 0.98, 0.2, 0.07, 0.42, '#ffd04a', [0.35, 0, 0]);
      const mane = ['#ff6b9d', '#ffb84d', '#4dc3ff'];
      for (const s of [-1, 1]) {
        for (let k = 0; k < 3; k++) a.e(s * 0.3, 0.7 - k * 0.18, -0.12, 0.14, 0.16, 0.12, mane[k]);
        a.cone(s * 0.2, 0.8, -0.02, 0.08, 0.2, '#ffffff', [0, 0, -s * 0.2]);
      }
      a.e(0, 0.8, 0.12, 0.18, 0.1, 0.14, '#ff6b9d');
      a.e(0, 0.32, 0.43, 0.14, 0.1, 0.09, '#ffd9e6');
      a.e(0, -0.3, -0.45, 0.1, 0.2, 0.1, '#ff6b9d', [0.7, 0, 0]);
      a.e(0, -0.5, -0.62, 0.09, 0.18, 0.09, '#4dc3ff', [0.7, 0, 0]);
    } },
];

export const SPECIES_BY_ID = Object.fromEntries(SPECIES.map((s) => [s.id, s]));
/** Очки за зверя и «сила хвата» зависят от редкости: чем реже, тем он скользче. */
export const RARITY = {
  1: { value: 20, grip: 0.82, label: 'Обычный' },
  2: { value: 40, grip: 0.68, label: 'Редкий' },
  3: { value: 70, grip: 0.52, label: 'Очень редкий' },
  4: { value: 150, grip: 0.4, label: 'Легендарный' },
};

// ───────────────────────── сборка меша ─────────────────────────
function mergeParts(parts) {
  let vCount = 0, iCount = 0;
  const gs = parts.map((p) => {
    const g = p.geo.clone();
    g.applyMatrix4(p.m);
    vCount += g.attributes.position.count;
    iCount += g.index.count;
    return g;
  });
  const pos = new Float32Array(vCount * 3), nor = new Float32Array(vCount * 3), col = new Float32Array(vCount * 3);
  const idx = new Uint16Array(iCount);
  let vo = 0, io = 0;
  gs.forEach((g, i) => {
    const c = parts[i].color, pa = g.attributes.position, na = g.attributes.normal;
    pos.set(pa.array, vo * 3);
    nor.set(na.array, vo * 3);
    for (let k = 0; k < pa.count; k++) { col[(vo + k) * 3] = c.r; col[(vo + k) * 3 + 1] = c.g; col[(vo + k) * 3 + 2] = c.b; }
    const ia = g.index.array;
    for (let k = 0; k < ia.length; k++) idx[io + k] = ia[k] + vo;
    vo += pa.count; io += ia.length;
    g.dispose();
  });
  const out = new THREE.BufferGeometry();
  out.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  out.setAttribute('normal', new THREE.BufferAttribute(nor, 3));
  out.setAttribute('color', new THREE.BufferAttribute(col, 3));
  out.setIndex(new THREE.BufferAttribute(idx, 1));
  return out;
}

const geoCache = {};
/** Геометрия зверя: по центру, высота ≈ 2r (чуть больше), чтобы сфера физики с ним совпадала. */
export function plushGeometry(id) {
  if (geoCache[id]) return geoCache[id];
  const sp = SPECIES_BY_ID[id];
  const b = new Builder();
  sp.build(b);
  const g = mergeParts(b.parts);
  g.computeBoundingBox();
  const bb = g.boundingBox;
  const h = bb.max.y - bb.min.y, w = Math.max(bb.max.x - bb.min.x, bb.max.z - bb.min.z);
  const s = Math.min((2 * sp.r * 1.12) / h, (2 * sp.r * 1.25) / w);
  g.translate(-(bb.max.x + bb.min.x) / 2, -(bb.max.y + bb.min.y) / 2, -(bb.max.z + bb.min.z) / 2);
  g.scale(s, s, s);
  g.computeBoundingSphere();
  geoCache[id] = g;
  return g;
}

let sharedMat = null;
export function plushMaterial() {
  if (!sharedMat) sharedMat = new THREE.MeshStandardMaterial({ vertexColors: true, roughness: 0.85, metalness: 0.0, envMapIntensity: 0.7 });
  return sharedMat;
}

export function makePlushMesh(id) {
  const m = new THREE.Mesh(plushGeometry(id), plushMaterial());
  m.castShadow = true; m.receiveShadow = true;
  return m;
}

/** Случайный вид для наполнения автомата: обычные чаще, редкие реже, легенда — отдельно. */
export function randomSpecies(rand = Math.random) {
  const w = { 1: 5, 2: 3, 3: 1.2 };
  const list = SPECIES.filter((s) => s.rarity < 4);
  let sum = 0;
  for (const s of list) sum += w[s.rarity];
  let x = rand() * sum;
  for (const s of list) { x -= w[s.rarity]; if (x <= 0) return s; }
  return list[0];
}
