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

// Subtype colors (matching plan spec)
const BUILDING_COLORS = {
    // Subtypes
    House:           new THREE.Color(0.40, 0.75, 0.35),
    Apartment:       new THREE.Color(0.30, 0.65, 0.25),
    Luxury:          new THREE.Color(0.20, 0.85, 0.40),
    Restaurant:      new THREE.Color(0.20, 0.50, 0.90),
    GroceryStore:    new THREE.Color(0.30, 0.60, 0.85),
    Cafe:            new THREE.Color(0.25, 0.55, 0.95),
    Shop:            new THREE.Color(0.35, 0.65, 0.80),
    Office:          new THREE.Color(0.15, 0.40, 0.80),
    Factory:         new THREE.Color(0.80, 0.70, 0.20),
    Warehouse:       new THREE.Color(0.70, 0.60, 0.15),
    Workshop:        new THREE.Color(0.90, 0.80, 0.30),
    Hospital:        new THREE.Color(0.70, 0.45, 0.75),
    School:          new THREE.Color(0.60, 0.35, 0.70),
    Library:         new THREE.Color(0.55, 0.30, 0.65),
    CommunityCenter: new THREE.Color(0.65, 0.40, 0.80),
    Park:            new THREE.Color(0.45, 0.80, 0.35),
    Gym:             new THREE.Color(0.55, 0.70, 0.30),
    Theater:         new THREE.Color(0.50, 0.75, 0.25),
    Stadium:         new THREE.Color(0.60, 0.85, 0.40),
    Museum:          new THREE.Color(0.40, 0.65, 0.20),
    // Category fallbacks
    Residential:     new THREE.Color(0.80, 0.50, 0.20),
    Commercial:      new THREE.Color(0.20, 0.50, 0.90),
    Industrial:      new THREE.Color(0.60, 0.30, 0.30),
    Civic:           new THREE.Color(0.65, 0.40, 0.75),
    Recreation:      new THREE.Color(0.50, 0.75, 0.30),
    Entertainment:   new THREE.Color(0.90, 0.80, 0.10),
};

const BUILDING_HEIGHTS = {
    // Subtypes
    House: 40, Apartment: 80, Luxury: 120,
    Restaurant: 36, GroceryStore: 28, Cafe: 24, Shop: 32, Office: 96,
    Factory: 32, Warehouse: 24, Workshop: 28,
    Hospital: 64, School: 48, Library: 36, CommunityCenter: 40,
    Park: 4, Gym: 32, Theater: 56, Stadium: 80, Museum: 48,
    // Category fallbacks
    Residential: 64, Commercial: 48, Industrial: 32,
    Civic: 48, Recreation: 32, Entertainment: 96,
};

const NEED_COLORS = {
    Hunger:         [1.0, 0.3, 0.3],
    Thirst:         [0.3, 0.7, 1.0],
    Sleep:          [0.3, 0.5, 1.0],
    Warmth:         [1.0, 0.6, 0.2],
    Shelter:        [0.8, 0.65, 1.0],
    Health:         [1.0, 0.4, 0.6],
    Financial:      [0.2, 0.8, 0.2],
    Friendship:     [0.65, 0.9, 0.63],
    Family:         [0.9, 0.7, 0.8],
    Community:      [0.6, 0.8, 0.9],
    Recognition:    [1.0, 0.85, 0.3],
    Accomplishment: [0.9, 0.75, 0.4],
    Status:         [0.85, 0.7, 0.1],
    Creativity:     [0.97, 0.88, 0.68],
    Learning:       [0.7, 0.5, 0.9],
    Purpose:        [0.8, 0.6, 0.95],
};

const MASLOW_LEVELS = [
    { name: 'Physiological', needs: ['Hunger', 'Thirst', 'Sleep', 'Warmth'], color: '#f38ba8' },
    { name: 'Safety', needs: ['Shelter', 'Health', 'Financial'], color: '#fab387' },
    { name: 'Love/Belonging', needs: ['Friendship', 'Family', 'Community'], color: '#a6e3a1' },
    { name: 'Esteem', needs: ['Recognition', 'Accomplishment', 'Status'], color: '#f9e2af' },
    { name: 'Self-Actualization', needs: ['Creativity', 'Learning', 'Purpose'], color: '#cba6f7' },
];

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
let peepData = [];
let prevPeepData = [];
let lastPeepTime = 0;
let paused = false;
let tickDelayMs = 50;
let snapshotData = null;
let currentBrainType = 'Utility';
let ws = null;

// --- HUD ---
const hudEl = document.getElementById('hud');
const clockEl = document.getElementById('clock');
const tickEl = document.getElementById('tick');
const peepCountEl = document.getElementById('peepCount');
const speedEl = document.getElementById('speed');
const pausedLabel = document.getElementById('pausedLabel');
const statusEl = document.getElementById('status');
const brainSelect = document.getElementById('brainSelect');
const genSizeSlider = document.getElementById('genSize');
const genSizeLabel = document.getElementById('genSizeLabel');
const genPeepsSlider = document.getElementById('genPeeps');
const genPeepsLabel = document.getElementById('genPeepsLabel');
const genOrganicSlider = document.getElementById('genOrganic');
const genOrganicLabel = document.getElementById('genOrganicLabel');
const generateBtn = document.getElementById('generateBtn');

function updateHUD(tick, peepCount, hour, minute, day) {
    if (hour !== undefined && minute !== undefined && day !== undefined) {
        const hh = String(hour).padStart(2, '0');
        const mm = String(minute).padStart(2, '0');
        clockEl.textContent = `Day ${day}  ${hh}:${mm}`;
        updateSkyColor(hour);
    }
    tickEl.textContent = tick;
    peepCountEl.textContent = peepCount;
    const speedFactor = Math.round(50 / tickDelayMs * 10) / 10;
    speedEl.textContent = speedFactor + 'x';
    pausedLabel.style.display = paused ? 'block' : 'none';
}

function updateSkyColor(hour) {
    let r, g, b;
    if (hour >= 7 && hour <= 17) {
        // Day: light blue-gray
        r = 0.45; g = 0.52; b = 0.65;
    } else if (hour >= 18 && hour <= 20) {
        // Dusk: warm orange fading to dark
        const t = (hour - 17) / 3;
        r = 0.45 - t * 0.30; g = 0.52 - t * 0.38; b = 0.65 - t * 0.47;
    } else if (hour >= 5 && hour <= 6) {
        // Dawn: dark to warm
        const t = (hour - 4) / 2;
        r = 0.12 + t * 0.33; g = 0.12 + t * 0.40; b = 0.18 + t * 0.47;
    } else {
        // Night: dark blue
        r = 0.06; g = 0.06; b = 0.12;
    }
    scene.background.setRGB(r, g, b);
}

// Slider labels
genSizeSlider.oninput = () => {
    const v = genSizeSlider.value;
    const miles = (v * 50 / 5280).toFixed(2);
    genSizeLabel.textContent = `${v} (${miles}mi)`;
};
genPeepsSlider.oninput = () => { genPeepsLabel.textContent = genPeepsSlider.value; };
genOrganicSlider.oninput = () => { genOrganicLabel.textContent = genOrganicSlider.value + '%'; };

// Brain select
brainSelect.onchange = () => {
    currentBrainType = brainSelect.value;
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'command', action: 'setBrainType', stringValue: currentBrainType }));
    }
};

// Generate button
generateBtn.onclick = () => {
    if (ws && ws.readyState === WebSocket.OPEN) {
        const size = parseInt(genSizeSlider.value);
        const peeps = parseInt(genPeepsSlider.value);
        const organic = parseInt(genOrganicSlider.value) / 100;
        mapBuilt = false;
        ws.send(JSON.stringify({
            type: 'command',
            action: 'generateWithConfig',
            stringValue: `${size},${size},${peeps},${organic}`
        }));
    }
};

// --- Build city geometry from snapshot ---
function getBuildingKey(bld) {
    return bld.subtype || bld.type;
}

function buildCity(data) {
    if (terrainGroup) scene.remove(terrainGroup);
    if (buildingGroup) scene.remove(buildingGroup);

    terrainGroup = new THREE.Group();
    buildingGroup = new THREE.Group();

    const mapData = data.map;

    // Terrain
    const terrainByType = {};
    for (const cell of mapData.cells) {
        if (cell.buildingId != null) continue;
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

    // Buildings — grouped by subtype or type
    const buildingsByKey = {};
    for (const bld of mapData.buildings) {
        const key = getBuildingKey(bld);
        if (!buildingsByKey[key]) buildingsByKey[key] = [];
        buildingsByKey[key].push(bld);
    }

    for (const [key, buildings] of Object.entries(buildingsByKey)) {
        const height = BUILDING_HEIGHTS[key] || 32;
        const color = BUILDING_COLORS[key] || new THREE.Color(0.5, 0.5, 0.5);
        const bldGeo = new THREE.BoxGeometry(CS, height, CS);
        const mat = new THREE.MeshLambertMaterial({ color, transparent: true, opacity: 0.55 });

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

        // Color based on top need
        const needColor = p.topNeed && p.topNeedValue > 0.3 ? NEED_COLORS[p.topNeed] : null;
        if (needColor) {
            color.setRGB(needColor[0], needColor[1], needColor[2]);
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

    let html = `
        <div class="row"><span class="label">Age</span><span>${peep.age}</span></div>
        <div class="row"><span class="label">Gender</span><span>${peep.gender}</span></div>
        <div class="row"><span class="label">Money</span><span>$${peep.money.toFixed(1)}</span></div>
        <div class="row"><span class="label">Home</span><span>${peep.homeId != null ? '#' + peep.homeId : 'None'}</span></div>
        <div class="row"><span class="label">Job</span><span>${peep.jobId != null ? '#' + peep.jobId : 'None'}</span></div>
        <div class="row"><span class="label">Brain</span><span>${peep.brainType}</span></div>
    `;

    // Maslow needs grouped by level
    const m = peep.maslowNeeds;
    if (m) {
        const needValues = {
            Hunger: m.hunger, Thirst: m.thirst, Sleep: m.sleep, Warmth: m.warmth,
            Shelter: m.shelter, Health: m.health,
            Friendship: m.friendship, Family: m.family, Community: m.community,
            Recognition: m.recognition, Accomplishment: m.accomplishment,
            Creativity: m.creativity, Learning: m.learning, Purpose: m.purpose,
        };

        for (const level of MASLOW_LEVELS) {
            html += `<div class="level-header" style="color:${level.color}">${level.name}</div>`;
            for (const need of level.needs) {
                const val = needValues[need];
                if (val !== undefined) {
                    html += needBar(need, val, level.color);
                }
            }
        }
    } else {
        // v1 fallback
        html += needBar('Hunger', peep.hunger, '#f38ba8');
        html += needBar('Fatigue', peep.fatigue, '#89b4fa');
        html += needBar('Social', peep.social, '#a6e3a1');
        html += needBar('Entertainment', peep.entertainment, '#f9e2af');
        html += needBar('Shelter', peep.shelter, '#cba6f7');
    }

    // Edit sliders
    html += '<div class="level-header" style="color:#a6adc8">Edit Needs</div>';
    html += editSlider('Hunger', m ? m.hunger : peep.hunger, peepId);
    html += editSlider('Sleep', m ? m.sleep : peep.fatigue, peepId);

    // Per-peep brain selector
    html += '<div class="level-header" style="color:#a6adc8">Brain</div>';
    html += `<select class="brain-select" id="peep-brain-select" data-peep-id="${peepId}">
        <option value="Utility" ${peep.brainType === 'Utility' ? 'selected' : ''}>Utility</option>
        <option value="Pyramid" ${peep.brainType === 'Pyramid' ? 'selected' : ''}>Pyramid</option>
        <option value="Wave" ${peep.brainType === 'Wave' ? 'selected' : ''}>Wave</option>
        <option value="Random" ${peep.brainType === 'Random' ? 'selected' : ''}>Random</option>
        <option value="Idle" ${peep.brainType === 'Idle' ? 'selected' : ''}>Idle</option>
    </select>`;

    inspectorBody.innerHTML = html;
    inspectorEl.style.display = 'block';
    updateSelectionRing(peep.posX, peep.posY);

    // Wire up edit slider events
    inspectorBody.querySelectorAll('.edit-slider').forEach(slider => {
        slider.oninput = () => {
            const need = slider.dataset.need;
            const val_ = parseFloat(slider.value);
            slider.nextElementSibling.textContent = Math.round(val_ * 100) + '%';
            if (ws && ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify({
                    type: 'command', action: 'setNeed',
                    value: peepId,
                    stringValue: `${need},${val_}`
                }));
            }
        };
    });

    // Wire up brain selector
    const brainSel = document.getElementById('peep-brain-select');
    if (brainSel) {
        brainSel.onchange = () => {
            if (ws && ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify({
                    type: 'command', action: 'setPeepBrain',
                    value: peepId,
                    stringValue: brainSel.value
                }));
            }
        };
    }
}

function showBuildingInspector(buildingId) {
    if (!snapshotData) return;
    const bld = snapshotData.map.buildings.find(b => b.id === buildingId);
    if (!bld) return;
    selectedPeepId = null;
    selectedBuildingId = buildingId;
    const residents = snapshotData.peeps.filter(p => p.homeId === buildingId);
    const workers = snapshotData.peeps.filter(p => p.jobId === buildingId);
    const displayName = bld.subtype ? `${bld.subtype}` : bld.type;
    inspectorTitle.textContent = `${displayName} #${bld.id}`;
    inspectorBody.innerHTML = `
        <div class="row"><span class="label">Category</span><span>${bld.type}</span></div>
        ${bld.subtype ? `<div class="row"><span class="label">Subtype</span><span>${bld.subtype}</span></div>` : ''}
        <div class="row"><span class="label">Cells</span><span>${bld.cells.length}</span></div>
        <div class="row"><span class="label">Capacity</span><span>${bld.capacity || '?'}</span></div>
        <div class="row"><span class="label">Occupants</span><span>${bld.occupants || 0}${bld.isFull ? ' <span style="color:#f38ba8">FULL</span>' : ''}</span></div>
        ${bld.wage ? `<div class="row"><span class="label">Wage</span><span>$${bld.wage}/tick</span></div>` : ''}
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

function editSlider(needName, value, peepId) {
    const pct = Math.round(value * 100);
    return `<div class="edit-row">
        <span class="label">${needName}</span>
        <input type="range" class="edit-slider" min="0" max="1" step="0.05" value="${value}" data-need="${needName}" data-peep="${peepId}">
        <span>${pct}%</span>
    </div>`;
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

    if (buildingGroup && snapshotData) {
        const hits = raycaster.intersectObjects(buildingGroup.children, true);
        if (hits.length > 0) {
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
    ws = new WebSocket(`ws://${location.host}/ws`);

    ws.onopen = () => {
        statusEl.style.display = 'none';
        hudEl.style.display = 'block';
        document.getElementById('cmdbar').style.display = 'flex';
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
                const cx = msg.data.map.width * CS / 2;
                const cz = msg.data.map.height * CS / 2;
                controls.target.set(cx, 0, cz);
                camera.position.set(cx + 300, 400, cz + 300);
                controls.update();
                mapBuilt = true;
            } else {
                buildCity(msg.data);
                initPeeps(msg.data.peeps.length);
            }
            prevPeepData = peepData;
            peepData = msg.data.peeps.map(p => ({
                id: p.id, x: p.posX, y: p.posY,
                hunger: p.hunger, fatigue: p.fatigue,
                topNeed: p.maslowNeeds ? getTopNeed(p.maslowNeeds) : null,
                topNeedValue: p.maslowNeeds ? getTopNeedValue(p.maslowNeeds) : 0
            }));
            lastPeepTime = performance.now();
            const cd = msg.data.clock;
            updateHUD(msg.data.tick, msg.data.peeps.length, cd ? cd.hour : undefined, cd ? cd.minute : undefined, cd ? cd.day : undefined);
            refreshInspector();
        }

        if (msg.type === 'peeps') {
            prevPeepData = peepData;
            peepData = msg.peeps;
            lastPeepTime = performance.now();
            updateHUD(msg.tick, msg.peeps.length, msg.hour, msg.minute, msg.day);
            if (selectedPeepId != null) {
                const p = peepData.find(pp => pp.id === selectedPeepId);
                if (p) updateSelectionRing(p.x, p.y);
            }
        }
    };

    // Keyboard commands
    window.onkeydown = (e) => {
        // Don't handle keys when a form element is focused
        if (e.target.tagName === 'SELECT' || e.target.tagName === 'INPUT') return;

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
        if (e.code === 'KeyB') {
            // Cycle brain types
            const types = ['Utility', 'Pyramid', 'Wave', 'Random'];
            const idx = types.indexOf(currentBrainType);
            currentBrainType = types[(idx + 1) % types.length];
            brainSelect.value = currentBrainType;
            ws.send(JSON.stringify({ type: 'command', action: 'setBrainType', stringValue: currentBrainType }));
        }
        if (e.code === 'Escape') {
            clearInspector();
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

function getTopNeed(m) {
    const needs = {
        Hunger: m.hunger, Thirst: m.thirst, Sleep: m.sleep, Warmth: m.warmth,
        Shelter: m.shelter, Health: m.health,
        Friendship: m.friendship, Family: m.family, Community: m.community,
        Recognition: m.recognition, Accomplishment: m.accomplishment,
        Creativity: m.creativity, Learning: m.learning, Purpose: m.purpose,
    };
    let top = null, topVal = 0;
    for (const [k, v] of Object.entries(needs)) {
        if (v > topVal) { top = k; topVal = v; }
    }
    return top;
}

function getTopNeedValue(m) {
    const needs = [m.hunger, m.thirst, m.sleep, m.warmth, m.shelter, m.health,
        m.friendship, m.family, m.community, m.recognition, m.accomplishment,
        m.creativity, m.learning, m.purpose];
    return Math.max(...needs);
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
