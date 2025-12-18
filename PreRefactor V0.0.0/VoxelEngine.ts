/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
*/


import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls';
import { AppState, SimulationVoxel, RebuildTarget, VoxelData } from '../types';
import { CONFIG, COLORS } from '../utils/voxelConstants';

// Physics constants
const TICK_RATE = 1000 / 30; // 30 Ticks/sec for smoother physics
const GRAVITY = 0.2;
const TERMINAL_VELOCITY = 1.2;
const FLUID_MOMENTUM_RETAIN = 0.94;
const PRESSURE_FORCE = 0.6;

// Bitmask Flags
const N_PX = 1;
const N_NX = 2;
const N_PY = 4;
const N_NY = 8;
const N_PZ = 16;
const N_NZ = 32;

// Spatial Hash Helper (Int32 optimization)
// Offset 512 to handle negative coordinates (valid range -512 to 511)
const HASH_OFFSET = 512;
const hash = (x: number, y: number, z: number) => {
    return ((x + HASH_OFFSET) & 0x3FF) | 
           (((y + HASH_OFFSET) & 0x3FF) << 10) | 
           (((z + HASH_OFFSET) & 0x3FF) << 20);
};

export class VoxelEngine {
  private container: HTMLElement;
  private scene: THREE.Scene;
  private camera: THREE.PerspectiveCamera;
  private renderer: THREE.WebGLRenderer;
  private controls: OrbitControls;
  private instanceMesh: THREE.InstancedMesh | null = null;
  private dummy = new THREE.Object3D();
  
  private voxels: SimulationVoxel[] = [];
  private rebuildTargets: RebuildTarget[] = [];
  private rebuildStartTime: number = 0;
  
  private state: AppState = AppState.STABLE;
  private onStateChange: (state: AppState) => void;
  private onCountChange: (count: number) => void;
  private animationId: number = 0;

  // Fluid Simulation State
  private lastTime: number = 0;
  private tickAccumulator: number = 0;
  private temperature: number = 20; // Celsius
  private precipitationIntensity: number = 0; // 0 to 100
  private weatherTickCounter: number = 0;

  constructor(
    container: HTMLElement, 
    onStateChange: (state: AppState) => void,
    onCountChange: (count: number) => void
  ) {
    this.container = container;
    this.onStateChange = onStateChange;
    this.onCountChange = onCountChange;

    // Init Three.js
    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(CONFIG.BG_COLOR);
    this.scene.fog = new THREE.Fog(CONFIG.BG_COLOR, 60, 140);

    this.camera = new THREE.PerspectiveCamera(45, window.innerWidth / window.innerHeight, 0.1, 1000);
    this.camera.position.set(30, 30, 60);

    this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false });
    this.renderer.setSize(window.innerWidth, window.innerHeight);
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    container.appendChild(this.renderer.domElement);

    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;
    this.controls.autoRotate = true;
    this.controls.autoRotateSpeed = 0.5;
    this.controls.target.set(0, 5, 0);

    // Lights
    const ambientLight = new THREE.AmbientLight(0xffffff, 0.7);
    this.scene.add(ambientLight);

    const dirLight = new THREE.DirectionalLight(0xffffff, 1.5);
    dirLight.position.set(50, 80, 30);
    dirLight.castShadow = true;
    dirLight.shadow.mapSize.width = 2048;
    dirLight.shadow.mapSize.height = 2048;
    dirLight.shadow.camera.left = -40;
    dirLight.shadow.camera.right = 40;
    dirLight.shadow.camera.top = 40;
    dirLight.shadow.camera.bottom = -40;
    this.scene.add(dirLight);

    // Floor
    const planeMat = new THREE.MeshStandardMaterial({ color: 0xe2e8f0, roughness: 1 });
    const floor = new THREE.Mesh(new THREE.PlaneGeometry(200, 200), planeMat);
    floor.rotation.x = -Math.PI / 2;
    floor.position.y = CONFIG.FLOOR_Y;
    floor.receiveShadow = true;
    this.scene.add(floor);

    this.lastTime = performance.now();
    this.animate = this.animate.bind(this);
    this.animate();
  }

  public loadInitialModel(data: VoxelData[]) {
    this.createVoxels(data);
    this.onCountChange(this.voxels.length);
    this.state = AppState.STABLE;
    this.onStateChange(this.state);
  }

  private createVoxels(data: VoxelData[]) {
    if (this.instanceMesh) {
      this.scene.remove(this.instanceMesh);
      this.instanceMesh.geometry.dispose();
      if (Array.isArray(this.instanceMesh.material)) {
          this.instanceMesh.material.forEach(m => m.dispose());
      } else {
          this.instanceMesh.material.dispose();
      }
    }

    this.voxels = data.map((v, i) => {
        const c = new THREE.Color(v.color);
        if (v.type === 'solid' && v.color !== COLORS.GLASS) {
            c.offsetHSL(0, 0, (Math.random() * 0.1) - 0.05);
        }
        
        return {
            id: i,
            x: v.x, y: v.y, z: v.z,
            gridX: Math.round(v.x), gridY: Math.round(v.y), gridZ: Math.round(v.z),
            prevGridX: Math.round(v.x), prevGridY: Math.round(v.y), prevGridZ: Math.round(v.z),
            color: c,
            type: v.type || 'solid',
            vx: 0, vy: 0, vz: 0, rx: 0, ry: 0, rz: 0,
            rvx: 0, rvy: 0, rvz: 0,
            neighbors: 0,
            pressure: 0
        };
    });

    const maxParticles = Math.max(this.voxels.length + 15000, 30000); 
    const geometry = new THREE.BoxGeometry(1.0, 1.0, 1.0); 
    const material = new THREE.MeshStandardMaterial({ 
        roughness: 0.1, 
        metalness: 0.1,
        transparent: true,
        opacity: 0.95
    });

    this.instanceMesh = new THREE.InstancedMesh(geometry, material, maxParticles);
    this.instanceMesh.count = this.voxels.length;
    this.instanceMesh.castShadow = true;
    this.instanceMesh.receiveShadow = true;
    this.scene.add(this.instanceMesh);

    this.draw(0);
  }

  private draw(interpolationAlpha: number) {
    if (!this.instanceMesh) return;
    
    if (this.instanceMesh.count !== this.voxels.length) {
        this.instanceMesh.count = this.voxels.length;
        this.onCountChange(this.voxels.length);
    }

    const isFluid = this.state === AppState.FLUID;
    const time = performance.now();
    
    const colorWater = new THREE.Color(COLORS.WATER);
    const colorFoam = new THREE.Color(COLORS.WATER).lerp(new THREE.Color(0xFFFFFF), 0.5);
    const colorIce = new THREE.Color(COLORS.ICE);
    const colorSteam = new THREE.Color(COLORS.STEAM);
    const colorSnow = new THREE.Color(COLORS.SNOW);
    const colorHail = new THREE.Color(COLORS.HAIL);
    const colorDeepWater = new THREE.Color(0x1e3a8a); // Darker blue for pressure depth

    this.voxels.forEach((v, i) => {
        let px, py, pz;
        let scaleX = 1, scaleY = 1, scaleZ = 1;

        if (isFluid && v.type !== 'solid') {
            // Linear Interpolation
            px = v.prevGridX + (v.gridX - v.prevGridX) * interpolationAlpha;
            py = v.prevGridY + (v.gridY - v.prevGridY) * interpolationAlpha;
            pz = v.prevGridZ + (v.gridZ - v.prevGridZ) * interpolationAlpha;

            const isSteam = v.type === 'steam' || this.temperature >= 100;

            if (isSteam) {
                scaleX = scaleY = scaleZ = 0.5 + Math.random() * 0.3;
            } else if (v.type === 'snow') {
                scaleX = scaleZ = 0.9;
                scaleY = 0.7;
            } else if (v.type === 'hail') {
                scaleX = scaleY = scaleZ = 0.5;
            } else {
                // WATER / ICE MESH DEFORMATION VISUALIZATION
                
                const n = v.neighbors;
                
                // Base "Droplet" size
                scaleX = 0.85; scaleY = 0.85; scaleZ = 0.85;
                
                // Wave Effect for surface water
                if (!(n & N_PY)) {
                    // No neighbor above = surface
                    const waveHeight = 0.12;
                    const waveFreq = 0.6;
                    const waveSpeed = 0.003;
                    const waveOffset = Math.sin(px * waveFreq + pz * waveFreq * 0.5 + time * waveSpeed) * waveHeight;
                    py += waveOffset;
                }

                // X Axis Connection
                if ((n & N_PX) && (n & N_NX)) {
                    scaleX = 1.05; // Fully connected
                } else if (n & N_PX) {
                    scaleX = 0.95; px += 0.05; // Reach Right
                } else if (n & N_NX) {
                    scaleX = 0.95; px -= 0.05; // Reach Left
                }

                // Y Axis Connection
                if ((n & N_PY) && (n & N_NY)) {
                    scaleY = 1.05; 
                } else if (n & N_PY) {
                    scaleY = 0.95; py += 0.05;
                } else if (n & N_NY) {
                    scaleY = 0.95; py -= 0.05;
                }

                // Z Axis Connection
                if ((n & N_PZ) && (n & N_NZ)) {
                    scaleZ = 1.05;
                } else if (n & N_PZ) {
                    scaleZ = 0.95; pz += 0.05;
                } else if (n & N_NZ) {
                    scaleZ = 0.95; pz -= 0.05;
                }

                // High velocity stretch
                if (Math.abs(v.vy) > 0.4) {
                    scaleY = Math.min(1.5, 0.8 + Math.abs(v.vy));
                    scaleX *= 0.7;
                    scaleZ *= 0.7;
                }
            }
        } else {
            px = v.x; py = v.y; pz = v.z;
            scaleX = scaleY = scaleZ = 1.0;
        }

        this.dummy.position.set(px, py, pz);
        this.dummy.rotation.set(v.rx, v.ry, v.rz);
        this.dummy.scale.set(scaleX, scaleY, scaleZ);
        
        if (v.type !== 'solid') {
            let finalColor = colorWater.clone();
            
            if (v.type === 'snow') {
                finalColor = colorSnow;
            } else if (v.type === 'hail') {
                finalColor = colorHail;
            } else {
                if (this.temperature <= 0) {
                    finalColor = colorIce;
                    this.dummy.scale.set(1.0, 1.0, 1.0);
                } else if (this.temperature >= 100 || v.type === 'steam') {
                    finalColor = colorSteam;
                } else if (this.temperature < 20) {
                    finalColor.lerpColors(colorIce, colorWater, this.temperature / 20);
                } else {
                    // Standard Water Logic
                    
                    // Apply Pressure Depth Coloring (Darker at bottom)
                    if (v.pressure > 0) {
                        const pressureDarkness = Math.min(v.pressure * 0.08, 0.7);
                        finalColor.lerp(colorDeepWater, pressureDarkness);
                    }

                    // Temperature shift to steam
                    if (this.temperature > 80) {
                        finalColor.lerpColors(finalColor, colorSteam, (this.temperature - 80) / 20);
                    }

                    const velocity = Math.abs(v.vx) + Math.abs(v.vy) + Math.abs(v.vz);
                    if (velocity > 0.8 && this.temperature > 0 && this.temperature < 100 && v.type !== 'steam') {
                        finalColor.lerp(colorFoam, 0.4);
                    }
                }
            }
            this.instanceMesh!.setColorAt(i, finalColor);
            this.dummy.updateMatrix();
            this.instanceMesh!.setMatrixAt(i, this.dummy.matrix);

        } else {
            this.instanceMesh!.setColorAt(i, v.color);
            this.instanceMesh!.setMatrixAt(i, this.dummy.matrix);
        }
    });
    
    this.instanceMesh.instanceMatrix.needsUpdate = true;
    this.instanceMesh.instanceColor!.needsUpdate = true;
  }

  public dismantle() {
    if (this.state !== AppState.STABLE) return;
    this.voxels.forEach(v => {
        v.x = v.gridX; v.y = v.gridY; v.z = v.gridZ;
    });

    this.state = AppState.DISMANTLING;
    this.onStateChange(this.state);

    this.voxels.forEach(v => {
        v.vx = (Math.random() - 0.5) * 0.8;
        v.vy = Math.random() * 0.5;
        v.vz = (Math.random() - 0.5) * 0.8;
        v.rvx = (Math.random() - 0.5) * 0.2;
        v.rvy = (Math.random() - 0.5) * 0.2;
        v.rvz = (Math.random() - 0.5) * 0.2;
    });
  }
  
  public startFluidSim() {
      if (this.state === AppState.FLUID) {
          this.state = AppState.STABLE;
          this.voxels.forEach(v => {
              v.x = v.gridX; v.y = v.gridY; v.z = v.gridZ;
          });
      } else {
          this.voxels.forEach(v => {
              v.prevGridX = v.gridX;
              v.prevGridY = v.gridY;
              v.prevGridZ = v.gridZ;
              v.pressure = 0;
              if (v.type !== 'solid') {
                  v.vx = 0; v.vy = 0; v.vz = 0;
              }
          });
          this.tickAccumulator = 0;
          this.state = AppState.FLUID;
      }
      this.onStateChange(this.state);
  }

  public setTemperature(temp: number) {
      this.temperature = temp;
  }
  
  public setPrecipitation(val: number) {
      this.precipitationIntensity = val;
  }

  private getColorDist(c1: THREE.Color, hex2: number): number {
    const c2 = new THREE.Color(hex2);
    const r = (c1.r - c2.r) * 0.3;
    const g = (c1.g - c2.g) * 0.59;
    const b = (c1.b - c2.b) * 0.11;
    return Math.sqrt(r * r + g * g + b * b);
  }

  public rebuild(targetModel: VoxelData[]) {
    if (this.state === AppState.REBUILDING) return;

    const available = this.voxels.map((v, i) => ({ index: i, color: v.color, taken: false }));
    const mappings: RebuildTarget[] = new Array(this.voxels.length).fill(null);

    targetModel.forEach(target => {
        let bestDist = 9999;
        let bestIdx = -1;

        for (let i = 0; i < available.length; i++) {
            if (available[i].taken) continue;

            const d = this.getColorDist(available[i].color, target.color);
            if (d < bestDist) {
                bestDist = d;
                bestIdx = i;
                if (d < 0.01) break;
            }
        }

        if (bestIdx !== -1) {
            available[bestIdx].taken = true;
            const h = Math.max(0, (target.y - CONFIG.FLOOR_Y) / 15);
            mappings[available[bestIdx].index] = {
                x: target.x, y: target.y, z: target.z,
                delay: h * 800
            };
        }
    });

    for (let i = 0; i < this.voxels.length; i++) {
        if (!mappings[i]) {
            mappings[i] = {
                x: this.voxels[i].x, y: this.voxels[i].y, z: this.voxels[i].z,
                isRubble: true, delay: 0
            };
        }
    }

    this.rebuildTargets = mappings;
    this.rebuildStartTime = Date.now();
    this.state = AppState.REBUILDING;
    this.onStateChange(this.state);
  }

  private spawnWeather() {
      if (this.precipitationIntensity <= 0) return;
      if (this.temperature >= 100) return;

      const threshold = 15 - Math.floor(this.precipitationIntensity / 8);
      this.weatherTickCounter++;
      
      if (this.weatherTickCounter >= Math.max(1, threshold)) {
          this.weatherTickCounter = 0;
          
          const area = 24;
          const spawnX = Math.floor(Math.random() * area * 2) - area;
          const spawnZ = Math.floor(Math.random() * area * 2) - area;
          const spawnY = 30; // High sky

          let type: 'water' | 'snow' | 'hail' = 'water';
          
          if (this.temperature <= 0) type = 'snow';
          else if (this.temperature > 0 && this.temperature < 15) type = 'hail';
          else type = 'water';

          const color = type === 'snow' ? new THREE.Color(COLORS.SNOW) : 
                        type === 'hail' ? new THREE.Color(COLORS.HAIL) : 
                        new THREE.Color(COLORS.WATER);
          
          const addVoxel = (ox:number, oy:number, oz:number) => {
              this.voxels.push({
                  id: this.voxels.length,
                  x: ox, y: oy, z: oz,
                  gridX: ox, gridY: oy, gridZ: oz,
                  prevGridX: ox, prevGridY: oy, prevGridZ: oz,
                  color: color,
                  type: type,
                  vx: 0, vy: 0, vz: 0, rx: 0, ry: 0, rz: 0, rvx: 0, rvy: 0, rvz: 0,
                  neighbors: 0,
                  pressure: 0
              });
          };

          if (type === 'snow') {
             addVoxel(spawnX, spawnY, spawnZ);
             addVoxel(spawnX, spawnY+1, spawnZ);
          } else {
             addVoxel(spawnX, spawnY, spawnZ);
          }
      }
  }

  private runWaterTick() {
    this.spawnWeather();

    const isGlobalSteam = this.temperature >= 100;
    const isFreezing = this.temperature <= 0;
    const isHailTemp = this.temperature > 0 && this.temperature < 15;

    // Use optimized Int32 keys
    const gridMap = new Map<number, SimulationVoxel>();
    const fluids: SimulationVoxel[] = [];

    // Spatial Hash Build
    for (const v of this.voxels) {
        gridMap.set(hash(v.gridX, v.gridY, v.gridZ), v);
        if (v.type !== 'solid') {
            fluids.push(v);
        }
    }

    // Shuffle for randomness
    for (let i = fluids.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [fluids[i], fluids[j]] = [fluids[j], fluids[i]];
    }

    for (const v of fluids) {
        // --- Phase Change & Type Logic ---
        if (v.type === 'snow' && this.temperature > 0) {
            v.type = 'water'; 
        } else if (v.type === 'hail' && this.temperature > 15) {
            // Hail melts into water if hot enough
            v.type = 'water';
        } else if (v.type === 'water') {
            if (isFreezing) {
                // Freeze check neighbors
                let nC = 0;
                if (gridMap.has(hash(v.gridX+1, v.gridY, v.gridZ))) nC++;
                if (gridMap.has(hash(v.gridX-1, v.gridY, v.gridZ))) nC++;
                if (gridMap.has(hash(v.gridX, v.gridY-1, v.gridZ))) nC++;
                if (nC >= 2) {
                    v.vx = 0; v.vy = 0; v.vz = 0; // Freeze in place
                    continue; // Skip movement
                }
                v.type = 'snow'; // Turn to snow/slush if falling
            } else if (isHailTemp && Math.random() < 0.01) {
                v.type = 'hail';
            } else if (isGlobalSteam) {
                v.type = 'steam';
            }
        } else if ((v.type as string) === 'steam' && !isGlobalSteam) {
             v.type = 'water';
        }

        v.prevGridX = v.gridX;
        v.prevGridY = v.gridY;
        v.prevGridZ = v.gridZ;
        
        // Remove self from map to allow movement
        gridMap.delete(hash(v.gridX, v.gridY, v.gridZ));

        // --- Pressure Calculation ---
        // Look up to 10 blocks above to calculate head pressure
        let pressure = 0;
        let checkY = v.gridY + 1;
        for (let k = 0; k < 10; k++) {
            const aboveKey = hash(v.gridX, checkY, v.gridZ);
            const aboveVoxel = gridMap.get(aboveKey);
            if (aboveVoxel && aboveVoxel.type === 'water') {
                pressure++;
                checkY++;
            } else {
                break;
            }
        }
        v.pressure = pressure;

        // --- Neighbor Calculation for Mesh Deformation (Bitmask) ---
        let neighbors = 0;
        if (gridMap.has(hash(v.gridX+1, v.gridY, v.gridZ))) neighbors |= N_PX;
        if (gridMap.has(hash(v.gridX-1, v.gridY, v.gridZ))) neighbors |= N_NX;
        if (gridMap.has(hash(v.gridX, v.gridY+1, v.gridZ))) neighbors |= N_PY;
        if (gridMap.has(hash(v.gridX, v.gridY-1, v.gridZ))) neighbors |= N_NY;
        if (gridMap.has(hash(v.gridX, v.gridY, v.gridZ+1))) neighbors |= N_PZ;
        if (gridMap.has(hash(v.gridX, v.gridY, v.gridZ-1))) neighbors |= N_NZ;
        v.neighbors = neighbors;

        // --- Physics Movement ---

        // Vertical
        if (v.type === 'steam') {
            v.vy += 0.08; 
            v.vx += (Math.random() - 0.5) * 0.4;
            v.vz += (Math.random() - 0.5) * 0.4;
        } else {
            v.vy -= GRAVITY;
            if (v.vy < -TERMINAL_VELOCITY) v.vy = -TERMINAL_VELOCITY;
        }

        let nextY = v.gridY;
        if (Math.abs(v.vy) >= 0.5) {
             nextY += Math.sign(v.vy);
        }

        // Collision Y
        let collidedY = false;
        if (nextY <= CONFIG.FLOOR_Y) {
            nextY = CONFIG.FLOOR_Y + 1;
            collidedY = true;
            
            // Bounce logic for Hail
            if (v.type === 'hail') {
                v.vy *= -0.6; // Bouncy
                v.vx += (Math.random() - 0.5) * 0.5; // Scatter
                v.vz += (Math.random() - 0.5) * 0.5;
            } else {
                v.vy = 0;
            }
        } else {
            const blocked = gridMap.get(hash(v.gridX, nextY, v.gridZ));
            if (blocked) {
                if (v.type !== 'steam' && blocked.type === 'water') {
                    // Spread force based on impact and PRESSURE
                    let spreadForce = PRESSURE_FORCE;
                    if (v.pressure > 0) spreadForce += (v.pressure * 0.15); // Higher pressure = more spread

                    const spread = Math.abs(v.vy) * spreadForce;
                    if (Math.random() > 0.5) v.vx += (Math.random()-0.5)*spread;
                    else v.vz += (Math.random()-0.5)*spread;
                }
                nextY = v.gridY;
                collidedY = true;
                
                if (v.type === 'hail') {
                    v.vy *= -0.5;
                    v.vx += (Math.random() - 0.5) * 0.4;
                    v.vz += (Math.random() - 0.5) * 0.4;
                } else {
                    v.vy = 0;
                }
            }
        }
        
        // Horizontal Flow
        if (v.type === 'water') {
            v.vx *= FLUID_MOMENTUM_RETAIN;
            v.vz *= FLUID_MOMENTUM_RETAIN;

            // Pressure & Flow
            if (collidedY || gridMap.has(hash(v.gridX, v.gridY-1, v.gridZ))) {
                
                // Pressure push from above
                if (v.pressure > 0) {
                     // If we have pressure, we REALLY want to move to an empty spot
                     // Move randomly but forcefully away from stack
                     const dir = Math.random() > 0.5 ? 1 : -1;
                     const pressureBoost = 0.2 * v.pressure;
                     
                     if (Math.random() > 0.5) v.vx += dir * (0.5 + pressureBoost);
                     else v.vz += dir * (0.5 + pressureBoost);
                }

                // Seek Lowest / Flow logic
                const neighborsDiff = [
                    {x:1, z:0}, {x:-1, z:0}, {x:0, z:1}, {x:0, z:-1},
                    {x:1, z:1}, {x:1, z:-1}, {x:-1, z:1}, {x:-1, z:-1}
                ];

                let bestDir = null;

                for (const n of neighborsDiff) {
                    // Check if drop exists (Hole)
                    if (!gridMap.has(hash(v.gridX+n.x, v.gridY-1, v.gridZ+n.z))) {
                        bestDir = n; // High priority: Fall down hole
                        break;
                    }
                    // Check if space exists on same level
                    if (!gridMap.has(hash(v.gridX+n.x, v.gridY, v.gridZ+n.z))) {
                        if (!bestDir) bestDir = n;
                    }
                }

                if (bestDir) {
                    const flowSpeed = 0.15 + (v.pressure * 0.05); // Faster flow under pressure
                    v.vx += bestDir.x * flowSpeed;
                    v.vz += bestDir.z * flowSpeed;
                }
            }
        } else if (v.type === 'hail') {
            v.vx *= 0.9; // Friction
            v.vz *= 0.9;
        }

        // Apply Horizontal
        let nextX = v.gridX;
        let nextZ = v.gridZ;

        if (Math.abs(v.vx) > 0.3) nextX += Math.sign(v.vx);
        if (Math.abs(v.vz) > 0.3) nextZ += Math.sign(v.vz);

        if (nextX !== v.gridX || nextZ !== v.gridZ) {
            const blocked = gridMap.get(hash(nextX, nextY, nextZ));
            if (blocked) {
                 v.vx *= -0.5;
                 v.vz *= -0.5;
                 nextX = v.gridX;
                 nextZ = v.gridZ;
            }
        }

        v.gridX = nextX;
        v.gridY = nextY;
        v.gridZ = nextZ;

        gridMap.set(hash(v.gridX, v.gridY, v.gridZ), v);
    }
  }

  private updateDismantlePhysics() {
      this.voxels.forEach(v => {
        v.vy -= 0.025; 
        v.x += v.vx; v.y += v.vy; v.z += v.vz;
        v.rx += v.rvx; v.ry += v.rvy; v.rz += v.rvz;

        if (v.y < CONFIG.FLOOR_Y + 0.5) {
            v.y = CONFIG.FLOOR_Y + 0.5;
            v.vy *= -0.5; v.vx *= 0.9; v.vz *= 0.9;
            v.rvx *= 0.8; v.rvy *= 0.8; v.rvz *= 0.8;
        }
    });
  }

  private updateRebuildPhysics() {
    const now = Date.now();
    const elapsed = now - this.rebuildStartTime;
    let allDone = true;

    this.voxels.forEach((v, i) => {
        const t = this.rebuildTargets[i];
        if (t.isRubble) return;

        if (elapsed < t.delay) {
            allDone = false;
            return;
        }

        const speed = 0.12;
        v.x += (t.x - v.x) * speed;
        v.y += (t.y - v.y) * speed;
        v.z += (t.z - v.z) * speed;
        v.rx += (0 - v.rx) * speed;
        v.ry += (0 - v.ry) * speed;
        v.rz += (0 - v.rz) * speed;
        
        v.gridX = Math.round(v.x);
        v.gridY = Math.round(v.y);
        v.gridZ = Math.round(v.z);
        v.prevGridX = v.gridX; v.prevGridY = v.gridY; v.prevGridZ = v.gridZ;
        v.vx = 0; v.vy = 0; v.vz = 0;

        if ((t.x - v.x) ** 2 + (t.y - v.y) ** 2 + (t.z - v.z) ** 2 > 0.01) {
            allDone = false;
        } else {
            v.x = t.x; v.y = t.y; v.z = t.z;
            v.rx = 0; v.ry = 0; v.rz = 0;
        }
    });

    if (allDone) {
        this.state = AppState.STABLE;
        this.onStateChange(this.state);
    }
  }

  private animate() {
    this.animationId = requestAnimationFrame(this.animate);
    
    const now = performance.now();
    const dt = now - this.lastTime;
    this.lastTime = now;
    
    this.tickAccumulator += dt;

    if (this.state === AppState.FLUID) {
        while (this.tickAccumulator >= TICK_RATE) {
            this.runWaterTick();
            this.tickAccumulator -= TICK_RATE;
        }
        const alpha = this.tickAccumulator / TICK_RATE;
        this.draw(alpha);
    } 
    else if (this.state === AppState.DISMANTLING) {
        this.updateDismantlePhysics();
        this.draw(0);
        this.tickAccumulator = 0;
    } 
    else if (this.state === AppState.REBUILDING) {
        this.updateRebuildPhysics();
        this.draw(0);
        this.tickAccumulator = 0;
    }
    else {
        if (this.controls.autoRotate) {
             this.draw(0);
        }
        this.tickAccumulator = 0;
    }

    this.controls.update();
    this.renderer.render(this.scene, this.camera);
  }

  public handleResize() {
      if (this.camera && this.renderer) {
        this.camera.aspect = window.innerWidth / window.innerHeight;
        this.camera.updateProjectionMatrix();
        this.renderer.setSize(window.innerWidth, window.innerHeight);
      }
  }
  
  public setAutoRotate(enabled: boolean) {
    if (this.controls) {
        this.controls.autoRotate = enabled;
    }
  }

  public getJsonData(): string {
      const data = this.voxels.map((v, i) => ({
          id: i,
          x: +v.x.toFixed(2),
          y: +v.y.toFixed(2),
          z: +v.z.toFixed(2),
          c: '#' + v.color.getHexString(),
          type: v.type
      }));
      return JSON.stringify(data, null, 2);
  }
  
  public getUniqueColors(): string[] {
    const colors = new Set<string>();
    this.voxels.forEach(v => {
        colors.add('#' + v.color.getHexString());
    });
    return Array.from(colors);
  }

  public cleanup() {
    cancelAnimationFrame(this.animationId);
    this.container.removeChild(this.renderer.domElement);
    this.renderer.dispose();
  }
}