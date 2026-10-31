import assert from 'node:assert/strict';
import test from 'node:test';
import { Euler, OrthographicCamera, Vector3 } from 'three';
import { fitOrthographicCamera, openingBands, planarRotate, polygonArea, polygonGeometry, sceneBounds, stairTreadSegments, worldRotation } from '../src/geometry';
import { normaliseInput, validateScene } from '../src/schema';
import type { PlanOpening, PlanPolygon } from '../src/schema';
import { sampleScene } from '../dev/sample';

const courtyard: PlanPolygon = {
  outer: [{ x: 0, y: 0 }, { x: 10, y: 0 }, { x: 10, y: 8 }, { x: 0, y: 8 }],
  holes: [[{ x: 3, y: 2 }, { x: 7, y: 2 }, { x: 7, y: 6 }, { x: 3, y: 6 }]],
};

test('floor triangulation preserves a courtyard instead of covering it', () => {
  const geometry = polygonGeometry(courtyard);
  const position = geometry.getAttribute('position');
  const index = geometry.getIndex()!;
  let area = 0;
  const a = new Vector3(), b = new Vector3(), c = new Vector3();
  for (let i = 0; i < index.count; i += 3) {
    a.fromBufferAttribute(position, index.getX(i)); b.fromBufferAttribute(position, index.getX(i + 1)); c.fromBufferAttribute(position, index.getX(i + 2));
    area += b.sub(a).cross(c.sub(a)).length() / 2;
  }
  assert.ok(Math.abs(area - 64) < .0001);
  assert.equal(polygonArea(courtyard), 64);
  geometry.dispose();
});

test('extrusion maps planar y down to world z and extrusion height to world y', () => {
  const geometry = polygonGeometry(courtyard, 2.8);
  geometry.computeBoundingBox();
  const bounds = geometry.boundingBox!;
  assert.ok(Math.abs(bounds.min.x) < .0001 && Math.abs(bounds.max.x - 10) < .0001);
  assert.ok(Math.abs(bounds.min.z) < .0001 && Math.abs(bounds.max.z - 8) < .0001);
  assert.ok(Math.abs(bounds.min.y) < .0001 && Math.abs(bounds.max.y - 2.8) < .0001);
  geometry.dispose();
});

test('clockwise plan furniture rotation agrees with Three world coordinates', () => {
  const point = planarRotate({ x: 2, y: 0 }, 90);
  const world = new Vector3(2, 0, 0).applyEuler(new Euler(0, worldRotation(90), 0));
  assert.ok(Math.abs(point.x - world.x) < 1e-9 && Math.abs(point.y - world.z) < 1e-9);
  assert.ok(Math.abs(world.z - 2) < 1e-9);
});

const window: PlanOpening = { id: 'window', kind: 'window', center: { x: 0, y: 0 }, widthMetres: 1.5,
  thicknessMetres: .2, heightMetres: 1.2, sillMetres: .9, rotationDegrees: 0 };

test('window gap is filled under its sill and above its head, leaving the actual aperture open', () => {
  const bands = openingBands(window, 2.8);
  assert.equal(bands.length, 2);
  assert.equal(bands[0].bottom, 0); assert.equal(bands[0].height, .9);
  assert.ok(Math.abs(bands[1].bottom - 2.1) < 1e-9);
  assert.ok(Math.abs(bands[1].height - .7) < 1e-9);
  assert.ok(bands.every(b => b.bottom + b.height <= .9 || b.bottom >= 2.1));
});

test('cutaway clips sill fill and never adds a floating lintel above the chosen wall height', () => {
  assert.deepEqual(openingBands(window, .75), [{ bottom: 0, height: .75 }]);
  assert.deepEqual(openingBands({ ...window, kind: 'door', sillMetres: 0, heightMetres: 2.1 }, 1.05), []);
});

test('bounds and floor area use the accepted footprint, including its notch', () => {
  const bounds = sceneBounds(sampleScene);
  assert.equal(bounds.width, 11); assert.equal(bounds.depth, 10);
  assert.equal(sampleScene.footprint.reduce((a, p) => a + polygonArea(p), 0), 101);
});

test('the bridge rejects nonfinite and unbounded geometry before GPU allocation', () => {
  const invalid = structuredClone(sampleScene); invalid.footprint[0].outer[0].x = Number.NaN;
  assert.throws(() => validateScene(invalid));
  invalid.footprint[0].outer[0].x = 1e8;
  assert.throws(() => validateScene(invalid));
  assert.throws(() => validateScene({ ...sampleScene, walls: [{ outer: [{ x: 0, y: 0 }], holes: [] }] }));
});

test('omitted optional collections receive defaults without altering accepted dimensions', () => {
  const input = normaliseInput({ scene: { ...sampleScene, openings: undefined, furniture: undefined, stairs: undefined } as never, theme: 'light' });
  assert.deepEqual(input.scene.openings, []); assert.deepEqual(input.scene.furniture, []);
  assert.equal(input.scene.wallHeightMetres, sampleScene.wallHeightMetres);
});

test('browser accepts the upper bounds of the shared portable scene contract', () => {
  const scene = structuredClone(sampleScene);
  scene.floorHeightMetres = 20; scene.wallHeightMetres = 20; scene.wallThicknessMetres = 3;
  scene.furniture[0].widthMetres = 100; scene.furniture[0].depthMetres = 100;
  scene.stairs = [{ id: 'large-stair', polygons: [courtyard], steps: 120, rotationDegrees: 359.9 }];
  assert.doesNotThrow(() => validateScene(scene));
});

test('collapsed footprints, duplicate IDs and windows above the wall fail closed', () => {
  const scene = structuredClone(sampleScene);
  scene.footprint = [{ outer: [{ x: 0, y: 0 }, { x: 1, y: 1 }, { x: 2, y: 2 }], holes: [] }];
  assert.throws(() => validateScene(scene));
  scene.footprint = sampleScene.footprint;
  scene.furniture[1].id = scene.furniture[0].id;
  assert.throws(() => validateScene(scene));
  scene.furniture = sampleScene.furniture;
  scene.openings[0].sillMetres = scene.wallHeightMetres;
  assert.throws(() => validateScene(scene));
});

test('stair tread markers stay within the accepted footprint and do not bridge its void', () => {
  const lines = stairTreadSegments([courtyard], 12);
  assert.ok(lines.length > 0);
  for (let i = 0; i < lines.length; i += 6) {
    const x = (lines[i] + lines[i + 3]) / 2, y = (lines[i + 2] + lines[i + 5]) / 2;
    assert.ok(x >= 0 && x <= 10 && y >= 0 && y <= 8);
    assert.ok(!(x > 3 && x < 7 && y > 2 && y < 6));
  }
});

test('desktop-to-narrow camera fitting contains every model corner without changing the orbit direction', () => {
  const camera = new OrthographicCamera(-720, 720, 472, -472, .01, 1000);
  camera.position.set(11, 12, 14); camera.lookAt(0, .7, 0);
  const orientation = camera.quaternion.clone();
  fitOrthographicCamera(camera, 11, 10, 3.2, 1440, 944);
  const desktopZoom = camera.zoom;
  fitOrthographicCamera(camera, 11, 10, 3.2, 390, 788);
  assert.ok(camera.zoom < desktopZoom);
  assert.ok(camera.quaternion.equals(orientation));
  for (const x of [-5.5, 5.5]) for (const y of [0, 3.2]) for (const z of [-5, 5]) {
    const ndc = new Vector3(x, y, z).project(camera);
    assert.ok(Math.abs(ndc.x) < 1 && Math.abs(ndc.y) < 1, `${ndc.x},${ndc.y}`);
  }
  const valid = { zoom: camera.zoom, left: camera.left, right: camera.right, top: camera.top, bottom: camera.bottom };
  assert.equal(fitOrthographicCamera(camera, 11, 10, 3.2, 0, 0), false);
  assert.deepEqual({ zoom: camera.zoom, left: camera.left, right: camera.right, top: camera.top, bottom: camera.bottom }, valid);
});


test('native stair solids remain bounded by the floor height at the JS boundary', () => {
  const scene = structuredClone(sampleScene);
  scene.stairs = [{ id: 'native-stair', polygons: [courtyard], steps: 21, rotationDegrees: 90,
    parts: [{ polygons: [courtyard], bottomMetres: .15, heightMetres: .15 }] }];
  validateScene(scene);
  scene.stairs[0].parts![0].heightMetres = scene.floorHeightMetres;
  assert.throws(() => validateScene(scene));
  scene.stairs[0].parts![0].heightMetres = .15;
  scene.stairs[0].parts![0].bottomMetres = -.1;
  assert.throws(() => validateScene(scene));
});


test('area display units follow the host without rescaling metre geometry', () => {
  const input = normaliseInput({scene: sampleScene, theme: 'light', units: 'FEET_INCHES'});
  assert.equal(input.units, 'FEET_INCHES');
  assert.deepEqual(input.scene.footprint, sampleScene.footprint);
  assert.equal(normaliseInput({scene: sampleScene, theme: 'light'}).units, 'METRES');
});
