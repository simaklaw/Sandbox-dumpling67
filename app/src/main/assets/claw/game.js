/**
 * Хваталка — Android-версия игры Claw Game (simaklaw/Claw-game, Apache-2.0).
 * Сервер и socket.io убраны (одиночная офлайн-игра), сцена собрана на чистом three.js,
 * физика призов — собственная (сферы + капсулы когтя), управление — джойстик и кнопка.
 */
import * as THREE from './three.module.min.js';

const $ = (id) => document.getElementById(id);
const bridge = (name, value) => { try { if (window.Android) window.Android.onEvent(name, String(value === undefined ? '' : value)); } catch (e) {} };

// ───────────────────────── правила (как в оригинале) ─────────────────────────
const COLOR_VALUES = { '#FBBC04': 50, '#EA4335': 40, '#34A853': 30, '#E37400': 20, '#9AA0A6': 10 };
const TYPE_MULT = { dodecahedron: 3, sphere: 2, box: 1 };
const COLORS = Object.keys(COLOR_VALUES);
const TYPES = Object.keys(TYPE_MULT);
const GAME_SECONDS = 60;
const CHUTE_X = -3.5, CHUTE_Z = 3.5;

// ───────────────────────── рендерер, сцена ─────────────────────────
const canvas = $('scene');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.75));
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
renderer.shadowMap.autoUpdate = false;
renderer.toneMapping = THREE.ACESFilmicToneMapping;

const scene = new THREE.Scene();
scene.background = new THREE.Color(0x15152a);
const camera = new THREE.PerspectiveCamera(45, 1, 0.1, 400);

scene.add(new THREE.AmbientLight(0xffffff, 2.5));
const lamp = new THREE.PointLight(0xffffff, 4.0, 0, 2);
lamp.position.set(0, 9, 0);
lamp.castShadow = true;
lamp.shadow.mapSize.set(1024, 1024);
lamp.shadow.bias = -0.0001;
scene.add(lamp);

function fitCamera() {
  const w = window.innerWidth, h = window.innerHeight;
  renderer.setSize(w, h, false);
  camera.aspect = w / h;
  // как в оригинале при игре: спереди, z = 26; на узком экране отодвигаем, чтобы влез весь автомат
  const tanHalf = Math.tan(THREE.MathUtils.degToRad(camera.fov / 2));
  const z = Math.max(26, 6.6 / (tanHalf * camera.aspect));
  const portrait = camera.aspect < 0.9;
  camera.position.set(0, portrait ? 6.5 : 8, z);
  camera.lookAt(0, portrait ? 3.6 : 4, 0);
  camera.updateProjectionMatrix();
}
window.addEventListener('resize', fitCamera);
fitCamera();

new THREE.TextureLoader().load('google-office.jpg', (tex) => {
  tex.mapping = THREE.EquirectangularReflectionMapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  scene.background = tex;
  scene.environment = tex;
  scene.backgroundBlurriness = 0.02;
  scene.environmentIntensity = 1.5;
  scene.backgroundRotation.set(0, Math.PI * 0.725, 0);
  scene.environmentRotation.set(0, Math.PI * 1.2, 0);
  $('loading').style.display = 'none';
}, undefined, () => { $('loading').style.display = 'none'; });
setTimeout(() => { $('loading').style.display = 'none'; }, 6000);

// ───────────────────────── автомат ─────────────────────────
const std = (color, metalness, roughness) => new THREE.MeshStandardMaterial({ color, metalness, roughness });
function box(parent, p, s, mat, shadow = true) {
  const m = new THREE.Mesh(new THREE.BoxGeometry(s[0], s[1], s[2]), mat);
  m.position.set(p[0], p[1], p[2]);
  m.castShadow = shadow; m.receiveShadow = shadow;
  parent.add(m);
  return m;
}

const machine = new THREE.Group();
scene.add(machine);
{
  const blue = std('#4285F4', 0.2, 0.8);
  box(machine, [-5.125, -10, 0], [0.25, 20, 10.5], blue);
  box(machine, [5.125, -10, 0], [0.25, 20, 10.5], blue);
  box(machine, [0, -10, -5.125], [10.5, 20, 0.25], blue);
  box(machine, [0, -10, 5.125], [10.5, 20, 0.25], blue);
  box(machine, [0, -19.875, 0], [10.5, 0.25, 10.5], blue);
  const floorMat = std('#e0e0e0', 0.2, 0.8);
  box(machine, [1.5, 0, 0], [7, 0.5, 10], floorMat);
  box(machine, [-3.5, 0, -1.5], [3, 0.5, 7], floorMat);
  box(machine, [0, 10.25, 0], [10.5, 0.5, 10.5], std('#4285F4', 0.4, 0.6));
  const pillar = std('#4285F4', 0.5, 0.5);
  for (const x of [-5.125, 5.125]) for (const z of [-5.125, 5.125]) box(machine, [x, 5, z], [0.25, 10, 0.25], pillar);
  const glass = new THREE.MeshPhysicalMaterial({ color: '#ffffff', transparent: true, opacity: 0.22, roughness: 0, metalness: 0, clearcoat: 1, side: THREE.DoubleSide, depthWrite: false });
  for (const [p, s] of [[[-5.25, 5, 0], [0.1, 10, 10]], [[5.25, 5, 0], [0.1, 10, 10]], [[0, 5, -5.25], [10, 10, 0.1]], [[0, 5, 5.25], [10, 10, 0.1]]]) {
    const g = box(machine, p, s, glass, false);
    g.renderOrder = 5;
  }
  const chute = std('#9AA0A6', 0.2, 0.8);
  box(machine, [-1.9, 1.75, 3.5], [0.2, 3, 3], chute);
  box(machine, [-3.5, 1.75, 1.9], [3, 3, 0.2], chute);
}

// ───────────────────────── андроид (Bugdroid) ─────────────────────────
function makeBugdroid() {
  const g = new THREE.Group();
  const mat = new THREE.MeshStandardMaterial({ color: '#34A853', roughness: 0.4, metalness: 0.1 });
  const white = new THREE.MeshStandardMaterial({ color: '#FFFFFF', roughness: 0.2 });
  const add = (geo, pos, mat2, rot) => {
    const m = new THREE.Mesh(geo, mat2 || mat);
    m.position.set(pos[0], pos[1], pos[2]);
    if (rot) m.rotation.set(rot[0], rot[1], rot[2]);
    m.castShadow = true; m.receiveShadow = true;
    g.add(m);
  };
  add(new THREE.CylinderGeometry(1, 1, 1.8, 24), [0, 2.4, 0]);
  add(new THREE.SphereGeometry(1, 24, 12, 0, Math.PI * 2, 0, Math.PI / 2), [0, 3.3, 0]);
  add(new THREE.SphereGeometry(0.1, 12, 12), [-0.4, 3.7, 0.9], white);
  add(new THREE.SphereGeometry(0.1, 12, 12), [0.4, 3.7, 0.9], white);
  add(new THREE.CylinderGeometry(0.04, 0.04, 0.6, 8), [-0.4, 4.3, 0], mat, [0, 0, Math.PI / 6]);
  add(new THREE.CylinderGeometry(0.04, 0.04, 0.6, 8), [0.4, 4.3, 0], mat, [0, 0, -Math.PI / 6]);
  add(new THREE.CapsuleGeometry(0.25, 1.3, 4, 12), [-1.4, 2.4, 0]);
  add(new THREE.CapsuleGeometry(0.25, 1.3, 4, 12), [1.4, 2.4, 0]);
  add(new THREE.CapsuleGeometry(0.25, 1, 4, 12), [-0.4, 0.75, 0]);
  add(new THREE.CapsuleGeometry(0.25, 1, 4, 12), [0.4, 0.75, 0]);
  return g;
}
{
  const top = makeBugdroid();
  top.position.set(0, 10.5, 0);
  top.rotation.y = Math.PI / 4;
  top.scale.setScalar(0.6);
  scene.add(top);
}

// ───────────────────────── коготь ─────────────────────────
const rails = std('#333', 0.8, 0.2);
const gantry = new THREE.Group();
scene.add(gantry);
box(gantry, [-4.8, 10, 0], [0.2, 0.2, 10], rails);
box(gantry, [4.8, 10, 0], [0.2, 0.2, 10], rails);
const crossbar = box(gantry, [0, 10, 0], [9.8, 0.25, 0.25], std('#444', 0.8, 0.2));
const carriage = new THREE.Group();
gantry.add(carriage);
box(carriage, [0, -0.2, 0], [1.2, 0.6, 1.2], std('#222', 0.6, 0.4));
{
  const spool = new THREE.Mesh(new THREE.CylinderGeometry(0.2, 0.2, 0.8, 16), std('#EA4335', 0.3, 0.7));
  spool.position.set(0, -0.2, 0.65); spool.rotation.z = Math.PI / 2; spool.castShadow = true;
  carriage.add(spool);
}
const cable = new THREE.Mesh(new THREE.CylinderGeometry(0.02, 0.02, 1, 8), std('#111', 0.9, 0.1));
cable.castShadow = true;
gantry.add(cable);

const clawBase = new THREE.Mesh(new THREE.CylinderGeometry(0.6, 0.6, 0.5, 20), std('#444', 0.8, 0.2));
clawBase.castShadow = true; clawBase.receiveShadow = true;
scene.add(clawBase);
const prongMat = std('#EA4335', 0.8, 0.2);
const prongs = [];
for (let i = 0; i < 4; i++) {
  const g = new THREE.Group();
  box(g, [0, -0.8, 0], [0.2, 1.6, 0.1], prongMat);
  box(g, [0, -1.55, 0.2], [0.2, 0.1, 0.5], prongMat);
  scene.add(g);
  prongs.push(g);
}

// кольцо-прицел на полу: показывает, куда упадёт коготь (на телефоне глубину сцены оценить трудно)
const aimRing = new THREE.Mesh(
  new THREE.RingGeometry(0.9, 1.2, 40),
  new THREE.MeshBasicMaterial({ color: 0xfff176, transparent: true, opacity: 0.85, side: THREE.DoubleSide, depthWrite: false })
);
aimRing.rotation.x = -Math.PI / 2;
aimRing.renderOrder = 6;
scene.add(aimRing);
const claw = { x: 0, y: 8, z: 0, state: 'idle', closed: false, timer: 0, grabbed: null, vx: 0, vy: 0, vz: 0 };

// ───────────────────────── призы ─────────────────────────
const GEO = {
  box: new THREE.BoxGeometry(0.8, 0.8, 0.8),
  sphere: new THREE.SphereGeometry(0.5, 20, 16),
  dodecahedron: new THREE.DodecahedronGeometry(0.6),
};
const PRIZE_R = { box: 0.5, sphere: 0.5, dodecahedron: 0.58, bugdroid: 0.75 };
const prizeMats = {};
function prizeMat(color) {
  if (!prizeMats[color]) prizeMats[color] = new THREE.MeshStandardMaterial({ color, metalness: 0.5, roughness: 0.1, envMapIntensity: 1 });
  return prizeMats[color];
}
let prizes = [];
let prizeSeq = 0;
function randomXZ() {
  let x = (Math.random() - 0.5) * 7;
  const z = (Math.random() - 0.5) * 7;
  if (x < -2 && z > 2) x += 3;
  return [x, z];
}
function addPrize(type, color, value, x, y, z) {
  const group = new THREE.Group();
  if (type === 'bugdroid') {
    const b = makeBugdroid();
    b.scale.setScalar(0.35);
    b.position.set(0, -0.8, 0);
    group.add(b);
  } else {
    const m = new THREE.Mesh(GEO[type], prizeMat(color));
    m.castShadow = true; m.receiveShadow = true;
    group.add(m);
  }
  group.position.set(x, y, z);
  scene.add(group);
  prizes.push({
    id: ++prizeSeq, type, color, value, group, r: PRIZE_R[type],
    pos: new THREE.Vector3(x, y, z), vel: new THREE.Vector3(), grabbed: false, gone: false,
  });
}
function clearPrizes() {
  for (const p of prizes) scene.remove(p.group);
  prizes = [];
  claw.grabbed = null;
}
function initPrizes() {
  clearPrizes();
  for (let i = 0; i < 60; i++) {
    const [x, z] = randomXZ();
    const type = TYPES[Math.floor(Math.random() * TYPES.length)];
    const color = COLORS[Math.floor(Math.random() * COLORS.length)];
    addPrize(type, color, COLOR_VALUES[color] * TYPE_MULT[type], x, Math.random() * 4 + 1, z);
  }
  for (let i = 0; i < 3; i++) {
    const [x, z] = randomXZ();
    addPrize('bugdroid', '#34A853', 100, x, Math.random() * 2 + 5, z);
  }
}

// ───────────────────────── физика ─────────────────────────
const G = -9.81;
const BOXES = [
  [[-2, -0.25, -5], [5, 0.25, 5]],        // пол справа
  [[-5, -0.25, -5], [-2, 0.25, 2]],       // пол слева (с лотком в углу)
  [[-2, 0.25, 2], [-1.8, 3.25, 5]],       // стенка лотка
  [[-5, 0.25, 1.8], [-2, 3.25, 2]],       // стенка лотка
  [[-7, -3, -7], [-5.2, 12, 7]], [[5.2, -3, -7], [7, 12, 7]],
  [[-7, -3, -7], [7, 12, -5.2]], [[-7, -3, 5.2], [7, 12, 7]],
  [[-7, 10, -7], [7, 12, 7]],
];
const tmpA = new THREE.Vector3(), tmpB = new THREE.Vector3(), tmpN = new THREE.Vector3(), tmpC = new THREE.Vector3();

function collideBox(p, bx, h) {
  const mn = bx[0], mx = bx[1];
  const cx = Math.max(mn[0], Math.min(p.pos.x, mx[0]));
  const cy = Math.max(mn[1], Math.min(p.pos.y, mx[1]));
  const cz = Math.max(mn[2], Math.min(p.pos.z, mx[2]));
  tmpN.set(p.pos.x - cx, p.pos.y - cy, p.pos.z - cz);
  let d = tmpN.length();
  if (d >= p.r) return;
  let pen;
  if (d > 1e-6) { tmpN.divideScalar(d); pen = p.r - d; }
  else { // центр внутри коробки — выталкиваем вверх
    tmpN.set(0, 1, 0); pen = p.r + (mx[1] - p.pos.y);
  }
  p.pos.addScaledVector(tmpN, pen);
  const vn = p.vel.dot(tmpN);
  if (vn < 0) p.vel.addScaledVector(tmpN, -(1 + 0.22) * vn);
  if (tmpN.y > 0.5) { // опора: трение и качение
    const k = Math.max(0, 1 - 1.6 * h);
    p.vel.x *= k; p.vel.z *= k;
    p.grounded = true;
  }
}

function collideSegment(p, a, b, rc, vel, h) {
  tmpA.copy(b).sub(a);
  const l2 = tmpA.lengthSq();
  const t = l2 > 0 ? Math.max(0, Math.min(1, tmpB.copy(p.pos).sub(a).dot(tmpA) / l2)) : 0;
  tmpC.copy(a).addScaledVector(tmpA, t);
  tmpN.copy(p.pos).sub(tmpC);
  const d = tmpN.length();
  const R = p.r + rc;
  if (d >= R) return;
  if (d > 1e-6) tmpN.divideScalar(d); else tmpN.set(0, 1, 0);
  p.pos.addScaledVector(tmpN, R - d);
  const rel = p.vel.dot(tmpN) - vel.dot(tmpN);
  if (rel < 0) p.vel.addScaledVector(tmpN, -(1 + 0.1) * rel);
}

const clawVel = new THREE.Vector3();
let prongSegs = []; // [a, b, r] в мировых координатах для текущего кадра

function physicsStep(dt) {
  const subs = 3, h = dt / subs;
  for (let s = 0; s < subs; s++) {
    for (const p of prizes) {
      if (p.grabbed) continue;
      p.grounded = false;
      p.vel.y += G * h;
      p.pos.addScaledVector(p.vel, h);
    }
    for (const p of prizes) {
      if (p.grabbed) continue;
      // пола нет над лотком — шарик падает
      for (let i = 0; i < BOXES.length; i++) collideBox(p, BOXES[i], h);
      for (const sg of prongSegs) collideSegment(p, sg[0], sg[1], sg[2], clawVel, h);
    }
    for (let i = 0; i < prizes.length; i++) {
      const a = prizes[i];
      for (let j = i + 1; j < prizes.length; j++) {
        const b = prizes[j];
        tmpN.copy(b.pos).sub(a.pos);
        const R = a.r + b.r;
        if (Math.abs(tmpN.x) > R || Math.abs(tmpN.y) > R || Math.abs(tmpN.z) > R) continue;
        const d2 = tmpN.lengthSq();
        if (d2 >= R * R) continue;
        const d = Math.sqrt(d2);
        if (d > 1e-6) tmpN.divideScalar(d); else tmpN.set(0, 1, 0);
        const pen = R - d;
        const wa = a.grabbed ? 0 : 0.5, wb = b.grabbed ? 0 : 0.5;
        const sum = wa + wb || 1;
        a.pos.addScaledVector(tmpN, -pen * wa / sum);
        b.pos.addScaledVector(tmpN, pen * wb / sum);
        const rv = tmpA.copy(b.vel).sub(a.vel).dot(tmpN);
        if (rv < 0) {
          const j2 = -(1 + 0.15) * rv / 2;
          if (!a.grabbed) a.vel.addScaledVector(tmpN, -j2);
          if (!b.grabbed) b.vel.addScaledVector(tmpN, j2);
        }
        if (!a.grabbed) { a.vel.x *= 1 - 0.4 * h; a.vel.z *= 1 - 0.4 * h; }
        if (!b.grabbed) { b.vel.x *= 1 - 0.4 * h; b.vel.z *= 1 - 0.4 * h; }
      }
    }
  }
}

const rollQ = new THREE.Quaternion(), rollAxis = new THREE.Vector3();
function syncPrizeMeshes(dt) {
  for (const p of prizes) {
    p.group.position.copy(p.pos);
    if (p.type === 'bugdroid') continue;
    if (p.grabbed) continue;
    const sp = Math.hypot(p.vel.x, p.vel.z);
    if (sp > 0.02 && (p.grounded || p.pos.y < 1.6)) {
      rollAxis.set(p.vel.z, 0, -p.vel.x).normalize();
      rollQ.setFromAxisAngle(rollAxis, (sp * dt) / p.r);
      p.group.quaternion.premultiply(rollQ);
    }
  }
}

// ───────────────────────── управление ─────────────────────────
const keys = { w: false, a: false, s: false, d: false };
const joy = { x: 0, z: 0 };
window.addEventListener('keydown', (e) => {
  if (e.key === 'w' || e.key === 'ArrowUp') keys.w = true;
  if (e.key === 's' || e.key === 'ArrowDown') keys.s = true;
  if (e.key === 'a' || e.key === 'ArrowLeft') keys.a = true;
  if (e.key === 'd' || e.key === 'ArrowRight') keys.d = true;
  if (e.key === ' ') tryDrop();
});
window.addEventListener('keyup', (e) => {
  if (e.key === 'w' || e.key === 'ArrowUp') keys.w = false;
  if (e.key === 's' || e.key === 'ArrowDown') keys.s = false;
  if (e.key === 'a' || e.key === 'ArrowLeft') keys.a = false;
  if (e.key === 'd' || e.key === 'ArrowRight') keys.d = false;
});

{
  // «плавающий» джойстик: центр там, куда палец коснулся в левой зоне; работает и мышью, и пальцем
  const zone = $('zoneL'), stick = $('stick'), knob = $('knob');
  let pid = null, cx = 0, cy = 0;
  const R = 62;
  const place = (x, y) => {
    stick.style.position = 'fixed';
    stick.style.left = (x - stick.offsetWidth / 2) + 'px';
    stick.style.top = (y - stick.offsetHeight / 2) + 'px';
    stick.style.opacity = '1';
  };
  const move = (e) => {
    let dx = e.clientX - cx, dy = e.clientY - cy;
    const len = Math.hypot(dx, dy);
    if (len > R) { dx = dx / len * R; dy = dy / len * R; }
    knob.style.transform = `translate(${dx}px,${dy}px)`;
    const nx = dx / R, ny = dy / R;
    joy.x = Math.abs(nx) < 0.1 ? 0 : nx;
    joy.z = Math.abs(ny) < 0.1 ? 0 : ny; // вверх по экрану = от нас (z−)
  };
  const end = (e) => {
    if (e && e.pointerId !== pid) return;
    pid = null; joy.x = 0; joy.z = 0; knob.style.transform = '';
    stick.style.opacity = '.75';
    stick.style.position = ''; stick.style.left = ''; stick.style.top = '';
  };
  zone.addEventListener('pointerdown', (e) => {
    if (pid !== null) return;
    pid = e.pointerId;
    try { zone.setPointerCapture(pid); } catch (err) { /* не критично */ }
    cx = e.clientX; cy = e.clientY;
    place(cx, cy);
    move(e);
    e.preventDefault();
  });
  zone.addEventListener('pointermove', (e) => { if (e.pointerId === pid) move(e); });
  zone.addEventListener('pointerup', end);
  zone.addEventListener('pointercancel', end);
  zone.addEventListener('lostpointercapture', end);
}
$('drop').addEventListener('pointerdown', (e) => { tryDrop(); e.preventDefault(); });

// ───────────────────────── игра ─────────────────────────
let mode = 'ready'; // ready | playing | over
let score = 0, timeLeft = GAME_SECONDS;
const store = {
  get(k, d) { try { const v = localStorage.getItem(k); return v === null ? d : JSON.parse(v); } catch (e) { return d; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) {} },
};
let best = store.get('claw_best', 0);
let topList = store.get('claw_top', []);

function updateHud() {
  $('scoreV').textContent = String(score);
  const t = Math.max(0, Math.ceil(timeLeft));
  $('timeV').textContent = mode === 'playing' ? String(t) : '--';
  $('time').classList.toggle('low', mode === 'playing' && t <= 10);
  $('drop').classList.toggle('off', !(mode === 'playing' && claw.state === 'idle'));
}
function toast(text) {
  const el = $('toast');
  el.textContent = text;
  el.classList.remove('go');
  void el.offsetWidth;
  el.classList.add('go');
}
function resetClaw() {
  Object.assign(claw, { x: 0, y: 8, z: 0, state: 'idle', closed: false, timer: 0, grabbed: null });
}
function startGame() {
  score = 0; timeLeft = GAME_SECONDS;
  resetClaw();
  initPrizes();
  mode = 'playing';
  $('startO').classList.remove('show');
  $('overO').classList.remove('show');
  bridge('start');
  updateHud();
}
function endGame() {
  mode = 'over';
  const wasBest = score > best;
  if (wasBest) { best = score; store.set('claw_best', best); }
  topList.push(score);
  topList.sort((a, b) => b - a);
  topList = topList.slice(0, 5);
  store.set('claw_top', topList);
  $('finalV').textContent = String(score);
  let shownMe = false;
  $('topList').innerHTML = topList.map((v, i) => {
    const me = !shownMe && v === score;
    if (me) shownMe = true;
    return `<div class="${me ? 'me' : ''}"><span>${i + 1}. ${me ? 'Ты' : 'Игра'}</span><span>${v}</span></div>`;
  }).join('') || '<div><span>—</span><span>0</span></div>';
  $('overO').classList.add('show');
  bridge('over', score);
  updateHud();
}
function tryDrop() {
  if (mode === 'playing' && claw.state === 'idle') { claw.state = 'dropping'; bridge('drop'); updateHud(); }
}
$('startBtn').addEventListener('click', startGame);
$('startO').addEventListener('click', (e) => { if (e.target === $('startO')) startGame(); });
$('againBtn').addEventListener('click', startGame);
$('infoBtn').addEventListener('click', () => $('infoO').classList.add('show'));
$('infoClose').addEventListener('click', () => $('infoO').classList.remove('show'));
$('bestLine').textContent = best > 0 ? `Рекорд: ${best}` : '';

function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }

function updateClaw(dt) {
  const ox = claw.x, oy = claw.y, oz = claw.z;
  const speed = 6 * dt;
  if (claw.state === 'idle') {
    if (mode === 'playing') {
      let ax = joy.x, az = joy.z;
      if (keys.a) ax -= 1; if (keys.d) ax += 1;
      if (keys.w) az -= 1; if (keys.s) az += 1;
      const l = Math.hypot(ax, az);
      if (l > 1) { ax /= l; az /= l; }
      claw.x = clamp(claw.x + ax * speed, -4.5, 4.5);
      claw.z = clamp(claw.z + az * speed, -4.5, 4.5);
    }
  } else if (claw.state === 'dropping') {
    claw.y -= 6 * dt;
    if (claw.y <= 2.5) { claw.y = 2.5; claw.state = 'closing'; claw.timer = 0; }
  } else if (claw.state === 'closing') {
    claw.closed = true;
    claw.timer += dt;
    if (!claw.grabbed) {
      let best2 = null, min = 1.8;
      for (const p of prizes) {
        const d = Math.hypot(p.pos.x - claw.x, p.pos.y - (claw.y - 1.2), p.pos.z - claw.z);
        if (d < min) { min = d; best2 = p; }
      }
      if (best2) { claw.grabbed = best2; best2.grabbed = true; best2.vel.set(0, 0, 0); bridge('grab'); }
    }
    if (claw.timer > 1) claw.state = 'raising';
  } else if (claw.state === 'raising') {
    claw.y += 4 * dt;
    if (claw.y >= 8) { claw.y = 8; claw.state = 'returning'; }
  } else if (claw.state === 'returning') {
    const k = Math.min(1, 3 * dt);
    claw.x += (CHUTE_X - claw.x) * k;
    claw.z += (CHUTE_Z - claw.z) * k;
    if (Math.abs(claw.x - CHUTE_X) < 0.1 && Math.abs(claw.z - CHUTE_Z) < 0.1) { claw.state = 'opening'; claw.timer = 0; }
  } else if (claw.state === 'opening') {
    claw.closed = false;
    if (claw.grabbed) { claw.grabbed.grabbed = false; claw.grabbed.vel.set(0, 0, 0); claw.grabbed = null; }
    claw.timer += dt;
    if (claw.timer > 3.0) { claw.state = 'idle'; claw.closed = false; updateHud(); }
  }
  clawVel.set((claw.x - ox) / dt, (claw.y - oy) / dt, (claw.z - oz) / dt);
  if (clawVel.lengthSq() > 400) clawVel.set(0, 0, 0);

  if (claw.grabbed) claw.grabbed.pos.set(claw.x, claw.y - 1.2, claw.z);

  clawBase.position.set(claw.x, claw.y, claw.z);
  const targetAngle = claw.closed ? -Math.PI / 8 : Math.PI / 3;
  prongSegs = [];
  const qa = new THREE.Quaternion(), qb = new THREE.Quaternion(), YA = new THREE.Vector3(0, 1, 0), XA = new THREE.Vector3(1, 0, 0);
  for (let i = 0; i < 4; i++) {
    const ang = (i * Math.PI) / 2;
    const g = prongs[i];
    g.position.set(claw.x + Math.cos(ang) * 0.55, claw.y - 0.2, claw.z + Math.sin(ang) * 0.55);
    qa.setFromAxisAngle(YA, -ang - Math.PI / 2);
    qb.setFromAxisAngle(XA, targetAngle);
    qa.multiply(qb);
    g.quaternion.slerp(qa, Math.min(1, 10 * dt));
    // капсулы для физики: тело и «лапка»
    const a = g.position.clone();
    const b1 = new THREE.Vector3(0, -1.6, 0).applyQuaternion(g.quaternion).add(a);
    const f0 = new THREE.Vector3(0, -1.55, -0.05).applyQuaternion(g.quaternion).add(a);
    const f1 = new THREE.Vector3(0, -1.55, 0.45).applyQuaternion(g.quaternion).add(a);
    prongSegs.push([a, b1, 0.1], [f0, f1, 0.08]);
  }
  prongSegs.push([new THREE.Vector3(claw.x, claw.y - 0.25, claw.z), new THREE.Vector3(claw.x, claw.y + 0.25, claw.z), 0.6]);

  aimRing.visible = mode === 'playing' && (claw.state === 'idle' || claw.state === 'dropping');
  aimRing.position.set(claw.x, 0.3, claw.z);
  aimRing.scale.setScalar(1 + Math.sin(performance.now() / 180) * 0.06);
  crossbar.position.set(0, 10, claw.z);
  carriage.position.set(claw.x, 10, claw.z);
  cable.position.set(claw.x, (10 + claw.y) / 2, claw.z);
  cable.scale.set(1, Math.max(0.01, 10 - claw.y), 1);
}

function collectPrizes() {
  for (let i = prizes.length - 1; i >= 0; i--) {
    const p = prizes[i];
    if (p.pos.y < -0.5) {
      scene.remove(p.group);
      prizes.splice(i, 1);
      if (mode === 'playing') {
        score += p.value;
        toast('+' + p.value);
        bridge('prize', p.value);
        updateHud();
      }
    }
  }
  if (prizes.length < 10) initPrizes();
}

// ───────────────────────── цикл ─────────────────────────
let last = performance.now();
let frame = 0;
function loop(now) {
  requestAnimationFrame(loop);
  const dt = Math.min(0.033, Math.max(0.001, (now - last) / 1000));
  last = now;
  if (document.hidden) return;
  if (mode === 'playing') {
    timeLeft -= dt;
    if (timeLeft <= 0) { timeLeft = 0; endGame(); }
    else if (frame % 6 === 0) updateHud();
  }
  updateClaw(dt);
  physicsStep(dt);
  collectPrizes();
  syncPrizeMeshes(dt);
  if (frame % 3 === 0) renderer.shadowMap.needsUpdate = true;
  frame++;
  renderer.render(scene, camera);
}

initPrizes();
resetClaw();
updateHud();
requestAnimationFrame(loop);
window.__claw = { claw, get prizes() { return prizes; }, get score() { return score; }, startGame, get mode() { return mode; } };
