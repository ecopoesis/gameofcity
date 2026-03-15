import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';

// --- Constants matching CityRenderer.kt ---
const CS = 32;
const TERRAIN_H = 2;
const PEEP_H = 18;

const TERRAIN_COLORS = {
    Road:      new THREE.Color(0.30, 0.30, 0.30),
    Sidewalk:  new THREE.Color(0.70, 0.70, 0.60),
    Park:      new THREE.Color(0.20, 0.60, 0.20),
    Interior:  new THREE.Color(0.50, 0.50, 0.50),
    Tunnel:    new THREE.Color(0.20, 0.20, 0.20),
    Empty:     new THREE.Color(0.10, 0.10, 0.15),
};

const BUILDING_COLORS = {
    Residential:   new THREE.Color(0.80, 0.50, 0.20),
    Commercial:    new THREE.Color(0.20, 0.50, 0.90),
    Industrial:    new THREE.Color(0.60, 0.30, 0.30),
    Entertainment: new THREE.Color(0.90, 0.80, 0.10),
};

const BUILDING_HEIGHTS = {
    Residential: 64,
    Commercial: 48,
    Industrial: 32,
    Entertainment: 96,
};

// --- Three.js setup ---
const scene = new THREE.Scene();
scene.background = new THREE.Color(0.12, 0.12, 0.18);

const camera = new THREE.PerspectiveCamera(67, window.innerWidth / window.innerHeight, 10, 5000);
camera.position.set(400, 500, 400);

const renderer = new THREE.WebGLRenderer({ antialias: true });
renderer.setSize(window.innerWidth, window.innerHeight);
renderer.setPixelRatio(window.devicePixelRatio);
document.body.appendChild(renderer.domElement);

const controls = new OrbitControls(camera, renderer.domElement);
controls.enableDamping = true;
controls.dampingFactor = 0.1;

// Lighting matching desktop
const ambient = new THREE.AmbientLight(0xffffff, 0.45);
scene.add(ambient);
const dirLight = new THREE.DirectionalLight(0xffffff, 0.8);
dirLight.position.set(-1, 0.8, -0.3).normalize();
scene.add(dirLight);

// --- State ---
let mapBuilt = false;
let terrainGroup = null;
let buildingGroup = null;
let peepMesh = null;
let peepData = [];      // latest peep positions
let prevPeepData = [];  // previous tick positions for lerp
let lastPeepTime = 0;
let paused = false;
let tickDelayMs = 50;
let snapshotData = null; // latest full snapshot for inspector

// --- HUD ---
const hudEl = document.getElementById('hud');
const tickEl = document.getElementById('tick');
const peepCountEl = document.getElementById('peepCount');
const speedEl = document.getElementById('speed');
const pausedLabel = document.getElementById('pausedLabel');
const statusEl = document.getElementById('status');

function updateHUD(tick, peepCount) {
    tickEl.textContent = tick;
    peepCountEl.textContent = peepCount;
    const speedFactor = Math.round(50 / tickDelayMs * 10) / 10;
    speedEl.textContent = speedFactor + 'x';
    pausedLabel.style.display = paused ? 'block' : 'none';
}

// --- Build city geometry from snapshot ---
function buildCity(data) {
    // Remove old geometry
    if (terrainGroup) scene.remove(terrainGroup);
    if (buildingGroup) scene.remove(buildingGroup);

    terrainGroup = new THREE.Group();
    buildingGroup = new THREE.Group();

    const mapData = data.map;

    // Terrain — instanced meshes per type
    const terrainByType = {};
    for (const cell of mapData.cells) {
        if (cell.buildingId != null) continue; // skip interior cells under buildings
        const t = cell.terrain;
        if (!terrainByType[t]) terrainByType[t] = [];
        terrainByType[t].push(cell);
    }

    const boxGeo = new THREE.BoxGeometry(CS, TERRAIN_H, CS);
    for (const [type, cells] of Object.entries(terrainByType)) {
        const color = TERRAIN_COLORS[type] || TERRAIN_COLORS.Empty;
        const mat = new THREE.MeshLambertMaterial({ color });
        const mesh = new THREE.InstancedMesh(boxGeo, mat, cells.length);
        const matrix = new THREE.Matrix4();
        cells.forEach((cell, i) => {
            matrix.setPosition(cell.x * CS + CS / 2, TERRAIN_H / 2, cell.y * CS + CS / 2);
            mesh.setMatrixAt(i, matrix);
        });
        mesh.instanceMatrix.needsUpdate = true;
        terrainGroup.add(mesh);
    }

    // Buildings — instanced meshes per type
    const buildingsByType = {};
    for (const bld of mapData.buildings) {
        if (!buildingsByType[bld.type]) buildingsByType[bld.type] = [];
        buildingsByType[bld.type].push(bld);
    }

    for (const [type, buildings] of Object.entries(buildingsByType)) {
        const height = BUILDING_HEIGHTS[type] || 32;
        const color = BUILDING_COLORS[type] || new THREE.Color(0.5, 0.5, 0.5);
        const bldGeo = new THREE.BoxGeometry(CS, height, CS);
        const mat = new THREE.MeshLambertMaterial({ color });

        // Count total cells
        let totalCells = 0;
        for (const bld of buildings) totalCells += bld.cells.length;

        const mesh = new THREE.InstancedMesh(bldGeo, mat, totalCells);
        const matrix = new THREE.Matrix4();
        let idx = 0;
        for (const bld of buildings) {
            for (const cell of bld.cells) {
                const wy = TERRAIN_H + 0.01 + height / 2;
                matrix.setPosition(cell.x * CS + CS / 2, wy, cell.y * CS + CS / 2);
                mesh.setMatrixAt(idx++, matrix);
            }
        }
        mesh.instanceMatrix.needsUpdate = true;
        buildingGroup.add(mesh);
    }

    scene.add(terrainGroup);
    scene.add(buildingGroup);

    // Center camera on map
    const cx = mapData.width * CS / 2;
    const cz = mapData.height * CS / 2;
    controls.target.set(cx, 0, cz);
    camera.position.set(cx + 300, 400, cz + 300);
    controls.update();
}

// --- Peep rendering ---
function initPeeps(count) {
    if (peepMesh) scene.remove(peepMesh);
    const geo = new THREE.SphereGeometry(3, 8, 6);
    const mat = new THREE.MeshLambertMaterial({ color: 0xffffff });
    peepMesh = new THREE.InstancedMesh(geo, mat, Math.max(count, 1));
    peepMesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
    scene.add(peepMesh);
}

function updatePeeps(interpolate) {
    if (!peepMesh || peepData.length === 0) return;

    // Resize if needed
    if (peepData.length > peepMesh.count) {
        initPeeps(peepData.length);
    }

    const matrix = new THREE.Matrix4();
    const color = new THREE.Color();
    const elapsed = performance.now() - lastPeepTime;
    const t = interpolate ? Math.min(elapsed / tickDelayMs, 1) : 1;

    for (let i = 0; i < peepData.length; i++) {
        const p = peepData[i];
        const prev = prevPeepData.find(pp => pp.id === p.id);

        let wx, wz;
        if (prev && t < 1) {
            wx = THREE.MathUtils.lerp(prev.x * CS + CS / 2, p.x * CS + CS / 2, t);
            wz = THREE.MathUtils.lerp(prev.y * CS + CS / 2, p.y * CS + CS / 2, t);
        } else {
            wx = p.x * CS + CS / 2;
            wz = p.y * CS + CS / 2;
        }
        const wy = TERRAIN_H + PEEP_H;

        matrix.setPosition(wx, wy, wz);
        peepMesh.setMatrixAt(i, matrix);

        // Color based on needs
        if (p.hunger > 0.6) {
            color.setRGB(1, 0.3, 0.3);
        } else if (p.fatigue > 0.8) {
            color.setRGB(0.3, 0.5, 1);
        } else {
            color.setRGB(1, 1, 1);
        }
        peepMesh.setColorAt(i, color);
    }

    // Hide unused instances
    const hideMatrix = new THREE.Matrix4().setPosition(0, -1000, 0);
    for (let i = peepData.length; i < peepMesh.count; i++) {
        peepMesh.setMatrixAt(i, hideMatrix);
    }

    peepMesh.instanceMatrix.needsUpdate = true;
    if (peepMesh.instanceColor) peepMesh.instanceColor.needsUpdate = true;
}

// --- WebSocket ---
function connect() {
    const ws = new WebSocket(`ws://${location.host}/ws`);

    ws.onopen = () => {
        statusEl.style.display = 'none';
        hudEl.style.display = 'block';
    };

    ws.onclose = () => {
        statusEl.innerHTML = '<span class="dot" style="background:#f38ba8;animation:none"></span>Disconnected. Reconnecting...';
        statusEl.style.display = 'block';
        setTimeout(connect, 2000);
    };

    ws.onmessage = (e) => {
        const msg = JSON.parse(e.data);

        if (msg.type === 'snapshot') {
            snapshotData = msg.data;
            if (!mapBuilt) {
                buildCity(msg.data);
                initPeeps(msg.data.peeps.length);
                mapBuilt = true;
            } else {
                // Rebuild on generate
                buildCity(msg.data);
                initPeeps(msg.data.peeps.length);
            }
            // Update peep positions from snapshot
            prevPeepData = peepData;
            peepData = msg.data.peeps.map(p => ({
                id: p.id, x: p.posX, y: p.posY,
                hunger: p.hunger, fatigue: p.fatigue
            }));
            lastPeepTime = performance.now();
            updateHUD(msg.data.tick, msg.data.peeps.length);
        }

        if (msg.type === 'peeps') {
            prevPeepData = peepData;
            peepData = msg.peeps;
            lastPeepTime = performance.now();
            updateHUD(msg.tick, msg.peeps.length);
        }
    };

    // Keyboard commands
    window.onkeydown = (e) => {
        if (e.code === 'Space') {
            e.preventDefault();
            paused = !paused;
            ws.send(JSON.stringify({ type: 'command', action: paused ? 'pause' : 'resume' }));
            pausedLabel.style.display = paused ? 'block' : 'none';
        }
        if (e.code === 'KeyN') {
            mapBuilt = false;
            ws.send(JSON.stringify({ type: 'command', action: 'generate' }));
        }
        if (e.code === 'Equal' || e.code === 'NumpadAdd') {
            tickDelayMs = Math.max(10, tickDelayMs - 10);
            ws.send(JSON.stringify({ type: 'command', action: 'setSpeed', value: tickDelayMs }));
        }
        if (e.code === 'Minus' || e.code === 'NumpadSubtract') {
            tickDelayMs = Math.min(1000, tickDelayMs + 10);
            ws.send(JSON.stringify({ type: 'command', action: 'setSpeed', value: tickDelayMs }));
        }
    };
}

connect();

// --- Render loop ---
function animate() {
    requestAnimationFrame(animate);
    controls.update();
    updatePeeps(true);
    renderer.render(scene, camera);
}
animate();

// --- Resize ---
window.addEventListener('resize', () => {
    camera.aspect = window.innerWidth / window.innerHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(window.innerWidth, window.innerHeight);
});
