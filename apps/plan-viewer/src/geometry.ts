import { Box3, ExtrudeGeometry, OrthographicCamera, Path, Shape, ShapeGeometry, Vector2, Vector3 } from 'three';
import type { PlanOpening, PlanPoint, PlanPolygon, PlanScene3D } from './schema';

/** Negate the shape's y before turning its XY plane into the world XZ plane. */
export function polygonShape(polygon: PlanPolygon): Shape {
  const ring = (points: PlanPoint[]) => points.map(p => new Vector2(p.x, -p.y));
  const shape = new Shape(ring(polygon.outer));
  shape.holes = (polygon.holes ?? []).map(hole => new Path(ring(hole)));
  return shape;
}

export function polygonGeometry(polygon: PlanPolygon, height = 0) {
  const geometry = height > 0
    ? new ExtrudeGeometry(polygonShape(polygon), { depth: height, bevelEnabled: false, steps: 1, curveSegments: 1 })
    : new ShapeGeometry(polygonShape(polygon));
  geometry.rotateX(-Math.PI / 2);
  geometry.computeVertexNormals();
  return geometry;
}

export const worldRotation = (degrees: number) => -degrees * Math.PI / 180;

export function planarRotate(point: PlanPoint, degrees: number): PlanPoint {
  const r = degrees * Math.PI / 180;
  return { x: point.x * Math.cos(r) - point.y * Math.sin(r), y: point.x * Math.sin(r) + point.y * Math.cos(r) };
}

export function ringArea(points: PlanPoint[]): number {
  return Math.abs(points.reduce((area, p, i) => {
    const q = points[(i + 1) % points.length];
    return area + p.x * q.y - q.x * p.y;
  }, 0)) / 2;
}

export function polygonArea(polygon: PlanPolygon): number {
  return Math.max(0, ringArea(polygon.outer) - (polygon.holes ?? []).reduce((sum, r) => sum + ringArea(r), 0));
}

export function sceneBounds(scene: PlanScene3D) {
  const points = scene.footprint.flatMap(p => p.outer);
  const minX = Math.min(...points.map(p => p.x)), maxX = Math.max(...points.map(p => p.x));
  const minY = Math.min(...points.map(p => p.y)), maxY = Math.max(...points.map(p => p.y));
  return { minX, maxX, minY, maxY, width: maxX - minX, depth: maxY - minY,
    center: { x: (minX + maxX) / 2, y: (minY + maxY) / 2 } };
}

/** Fit the current orbit direction to the actual viewport, leaving hidden 0×0 views untouched. */
export function fitOrthographicCamera(camera: OrthographicCamera, width: number, depth: number, wallHeight: number,
  viewportWidth: number, viewportHeight: number): boolean {
  if (viewportWidth <= 0 || viewportHeight <= 0) return false;
  camera.left = -viewportWidth / 2; camera.right = viewportWidth / 2;
  camera.top = viewportHeight / 2; camera.bottom = -viewportHeight / 2;
  camera.updateMatrixWorld(true);
  const box = new Box3(), point = new Vector3();
  for (const x of [-width / 2, width / 2]) for (const y of [0, wallHeight]) for (const z of [-depth / 2, depth / 2]) {
    point.set(x, y, z).applyMatrix4(camera.matrixWorldInverse); box.expandByPoint(point);
  }
  const span = box.getSize(new Vector3());
  camera.zoom = Math.min(viewportWidth / Math.max(span.x, 1), viewportHeight / Math.max(span.y, 1)) * .78;
  camera.updateProjectionMatrix();
  return true;
}

export interface VerticalBand { bottom: number; height: number }

/** 2D walls have aperture gaps. Restore only the solid bands under/over each opening. */
export function openingBands(opening: PlanOpening, wallHeight: number): VerticalBand[] {
  const sill = Math.max(0, opening.sillMetres);
  const head = sill + opening.heightMetres;
  const bands: VerticalBand[] = [];
  if (sill > 0) bands.push({ bottom: 0, height: Math.min(sill, wallHeight) });
  if (head < wallHeight) bands.push({ bottom: head, height: wallHeight - head });
  return bands.filter(b => b.height > .001);
}

/** Schematic tread lines stay inside every outer ring and outside any stair void. */
export function stairTreadSegments(polygons: PlanPolygon[], steps: number): number[] {
  const vertices: number[] = [];
  for (const polygon of polygons) {
    const xs = polygon.outer.map(p => p.x), ys = polygon.outer.map(p => p.y);
    const minX = Math.min(...xs), maxX = Math.max(...xs), minY = Math.min(...ys), maxY = Math.max(...ys);
    const alongX = maxX - minX > maxY - minY;
    const low = alongX ? minX : minY, high = alongX ? maxX : maxY;
    const count = Math.min(Math.max(steps, 2), 24);
    for (let step = 1; step < count; step++) {
      const at = low + (high - low) * step / count;
      const hits: number[] = [];
      for (const ring of [polygon.outer, ...(polygon.holes ?? [])]) for (let i = 0; i < ring.length; i++) {
        const a = ring[i], b = ring[(i + 1) % ring.length];
        const aa = alongX ? a.x : a.y, bb = alongX ? b.x : b.y;
        if ((aa > at) === (bb > at)) continue;
        const av = alongX ? a.y : a.x, bv = alongX ? b.y : b.x;
        hits.push(av + (at - aa) * (bv - av) / (bb - aa));
      }
      hits.sort((a, b) => a - b);
      for (let i = 0; i + 1 < hits.length; i += 2) {
        if (alongX) vertices.push(at, .17, hits[i], at, .17, hits[i + 1]);
        else vertices.push(hits[i], .17, at, hits[i + 1], .17, at);
      }
    }
  }
  return vertices;
}
