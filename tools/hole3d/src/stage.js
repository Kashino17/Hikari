// Darstellung: Tischdecke mit echtem Loch, Schacht, die Dinge als Instanzen, Kamera.
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { CLOTHS } from './levels.js';
import { FALL, GONE, REST } from './sim.js';

const FOV = 42;
const CAM_UP = 13;
const CAM_BACK = 9.5;

/** Lädt die gepackten Modelle: Name → Geometrie, dazu das gemeinsame Material. */
export async function loadFoods(url) {
  const gltf = await new GLTFLoader().loadAsync(url);
  const geometries = new Map();
  let map = null;
  gltf.scene.updateMatrixWorld(true);
  gltf.scene.traverse((node) => {
    if (!node.isMesh) return;
    const geometry = node.geometry.clone();
    geometry.applyMatrix4(node.matrixWorld);
    geometries.set(node.name.replace(/_\d+$/, ''), geometry);
    map ??= node.material.map;
  });
  // Lambert reicht für die flachen Farben des Kits und ist auf dem Handy am günstigsten.
  const material = new THREE.MeshLambertMaterial({ map });
  return { geometries, material };
}

const GROUND_VERT = /* glsl */ `
  varying vec2 vPos;
  void main() {
    vec4 world = modelMatrix * vec4(position, 1.0);
    vPos = world.xz;
    gl_Position = projectionMatrix * viewMatrix * world;
  }
`;

// Die Decke hat ein echtes Loch: Alles im Lochkreis wird nicht gezeichnet, dahinter liegt der Schacht.
const GROUND_FRAG = /* glsl */ `
  uniform vec3 uA;
  uniform vec3 uB;
  uniform vec3 uTable;
  uniform vec3 uHole; // x, z, Radius
  uniform float uArena;
  varying vec2 vPos;
  void main() {
    float d = distance(vPos, uHole.xy);
    if (d < uHole.z) discard;
    vec2 cell = floor(vPos / 2.0);
    vec3 col = mix(uA, uB, mod(cell.x + cell.y, 2.0));
    float reach = max(abs(vPos.x), abs(vPos.y));
    float inside = 1.0 - step(uArena, reach);
    float hem = smoothstep(uArena - 0.42, uArena - 0.30, reach);
    col = mix(col, vec3(1.0), hem * 0.6);
    // Schattenwurf der Decke auf den Tisch.
    float drop = 1.0 - smoothstep(uArena, uArena + 1.6, reach) * 0.0;
    vec3 table = uTable * (0.82 + 0.18 * smoothstep(uArena, uArena + 1.4, reach));
    col = mix(table, col, inside) * drop;
    // Das Loch dunkelt seinen Rand leicht ab — das gibt Tiefe.
    float ao = 1.0 - smoothstep(uHole.z, uHole.z * 1.55, d);
    col *= 1.0 - ao * 0.22;
    gl_FragColor = vec4(col, 1.0);
    #include <colorspace_fragment>
  }
`;

const WELL_FRAG = /* glsl */ `
  varying float vDepth;
  void main() {
    // Fast schwarz; nur ganz oben fängt die Wand noch etwas Licht.
    vec3 col = mix(vec3(0.075, 0.05, 0.12), vec3(0.0), smoothstep(0.0, 0.42, vDepth));
    col += vec3(0.10, 0.08, 0.16) * (1.0 - smoothstep(0.0, 0.05, vDepth));
    gl_FragColor = vec4(col, 1.0);
    #include <colorspace_fragment>
  }
`;
const WELL_VERT = /* glsl */ `
  varying float vDepth;
  void main() {
    vDepth = 0.5 - position.y; // 0 oben am Rand, 1 unten
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }
`;

export class Stage {
  constructor(canvas, foods) {
    this.foods = foods;
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.scene = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(FOV, 1, 0.5, 220);
    this.focus = new THREE.Vector3();
    this.zoom = 1;

    this.scene.add(new THREE.HemisphereLight(0xffffff, 0x9a9a9a, 1.25));
    const sun = new THREE.DirectionalLight(0xffffff, 1.5);
    sun.position.set(5, 12, 7);
    this.scene.add(sun);

    this.groundUniforms = {
      uA: { value: new THREE.Color() },
      uB: { value: new THREE.Color() },
      uTable: { value: new THREE.Color() },
      uHole: { value: new THREE.Vector3(0, 0, 1) },
      uArena: { value: 10 },
    };
    const ground = new THREE.Mesh(
      new THREE.PlaneGeometry(400, 400).rotateX(-Math.PI / 2),
      new THREE.ShaderMaterial({ uniforms: this.groundUniforms, vertexShader: GROUND_VERT, fragmentShader: GROUND_FRAG }),
    );
    this.scene.add(ground);

    // Schacht unter dem Loch: Wand mit Verlauf ins Schwarze und ein Boden.
    this.well = new THREE.Group();
    const wall = new THREE.Mesh(
      new THREE.CylinderGeometry(1, 1, 1, 48, 1, true),
      new THREE.ShaderMaterial({ vertexShader: WELL_VERT, fragmentShader: WELL_FRAG, side: THREE.BackSide }),
    );
    wall.position.y = -0.5;
    const floor = new THREE.Mesh(new THREE.CircleGeometry(1, 48).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ color: 0x000000 }));
    floor.position.y = -1;
    this.well.add(wall, floor);
    this.scene.add(this.well);

    this.rim = new THREE.Mesh(
      new THREE.RingGeometry(1.0, 1.085, 64).rotateX(-Math.PI / 2),
      new THREE.MeshBasicMaterial({ color: 0xffffff }),
    );
    this.rim.position.y = 0.03;
    this.scene.add(this.rim);
    this.rimPulse = 0;

    this.shadowMaterial = new THREE.MeshBasicMaterial({ color: 0x000000, transparent: true, opacity: 0.16, depthWrite: false });
    this.shadowGeometry = new THREE.CircleGeometry(0.5, 20).rotateX(-Math.PI / 2);
    this.meshes = [];
    this.active = new Set();

    this._m = new THREE.Matrix4();
    this._p = new THREE.Vector3();
    this._s = new THREE.Vector3();
    this._q = new THREE.Quaternion();
    this._q2 = new THREE.Quaternion();
    this._axis = new THREE.Vector3();
    this._up = new THREE.Vector3(0, 1, 0);
    this._look = new THREE.Vector3();
  }

  resize(width, height) {
    this.renderer.setSize(width, height, false);
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.height = height;
    // Hochkant sieht man seitlich wenig — etwas weiter weg.
    this.portrait = width / height < 0.7 ? 1.18 : 1;
  }

  /** Baut die Szene für ein Level: je Modell ein Instanzen-Netz, dazu die Kontaktschatten. */
  setLevel(sim, clothName) {
    for (const mesh of this.meshes) {
      this.scene.remove(mesh);
      mesh.dispose();
    }
    this.meshes = [];
    this.active.clear();

    const cloth = CLOTHS[clothName];
    this.groundUniforms.uA.value.set(cloth.a);
    this.groundUniforms.uB.value.set(cloth.b);
    this.groundUniforms.uTable.value.set(cloth.table);
    this.groundUniforms.uArena.value = sim.arena;
    this.renderer.setClearColor(new THREE.Color(cloth.table).multiplyScalar(0.82));

    const counts = new Map();
    for (const item of sim.items) counts.set(item.type, (counts.get(item.type) ?? 0) + 1);
    const byType = new Map();
    for (const [type, count] of counts) {
      const mesh = new THREE.InstancedMesh(this.foods.geometries.get(type), this.foods.material, count);
      mesh.frustumCulled = false; // die Instanzen liegen überall, die Grenzen des Netzes sagen nichts
      this.scene.add(mesh);
      this.meshes.push(mesh);
      byType.set(type, { mesh, next: 0 });
    }
    this.shadows = new THREE.InstancedMesh(this.shadowGeometry, this.shadowMaterial, sim.items.length);
    this.shadows.frustumCulled = false;
    this.shadows.renderOrder = 1;
    this.scene.add(this.shadows);
    this.meshes.push(this.shadows);

    this.slots = sim.items.map((item) => {
      const entry = byType.get(item.type);
      return { mesh: entry.mesh, slot: entry.next++ };
    });
    for (const item of sim.items) this.writeItem(sim, item, 0);
    for (const mesh of this.meshes) mesh.instanceMatrix.needsUpdate = true;

    this.zoom = this.zoomFor(sim.hole.r);
    this.focus.set(sim.hole.x, 0, Math.min(sim.hole.z, Math.max(0, sim.arena - 5.5 * this.zoom)));
    this.placeHole(sim.hole, 0);
    this.placeCamera();
  }

  zoomFor(radius) {
    return (1.16 + 0.22 * radius) * (this.portrait ?? 1);
  }

  /** Welt-Einheiten je Bildschirmpunkt auf Höhe des Lochs — damit das Loch dem Finger folgt. */
  worldPerPixel() {
    const distance = Math.hypot(CAM_UP, CAM_BACK) * this.zoom;
    return (2 * distance * Math.tan((FOV * Math.PI) / 360)) / (this.height || 800);
  }

  /** Senkrechte Fingerwege sind auf dem schräg gesehenen Boden länger. */
  get depthStretch() {
    return Math.hypot(CAM_UP, CAM_BACK) / CAM_UP;
  }

  pulseRim() {
    this.rimPulse = 1;
  }

  placeHole(hole, dt) {
    const r = hole.r;
    this.groundUniforms.uHole.value.set(hole.x, hole.z, r);
    const depth = 3 * r + 2.5;
    this.well.position.set(hole.x, 0, hole.z);
    this.well.scale.set(r, depth, r);
    this.rimPulse = Math.max(0, this.rimPulse - dt * 2.2);
    const pulse = 1 + this.rimPulse * 0.14;
    this.rim.position.set(hole.x, 0.03, hole.z);
    this.rim.scale.set(r * pulse, 1, r * pulse);
    this.rim.material.color.setRGB(1, 1 - this.rimPulse * 0.25, 1 - this.rimPulse * 0.85);
  }

  placeCamera() {
    const k = this.zoom;
    this.camera.position.set(this.focus.x, CAM_UP * k, this.focus.z + CAM_BACK * k);
    this._look.set(this.focus.x, 0, this.focus.z - 0.9 * k);
    this.camera.lookAt(this._look);
  }

  /** Überträgt den Stand der Simulation ins Bild. */
  sync(sim, dt, time) {
    const hole = sim.hole;
    this.placeHole(hole, dt);

    // Die Kamera folgt dem Loch, bleibt aber über der Decke — am Rand soll nicht der halbe
    // Bildschirm leerer Tisch sein.
    const follow = Math.min(1, dt * 9);
    const sideways = Math.max(0, sim.arena - 3.5 * this.zoom);
    const towards = Math.max(0, sim.arena - 5.5 * this.zoom);
    const away = Math.max(0, sim.arena - 4.2 * this.zoom);
    const fx = Math.max(-sideways, Math.min(sideways, hole.x));
    const fz = Math.max(-away, Math.min(towards, hole.z));
    this.focus.x += (fx - this.focus.x) * follow;
    this.focus.z += (fz - this.focus.z) * follow;
    this.zoom += (this.zoomFor(hole.r) - this.zoom) * Math.min(1, dt * 2.5);
    this.placeCamera();

    // Nur anfassen, was sich bewegt — und was sich gerade noch bewegt hat (letzter Stand).
    const now = new Set(sim.moving);
    for (const index of sim.touched) now.add(index);
    for (const index of this.active) if (!now.has(index)) this.writeItem(sim, sim.items[index], time);
    for (const index of now) this.writeItem(sim, sim.items[index], time);
    if (now.size || this.active.size) for (const mesh of this.meshes) mesh.instanceMatrix.needsUpdate = true;
    this.active = now;
  }

  writeItem(sim, item, time) {
    const { mesh, slot } = this.slots[item.index];
    const gone = item.state === GONE;
    const scale = gone ? 0 : item.vis;
    let y = item.y;

    this._q.setFromAxisAngle(this._up, item.yaw);
    if (item.tilt > 0) {
      // Zum Loch kippen: Drehachse quer zur Richtung, Fuß bleibt stehen.
      const dx = Math.cos(item.tiltDir);
      const dz = Math.sin(item.tiltDir);
      this._q2.setFromAxisAngle(this._axis.set(dz, 0, -dx), item.tilt * 0.65);
      this._q.premultiply(this._q2);
      y -= item.tilt * 0.12 * item.r;
    }
    if (item.wobble > 0) {
      this._q2.setFromAxisAngle(this._axis.set(1, 0, 0.4).normalize(), Math.sin(time * 38 + item.index) * 0.07 * item.wobble);
      this._q.premultiply(this._q2);
    }
    if (item.state === FALL) {
      this._axis.set(Math.cos(item.spinAxis), 0.35, Math.sin(item.spinAxis)).normalize();
      this._q2.setFromAxisAngle(this._axis, item.spin);
      this._q.premultiply(this._q2);
    }
    this._m.compose(this._p.set(item.x, y, item.z), this._q, this._s.setScalar(scale));
    mesh.setMatrixAt(slot, this._m);

    // Kontaktschatten nur für das, was ruhig am Boden steht.
    const grounded = item.state === REST && item.y < 0.05;
    const shadow = grounded ? item.vis * 1.18 : 0;
    this._m.compose(this._p.set(item.x, 0.02, item.z), this._q.identity(), this._s.set(shadow, 1, shadow));
    this.shadows.setMatrixAt(item.index, this._m);
  }

  /** Langsamer Kameraschwenk über das Level, solange das Menü offen ist. */
  idle(sim, time) {
    const a = time * 0.12;
    const reach = sim.arena * 0.35;
    this.focus.set(Math.sin(a) * reach, 0, Math.cos(a * 0.8) * reach * 0.6);
    this.zoom = 2.1 * (this.portrait ?? 1);
    this.groundUniforms.uHole.value.set(0, 0, 0);
    this.well.scale.setScalar(0.0001);
    this.rim.scale.setScalar(0.0001);
    this.placeCamera();
  }

  render() {
    this.renderer.render(this.scene, this.camera);
  }
}
