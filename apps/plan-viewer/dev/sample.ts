import type { PlanFurniture, PlanOpening, PlanPolygon, PlanScene3D } from '../src/schema';

/** Newly authored synthetic geometry, used only by the Vite development harness and tests. */
const rect = (x: number, y: number, w: number, h: number): PlanPolygon => ({
  outer: [{ x, y }, { x: x + w, y }, { x: x + w, y: y + h }, { x, y: y + h }], holes: [],
});
const opening = (id: string, kind: string, x: number, y: number, width: number, angle = 0): PlanOpening => ({
  id, kind, center: { x, y }, widthMetres: width, thicknessMetres: .18,
  heightMetres: kind === 'window' ? 1.3 : 2.1, sillMetres: kind === 'window' ? .85 : 0, rotationDegrees: (angle % 360 + 360) % 360,
});
const furniture = (id: string, kind: string, x: number, y: number, w: number, d: number, angle = 0): PlanFurniture => ({
  id, kind, center: { x, y }, widthMetres: w, depthMetres: d, rotationDegrees: (angle % 360 + 360) % 360,
});

export const sampleScene: PlanScene3D = {
  version: 1, label: 'Ground floor', floorHeightMetres: 3.2, wallHeightMetres: 2.95, wallThicknessMetres: .18,
  footprint: [{ outer: [{ x: 0, y: 0 }, { x: 11, y: 0 }, { x: 11, y: 7 }, { x: 8, y: 7 }, { x: 8, y: 10 }, { x: 0, y: 10 }], holes: [] }],
  walls: [
    rect(0, 0, 1, .18), rect(2.8, 0, 3.9, .18), rect(8.4, 0, 2.6, .18),
    rect(0, .18, .18, 1.82), rect(0, 3.5, .18, 2.5), rect(0, 7.3, .18, 2.52),
    rect(10.82, .18, .18, .82), rect(10.82, 2.8, .18, 1.7), rect(10.82, 6, .18, 1),
    rect(.18, 9.82, 6.02, .18), rect(7.1, 9.82, .9, .18), rect(7.82, 7, .18, 2.82),
    rect(8, 6.82, 2.82, .18),
    rect(5.1, .18, .18, 1.92), rect(5.1, 3, .18, 1.8), rect(5.1, 5.7, .18, 1.3),
    rect(5.28, 3.8, 5.54, .18),
    rect(.18, 5.35, 3.52, .18), rect(4.6, 5.35, .5, .18),
    rect(3.4, 5.53, .18, 3.07), rect(3.4, 9.4, .18, .42),
    rect(3.58, 8.2, 1.52, .18),
  ],
  rooms: [
    { id: 'living', name: 'Living & dining', kind: 'living', polygons: [rect(.18, .18, 4.92, 5.17)] },
    { id: 'bedroom-1', name: 'Bedroom 1', kind: 'bedroom', polygons: [rect(5.28, .18, 5.54, 3.62)] },
    { id: 'bedroom-2', name: 'Bedroom 2', kind: 'bedroom', polygons: [rect(5.28, 3.98, 5.54, 2.84)] },
    { id: 'kitchen', name: 'Kitchen', kind: 'kitchen', polygons: [rect(.18, 5.53, 3.22, 4.29)] },
    { id: 'bathroom', name: 'Bathroom', kind: 'bathroom', polygons: [rect(3.58, 5.53, 1.52, 2.67)] },
    { id: 'entry', name: 'Entry', kind: 'foyer', polygons: [rect(5.28, 7, 2.54, 2.82)] },
  ],
  openings: [
    opening('living-window', 'window', 1.9, .09, 1.8), opening('bed-window', 'window', 7.55, .09, 1.7),
    opening('living-west', 'window', .09, 2.75, 1.5, 90), opening('kitchen-west', 'window', .09, 6.65, 1.3, 90),
    opening('bed-east', 'window', 10.91, 1.9, 1.8, 90), opening('bed-2-east', 'window', 10.91, 5.25, 1.5, 90),
    opening('entry-door', 'door', 6.65, 9.91, .9), opening('bed-1-door', 'door', 5.19, 2.55, .9, 90),
    opening('bed-2-door', 'door', 5.19, 5.25, .9, 90), opening('kitchen-door', 'arch', 4.15, 5.44, .9),
    opening('bath-door', 'door', 3.49, 9, .8, 90),
  ],
  furniture: [
    furniture('bed-1', 'bed', 9.38, 1.92, 2.05, 1.65, 180), furniture('wardrobe-1', 'wardrobe', 6.4, .52, .6, 1.7, 90),
    furniture('bed-2', 'bed', 9.38, 5.4, 2.05, 1.5, 180),
    furniture('sofa', 'sofa', .76, 2.45, 1.02, 2.3), furniture('coffee-table', 'coffee_table', 2.15, 2.45, .65, 1.1),
    furniture('dining', 'dining_table', 3.9, 1.15, 1.45, .8),
    furniture('chair-1', 'chair', 3.9, .45, .48, .45, 90), furniture('chair-2', 'chair', 3.9, 1.85, .48, .45, -90),
    { ...furniture('counter', 'kitchen_counter', 1.78, 7.67, 3.22, 4.29), polygons: [{ outer: [
      { x: .22, y: 5.57 }, { x: 3.36, y: 5.57 }, { x: 3.36, y: 6.17 },
      { x: .82, y: 6.17 }, { x: .82, y: 9.78 }, { x: .22, y: 9.78 },
    ], holes: [] }] },
    furniture('sink', 'sink', .52, 6.5, .58, .75), furniture('fridge', 'refrigerator', 2.95, 9.37, .75, .8),
    furniture('wc', 'toilet', 4.53, 6, .65, .5, -90), furniture('basin', 'basin', 4.6, 7.65, .6, .5, 180),
  ],
  stairs: [], assumptions: ['Synthetic fixture. Furniture and heights are illustrative.'],
};
