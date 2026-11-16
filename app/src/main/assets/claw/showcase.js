/**
 * Мини-презентация выигрыша: зверёк «выпрыгивает» по центру экрана, крутится, вокруг сияние, звёздочки и конфетти.
 * Рисуется тем же рендерером поверх остановленной сцены автомата (вторая сцена, без второго WebGL-контекста).
 * Текст и кнопку рисует страница (HTML), здесь только 3D.
 */
import * as THREE from './three.module.min.js';
import { makePlushMesh } from './plush.js';

const PALETTE = [0xff5a5f, 0xffc233, 0x3ddc84, 0x4dabf7, 0xb57bff, 0xff8fd1];
const COUNT = 220;
const SPARKS = 26;
const CAM_Z = 7;
const VIS_H = 2 * CAM_Z * Math.tan(THREE.MathUtils.degToRad(17.5));

function canvasTex(size, draw) {
  const c = document.createElement('canvas');
  c.width = c.height = size;
  draw(c.getContext('2d'), size);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  return t;
}
const dotTex = () => canvasTex(64, (g, s) => {
  const r = g.createRadialGradient(s / 2, s / 2, 0, s / 2, s / 2, s / 2);
  r.addColorStop(0, 'rgba(255,255,255,1)'); r.addColorStop(0.7, 'rgba(255,255,255,1)'); r.addColorStop(1, 'rgba(255,255,255,0)');
  g.fillStyle = r; g.fillRect(0, 0, s, s);
});
const starTex = () => canvasTex(64, (g, s) => {
  g.translate(s / 2, s / 2);
  g.fillStyle = '#fff';
  g.beginPath();
  for (let i = 0; i < 8; i++) {
    const a = (i * Math.PI) / 4, r = i % 2 ? s * 0.12 : s * 0.48;
    g.lineTo(Math.cos(a) * r, Math.sin(a) * r);
  }
  g.closePath(); g.fill();
});
const sunTex = () => canvasTex(512, (g, s) => {
  g.translate(s / 2, s / 2);
  const n = 14;
  g.fillStyle = 'rgba(255,230,140,0.75)';
  for (let i = 0; i < n; i++) {
    const a0 = (i / n) * Math.PI * 2, a1 = a0 + (Math.PI * 2) / n / 2;
    g.beginPath(); g.moveTo(0, 0); g.arc(0, 0, s, a0, a1); g.closePath(); g.fill();
  }
  g.globalCompositeOperation = 'destination-in';
  const r = g.createRadialGradient(0, 0, 0, 0, 0, s / 2);
  r.addColorStop(0, 'rgba(0,0,0,1)'); r.addColorStop(1, 'rgba(0,0,0,0)');
  g.fillStyle = r; g.fillRect(-s / 2, -s / 2, s, s);
});

const easeOutBack = (x) => { const c1 = 1.70158, c3 = c1 + 1; return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2); };
const easeInOut = (x) => (x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2);

export class Showcase {
  constructor(renderer) {
    this.renderer = renderer;
    this.active = false;
    this.t = 0;
    this.mesh = null;
    this.rarity = 1;
    this.aspect = 1;
    this.scene = new THREE.Scene();
    this.cam = new THREE.PerspectiveCamera(35, 1, 0.1, 60);
    this.cam.position.set(0, 0, CAM_Z);

    this.scene.add(new THREE.AmbientLight(0xffffff, 2.2));
    const key = new THREE.DirectionalLight(0xffffff, 3.2); key.position.set(2, 3, 5); this.scene.add(key);
    const rim = new THREE.DirectionalLight(0x9ec5ff, 2.0); rim.position.set(-4, 2, -3); this.scene.add(rim);

    this.dim = new THREE.Mesh(new THREE.PlaneGeometry(60, 60), new THREE.MeshBasicMaterial({ color: 0x060818, transparent: true, opacity: 0.72, depthWrite: false }));
    this.dim.position.z = -8;
    this.scene.add(this.dim);

    this.sun = new THREE.Mesh(new THREE.PlaneGeometry(11, 11), new THREE.MeshBasicMaterial({ map: sunTex(), transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, opacity: 0 }));
    this.sun.position.z = -2;
    this.scene.add(this.sun);

    // конфетти
    this.pos = new Float32Array(COUNT * 3);
    this.col = new Float32Array(COUNT * 3);
    this.vel = new Float32Array(COUNT * 3);
    this.life = new Float32Array(COUNT);
    this.cursor = 0;
    for (let i = 0; i < COUNT; i++) this.pos[i * 3 + 1] = -100;
    const pg = new THREE.BufferGeometry();
    pg.setAttribute('position', new THREE.BufferAttribute(this.pos, 3));
    pg.setAttribute('color', new THREE.BufferAttribute(this.col, 3));
    this.confetti = new THREE.Points(pg, new THREE.PointsMaterial({ size: 0.24, map: dotTex(), vertexColors: true, transparent: true, depthWrite: false, sizeAttenuation: true }));
    this.confetti.frustumCulled = false;
    this.scene.add(this.confetti);

    // мерцающие звёздочки вокруг зверька
    const sp = new Float32Array(SPARKS * 3);
    const sg = new THREE.BufferGeometry();
    sg.setAttribute('position', new THREE.BufferAttribute(sp, 3));
    this.sparkBase = [];
    for (let i = 0; i < SPARKS; i++) this.sparkBase.push({ a: Math.random() * Math.PI * 2, r: 1.2 + Math.random() * 1.3, y: (Math.random() - 0.5) * 2.4, ph: Math.random() * 6 });
    this.sparks = new THREE.Points(sg, new THREE.PointsMaterial({ size: 0.32, map: starTex(), color: 0xfff0a0, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending }));
    this.sparks.frustumCulled = false;
    this.scene.add(this.sparks);

    this.group = new THREE.Group();
    this.scene.add(this.group);
    this.homeX = 0; this.homeY = 0; this.targetH = 2.4;
  }

  setEnvironment(tex) {
    this.scene.environment = tex;
    this.scene.environmentIntensity = 1.2;
  }

  resize(w, h) {
    this.aspect = w / h;
    this.cam.aspect = this.aspect;
    this.cam.updateProjectionMatrix();
    const visW = VIS_H * this.aspect;
    const landscape = this.aspect > 1.15;
    // в альбомной ориентации текст справа, зверёк слева; в портретной зверёк выше середины, текст снизу
    this.homeX = landscape ? -visW * 0.2 : 0;
    this.homeY = landscape ? -0.1 : 0.55;
    this.targetH = landscape ? Math.min(2.6, VIS_H * 0.62) : Math.min(2.4, visW * 0.7);
    this.group.position.set(this.homeX, this.homeY, 0);
    this.sun.position.set(this.homeX, this.homeY, -2);
    if (this.mesh) this.fitMesh();
  }

  fitMesh() {
    const g = this.mesh.geometry;
    if (!g.boundingBox) g.computeBoundingBox();
    const h = g.boundingBox.max.y - g.boundingBox.min.y;
    this.baseScale = this.targetH / h;
  }

  start(speciesId, rarity) {
    this.stop();
    this.mesh = makePlushMesh(speciesId);
    this.mesh.castShadow = false; this.mesh.receiveShadow = false;
    this.group.add(this.mesh);
    this.rarity = rarity;
    this.fitMesh();
    this.mesh.scale.setScalar(0.001);
    this.t = 0;
    this.nextBurst = 0;
    this.burstsDone = 0;
    this.active = true;
  }

  stop() {
    this.active = false;
    if (this.mesh) { this.group.remove(this.mesh); this.mesh = null; }
    for (let i = 0; i < COUNT; i++) { this.life[i] = 0; this.pos[i * 3 + 1] = -100; }
    this.confetti.geometry.attributes.position.needsUpdate = true;
  }

  burst(n, speed) {
    const c = new THREE.Color();
    for (let k = 0; k < n; k++) {
      const i = this.cursor++ % COUNT;
      const a = Math.random() * Math.PI * 2, up = Math.random();
      const sp = speed * (0.4 + Math.random() * 0.8);
      this.pos[i * 3] = this.homeX; this.pos[i * 3 + 1] = this.homeY + 0.2; this.pos[i * 3 + 2] = 0.5;
      this.vel[i * 3] = Math.cos(a) * sp * (0.4 + up * 0.6);
      this.vel[i * 3 + 1] = Math.sin(a * 0.5 + 0.3) * sp * 0.5 + 2 + up * 3;
      this.vel[i * 3 + 2] = (Math.random() - 0.3) * sp * 0.5;
      this.life[i] = 2 + Math.random() * 1.6;
      c.setHex(PALETTE[(Math.random() * PALETTE.length) | 0]);
      this.col[i * 3] = c.r; this.col[i * 3 + 1] = c.g; this.col[i * 3 + 2] = c.b;
    }
    this.confetti.geometry.attributes.color.needsUpdate = true;
  }

  update(dt) {
    if (!this.active) return;
    this.t += dt;
    const t = this.t;
    if (this.mesh) {
      const pop = t < 0.7 ? easeOutBack(t / 0.7) : 1;
      this.mesh.scale.setScalar(Math.max(0.001, this.baseScale * pop));
      this.mesh.rotation.y = t < 1.2 ? easeInOut(t / 1.2) * Math.PI * 2 : Math.sin((t - 1.2) * 1.3) * 0.55;
      const hop = t > 0.7 ? Math.abs(Math.sin(t * 3.2)) * 0.14 : 0;
      this.mesh.position.y = hop;
      this.mesh.rotation.z = t > 0.7 ? Math.sin(t * 3.2) * 0.05 : 0;
    }
    this.sun.material.opacity = Math.min(0.85, t * 1.4) * (0.8 + 0.2 * Math.sin(t * 3));
    this.sun.rotation.z += dt * 0.35;

    // залпы конфетти: сильнее у редких зверей
    if (t >= this.nextBurst) {
      const first = this.burstsDone === 0;
      this.burst(first ? 70 + this.rarity * 25 : 26 + this.rarity * 8, first ? 6 : 4.5);
      this.burstsDone++;
      this.nextBurst = t + (this.rarity >= 3 ? 1.0 : 1.5);
    }
    if (t < 2.5) this.rain(dt);

    for (let i = 0; i < COUNT; i++) {
      if (this.life[i] <= 0) continue;
      this.life[i] -= dt;
      if (this.life[i] <= 0) { this.pos[i * 3 + 1] = -100; continue; }
      this.vel[i * 3 + 1] -= 6 * dt;
      this.vel[i * 3] *= 1 - 0.6 * dt; this.vel[i * 3 + 2] *= 1 - 0.6 * dt;
      this.pos[i * 3] += this.vel[i * 3] * dt;
      this.pos[i * 3 + 1] += this.vel[i * 3 + 1] * dt;
      this.pos[i * 3 + 2] += this.vel[i * 3 + 2] * dt;
    }
    this.confetti.geometry.attributes.position.needsUpdate = true;

    const sp = this.sparks.geometry.attributes.position;
    for (let i = 0; i < SPARKS; i++) {
      const b = this.sparkBase[i], a = b.a + t * 0.5;
      sp.array[i * 3] = this.homeX + Math.cos(a) * b.r;
      sp.array[i * 3 + 1] = this.homeY + b.y + Math.sin(t * 2 + b.ph) * 0.15;
      sp.array[i * 3 + 2] = Math.sin(a) * b.r * 0.4 + 0.5;
    }
    sp.needsUpdate = true;
    this.sparks.material.opacity = 0.55 + 0.45 * Math.sin(t * 7);
  }

  rain(dt) {
    const n = Math.round(dt * 60);
    const c = new THREE.Color();
    for (let k = 0; k < n; k++) {
      const i = this.cursor++ % COUNT;
      const visW = VIS_H * this.aspect;
      this.pos[i * 3] = (Math.random() - 0.5) * visW; this.pos[i * 3 + 1] = VIS_H / 2 + 0.3; this.pos[i * 3 + 2] = 0.5 + Math.random();
      this.vel[i * 3] = (Math.random() - 0.5) * 0.8; this.vel[i * 3 + 1] = -1.5 - Math.random(); this.vel[i * 3 + 2] = 0;
      this.life[i] = 3;
      c.setHex(PALETTE[(Math.random() * PALETTE.length) | 0]);
      this.col[i * 3] = c.r; this.col[i * 3 + 1] = c.g; this.col[i * 3 + 2] = c.b;
    }
    if (n) this.confetti.geometry.attributes.color.needsUpdate = true;
  }

  render() {
    if (!this.active) return;
    const r = this.renderer;
    r.autoClear = false;
    r.clearDepth();
    r.render(this.scene, this.cam);
    r.autoClear = true;
  }
}
