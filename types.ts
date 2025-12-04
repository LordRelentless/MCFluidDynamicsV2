
/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
*/


import * as THREE from 'three';

export enum AppState {
  STABLE = 'STABLE',
  DISMANTLING = 'DISMANTLING',
  REBUILDING = 'REBUILDING',
  FLUID = 'FLUID'
}

export interface VoxelData {
  x: number;
  y: number;
  z: number;
  color: number;
  type?: 'solid' | 'water' | 'snow' | 'hail' | 'steam';
}

export interface SimulationVoxel {
  id: number;
  // Visual position (Interpolated)
  x: number;
  y: number;
  z: number;
  
  // Current Logic position (The Target)
  gridX: number;
  gridY: number;
  gridZ: number;

  // Previous Logic position (For Lerping)
  prevGridX: number;
  prevGridY: number;
  prevGridZ: number;
  
  color: THREE.Color;
  type: 'solid' | 'water' | 'snow' | 'hail' | 'steam';
  
  // Physics state
  vx: number;
  vy: number;
  vz: number;
  rx: number;
  ry: number;
  rz: number;
  rvx: number;
  rvy: number;
  rvz: number;

  // Bitmask for visual connectivity (Mesh Deformation approximation)
  // 1: +x, 2: -x, 4: +y, 8: -y, 16: +z, 32: -z
  neighbors: number; 
  
  // Fluid dynamics
  pressure: number;
}

export interface RebuildTarget {
  x: number;
  y: number;
  z: number;
  delay: number;
  isRubble?: boolean;
}

export interface SavedModel {
  name: string;
  data: VoxelData[];
  baseModel?: string;
}