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
}

// --- Peep rendering ---
function initPeeps(count) {
    if (peepMesh) scene.remove(peepMesh);
    const geo = new THREE.SphereGeometry(6, 8, 6);
    const mat = new THREE.MeshPhongMaterial({ color: 0xffffff, emissive: 0x333333 });
    peepMesh = new THREE.InstancedMesh(geo, mat, Math.max(count, 1));
    peepMesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
    peepMesh.frustumCulled = false;
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

// --- Inspector ---
const inspectorEl = document.getElementById('inspector');
const inspectorTitle = document.getElementById('inspector-title');
const inspectorBody = document.getElementById('inspector-body');
let selectedPeepId = null;
let selectedBuildingId = null;
let selectionRing = null;

function showPeepInspector(peepId) {
    if (!snapshotData) return;
    const peep = snapshotData.peeps.find(p => p.id === peepId);
    if (!peep) return;
    selectedPeepId = peepId;
    selectedBuildingId = null;
    inspectorTitle.textContent = peep.name || `Peep #${peep.id}`;
    inspectorBody.innerHTML = `
        <div class="row"><span class="label">Age</span><span>${peep.age}</span></div>
        <div class="row"><span class="label">Gender</span><span>${peep.gender}</span></div>
        <div class="row"><span class="label">Money</span><span>$${peep.money.toFixed(1)}</span></div>
        <div class="row"><span class="label">Home</span><span>${peep.homeId != null ? '#' + peep.homeId : 'None'}</span></div>
        <div class="row"><span class="label">Job</span><span>${peep.jobId != null ? '#' + peep.jobId : 'None'}</span></div>
        <div class="row"><span class="label">Brain</span><span>${peep.brainType}</span></div>
        ${needBar('Hunger', peep.hunger, '#f38ba8')}
        ${needBar('Fatigue', peep.fatigue, '#89b4fa')}
        ${needBar('Social', peep.social, '#a6e3a1')}
        ${needBar('Entertainment', peep.entertainment, '#f9e2af')}
        ${needBar('Shelter', peep.shelter, '#cba6f7')}
    `;
    inspectorEl.style.display = 'block';
    updateSelectionRing(peep.posX, peep.posY);
}

function showBuildingInspector(buildingId) {
    if (!snapshotData) return;
    const bld = snapshotData.map.buildings.find(b => b.id === buildingId);
    if (!bld) return;
    selectedPeepId = null;
    selectedBuildingId = buildingId;
    const residents = snapshotData.peeps.filter(p => p.homeId === buildingId);
    const workers = snapshotData.peeps.filter(p => p.jobId === buildingId);
    inspectorTitle.textContent = `${bld.type} #${bld.id}`;
    inspectorBody.innerHTML = `
        <div class="row"><span class="label">Type</span><span>${bld.type}</span></div>
        <div class="row"><span class="label">Cells</span><span>${bld.cells.length}</span></div>
        <div class="row"><span class="label">Residents</span><span>${residents.length}</span></div>
        <div class="row"><span class="label">Workers</span><span>${workers.length}</span></div>
        ${residents.length > 0 ? '<div style="margin-top:6px;color:#a6adc8">Residents:</div>' + residents.map(p => `<div>  ${p.name || 'Peep #' + p.id}</div>`).join('') : ''}
        ${workers.length > 0 ? '<div style="margin-top:6px;color:#a6adc8">Workers:</div>' + workers.map(p => `<div>  ${p.name || 'Peep #' + p.id}</div>`).join('') : ''}
    `;
    inspectorEl.style.display = 'block';
    const cell = bld.cells[0];
    if (cell) updateSelectionRing(cell.x, cell.y);
}

function clearInspector() {
    selectedPeepId = null;
    selectedBuildingId = null;
    inspectorEl.style.display = 'none';
    if (selectionRing) { scene.remove(selectionRing); selectionRing = null; }
}

function needBar(label, value, color) {
    const pct = Math.min(value * 100, 100).toFixed(0);
    return `
        <div class="row"><span class="label">${label}</span><span>${pct}%</span></div>
        <div class="bar-wrap"><div class="bar" style="width:${pct}%;background:${color}"></div></div>
    `;
}

function updateSelectionRing(cellX, cellY) {
    if (selectionRing) scene.remove(selectionRing);
    const geo = new THREE.RingGeometry(14, 16, 24);
    geo.rotateX(-Math.PI / 2);
    const mat = new THREE.MeshBasicMaterial({ color: 0xffffff, side: THREE.DoubleSide });
    selectionRing = new THREE.Mesh(geo, mat);
    selectionRing.position.set(cellX * CS + CS / 2, TERRAIN_H + 0.5, cellY * CS + CS / 2);
    scene.add(selectionRing);
}

// Update inspector data each snapshot
function refreshInspector() {
    if (selectedPeepId != null) showPeepInspector(selectedPeepId);
    else if (selectedBuildingId != null) showBuildingInspector(selectedBuildingId);
}

// --- Raycasting ---
const raycaster = new THREE.Raycaster();
const mouse = new THREE.Vector2();

renderer.domElement.addEventListener('click', (e) => {
    mouse.x = (e.clientX / window.innerWidth) * 2 - 1;
    mouse.y = -(e.clientY / window.innerHeight) * 2 + 1;
    raycaster.setFromCamera(mouse, camera);

    // Check peeps first
    if (peepMesh) {
        const hits = raycaster.intersectObject(peepMesh);
        if (hits.length > 0 && hits[0].instanceId != null) {
            const idx = hits[0].instanceId;
            if (idx < peepData.length) {
                showPeepInspector(peepData[idx].id);
                return;
            }
        }
    }

    // Check buildings
    if (buildingGroup && snapshotData) {
        const hits = raycaster.intersectObjects(buildingGroup.children, true);
        if (hits.length > 0) {
            // Find which building was hit by world position → cell coord
            const pt = hits[0].point;
            const cx = Math.floor(pt.x / CS);
            const cy = Math.floor(pt.z / CS);
            const cell = snapshotData.map.cells.find(c => c.x === cx && c.y === cy && c.buildingId != null);
            if (cell) {
                showBuildingInspector(cell.buildingId);
                return;
            }
        }
    }

    clearInspector();
});

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
                // Center camera on map on first load
                const cx = msg.data.map.width * CS / 2;
                const cz = msg.data.map.height * CS / 2;
                controls.target.set(cx, 0, cz);
                camera.position.set(cx + 300, 400, cz + 300);
                controls.update();
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
            refreshInspector();
        }

        if (msg.type === 'peeps') {
            prevPeepData = peepData;
            peepData = msg.peeps;
            lastPeepTime = performance.now();
            updateHUD(msg.tick, msg.peeps.length);
            // Update selection ring for tracked peep
            if (selectedPeepId != null) {
                const p = peepData.find(pp => pp.id === selectedPeepId);
                if (p) updateSelectionRing(p.x, p.y);
            }
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
