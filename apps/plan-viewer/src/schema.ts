/** Portable accepted-plan geometry. x/y are metres in the displayed 2D plan. */
export interface PlanPoint { x: number; y: number }
export interface PlanPolygon { outer: PlanPoint[]; holes: PlanPoint[][] }
export interface PlanRoom { id: string; name: string; kind: string; polygons: PlanPolygon[] }
export interface PlanOpening {
  id: string; kind: string; center: PlanPoint; widthMetres: number;
  thicknessMetres: number; heightMetres: number; sillMetres: number; rotationDegrees: number;
}
export interface PlanFurniture {
  id: string; kind: string; center: PlanPoint; widthMetres: number;
  depthMetres: number; rotationDegrees: number; polygons?: PlanPolygon[];
}
export interface PlanStairPart { polygons: PlanPolygon[]; bottomMetres: number; heightMetres: number }
export interface PlanStair { id: string; polygons: PlanPolygon[]; steps: number; rotationDegrees: number; parts?: PlanStairPart[] }
export interface PlanScene3D {
  version: 1; label: string; floorHeightMetres: number; wallHeightMetres: number;
  wallThicknessMetres: number; footprint: PlanPolygon[]; walls: PlanPolygon[];
  rooms: PlanRoom[]; openings: PlanOpening[]; furniture: PlanFurniture[];
  stairs: PlanStair[]; assumptions?: string[];
}
export interface ViewerInput { scene: PlanScene3D; theme: 'light' | 'dark'; units?: 'METRES' | 'FEET_INCHES' }
export interface ViewerCallbacks { onError?: (message: string) => void }
export interface ViewerHandle { update: (input: ViewerInput) => void; dispose: () => void }

const error = () => new Error('This plan’s 3D geometry could not be opened.');
const record = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const finite = (v: unknown, min: number, max: number) => typeof v === 'number' && Number.isFinite(v) && v >= min && v <= max;
const text = (v: unknown) => typeof v === 'string' && v.length <= 240;
const identifier = (v: unknown) => typeof v === 'string' && v.trim().length > 0 && v.length <= 160;
const distance = (v: unknown, maximum: number) => finite(v, Number.MIN_VALUE, maximum);
const rotation = (v: unknown) => typeof v === 'number' && Number.isFinite(v) && v >= 0 && v < 360;

/** Guard the public JS boundary before allocating meshes or GPU resources. */
export function validateScene(value: unknown): asserts value is PlanScene3D {
  if (!record(value) || value.version !== 1 || !identifier(value.label)
    || !distance(value.floorHeightMetres, 20) || !distance(value.wallHeightMetres, 20)
    || (value.wallHeightMetres as number) > (value.floorHeightMetres as number)
    || !distance(value.wallThicknessMetres, 3)) throw error();
  let points = 0;
  const point = (p: unknown) => {
    if (!record(p) || !finite(p.x, -2000, 2000) || !finite(p.y, -2000, 2000)) throw error();
  };
  const ring = (r: unknown) => {
    if (!Array.isArray(r) || r.length < 3 || r.length > 8192 || (points += r.length) > 50_000) throw error();
    r.forEach(point);
    const twiceArea = (r as PlanPoint[]).reduce((sum, p, i) => {
      const q = r[(i + 1) % r.length] as PlanPoint;
      return sum + p.x * q.y - q.x * p.y;
    }, 0);
    if (Math.abs(twiceArea) <= 1e-8) throw error();
  };
  const polygons = (items: unknown, requireOne = false) => {
    if (!Array.isArray(items) || items.length > 1000 || (requireOne && items.length === 0)) throw error();
    for (const p of items) {
      if (!record(p) || !Array.isArray(p.holes) || p.holes.length > 128) throw error();
      ring(p.outer); p.holes.forEach(ring);
    }
  };
  const items = (v: unknown, limit: number): Record<string, unknown>[] => {
    if (!Array.isArray(v) || v.length > limit || v.some(i => !record(i) || !identifier(i.id))) throw error();
    if (new Set(v.map(i => i.id)).size !== v.length) throw error();
    return v as Record<string, unknown>[];
  };
  polygons(value.footprint, true); polygons(value.walls);
  for (const r of items(value.rooms ?? [], 120)) {
    if (typeof r.name !== 'string' || r.name.length > 160 || !identifier(r.kind)) throw error();
    polygons(r.polygons, true);
  }
  for (const o of items(value.openings ?? [], 512)) {
    point(o.center);
    if (!identifier(o.kind) || !distance(o.widthMetres, 100) || !distance(o.thicknessMetres, 3)
      || !distance(o.heightMetres, 20) || !finite(o.sillMetres, 0, 20)
      || (o.sillMetres as number) + (o.heightMetres as number) > (value.wallHeightMetres as number) + .001
      || !rotation(o.rotationDegrees)) throw error();
  }
  for (const f of items(value.furniture ?? [], 800)) {
    point(f.center);
    if (!identifier(f.kind) || !distance(f.widthMetres, 100) || !distance(f.depthMetres, 100)
      || !rotation(f.rotationDegrees)) throw error();
    if (f.polygons !== undefined) polygons(f.polygons);
  }
  for (const s of items(value.stairs ?? [], 24)) {
    polygons(s.polygons, true);
    if (s.parts !== undefined) {
      if (!Array.isArray(s.parts) || s.parts.length > 122) throw error();
      for (const part of s.parts) {
        if (!record(part) || !finite(part.bottomMetres, 0, 20) || !distance(part.heightMetres, 20)
          || (part.bottomMetres as number) + (part.heightMetres as number) > (value.floorHeightMetres as number) + .001) throw error();
        polygons(part.polygons, true);
      }
    }
    if (!finite(s.steps, 1, 120) || !Number.isInteger(s.steps) || !rotation(s.rotationDegrees)) throw error();
  }
  if (value.assumptions !== undefined && (!Array.isArray(value.assumptions)
    || value.assumptions.length > 12 || value.assumptions.some(v => !text(v)))) throw error();
}

export function normaliseInput(input: ViewerInput): ViewerInput {
  validateScene(input?.scene);
  const scene = input.scene;
  return { theme: input.theme === 'dark' ? 'dark' : 'light', units: input.units === 'FEET_INCHES' ? 'FEET_INCHES' : 'METRES', scene: {
    ...scene, rooms: scene.rooms ?? [], openings: scene.openings ?? [],
    furniture: scene.furniture ?? [], stairs: scene.stairs ?? [], assumptions: scene.assumptions ?? [],
  } };
}
