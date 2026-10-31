import { Component, Suspense, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import type { ErrorInfo, ReactNode } from 'react';
import { Canvas, useThree } from '@react-three/fiber';
import { OrbitControls } from '@react-three/drei';
import type { OrbitControls as OrbitControlsImpl } from 'three-stdlib';
import { DoubleSide, MOUSE, OrthographicCamera, PCFShadowMap, TOUCH, Vector3 } from 'three';
import { Furniture, PolygonSolid, Stair } from './furniture';
import type { Materials } from './furniture';
import { fitOrthographicCamera, openingBands, polygonArea, sceneBounds, worldRotation } from './geometry';
import type { PlanOpening, PlanScene3D, ViewerCallbacks, ViewerInput } from './schema';

type CameraAction = { kind: 'fit' | 'isometric' | 'top' | 'in' | 'out'; serial: number };
type ViewMode = 'isometric' | 'top';

export class ViewerBoundary extends Component<{ children: ReactNode; onError?: ViewerCallbacks['onError'] }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidCatch(_error: Error, _info: ErrorInfo) { this.props.onError?.('The 3D view could not be opened. Try reopening this floor.'); }
  render() {
    return this.state.failed ? <ViewerMessage title="Couldn’t open the 3D view" detail="Switch to 2D, then reopen this floor to try again." /> : this.props.children;
  }
}

export function ViewerMessage({ title, detail }: { title: string; detail: string }) {
  return <div className="pv-message" role="status"><Icon name="cube" /><strong>{title}</strong><span>{detail}</span></div>;
}

function supportedWebGL() {
  try {
    const canvas = document.createElement('canvas');
    const gl = canvas.getContext('webgl2');
    if (!gl) return false;
    gl.getExtension('WEBGL_lose_context')?.loseContext();
    return true;
  } catch { return false; }
}

const light: Materials = { floor: '#d8d2c6', room: '#eee9df', wall: '#eee9dc', cap: '#fffdf7',
  line: '#aaa396', furnishing: '#aaa294', soft: '#e6e0d5', detail: '#80796e', glass: '#b6c2bf', ground: '#fbf9f2' };
const dark: Materials = { floor: '#55564d', room: '#65665b', wall: '#c3c2b5', cap: '#d6d4c8',
  line: '#85867a', furnishing: '#8a897c', soft: '#b8b6a9', detail: '#64675e', glass: '#78928d', ground: '#141513' };

function Opening({ opening: o, wallHeight, materials: m }: { opening: PlanOpening; wallHeight: number; materials: Materials }) {
  const bands = openingBands(o, wallHeight);
  const sill = o.sillMetres, head = Math.min(wallHeight, sill + o.heightMetres);
  const visibleHeight = Math.max(0, head - sill);
  const window = o.kind.toLowerCase().includes('window') || o.kind.toLowerCase().includes('vent');
  const frame = Math.min(.045, o.widthMetres * .08, Math.max(.015, visibleHeight * .1));
  return <group position={[o.center.x, 0, o.center.y]} rotation={[0, worldRotation(o.rotationDegrees), 0]}>
    {bands.map((band, i) => <mesh key={i} position={[0, band.bottom + band.height / 2, 0]} castShadow receiveShadow>
      <boxGeometry args={[o.widthMetres + .006, band.height, o.thicknessMetres]} />
      <meshStandardMaterial color={m.wall} roughness={.9} />
    </mesh>)}
    {visibleHeight > .025 && (window ? <>
      <mesh position={[0, sill + visibleHeight / 2, 0]}>
        <boxGeometry args={[Math.max(.02, o.widthMetres - frame * 2), Math.max(.02, visibleHeight - frame * 2), .012]} />
        <meshStandardMaterial color={m.glass} transparent opacity={.3} roughness={.3} depthWrite={false} side={DoubleSide} />
      </mesh>
      {[-1, 1].map(side => <mesh key={side} position={[side * (o.widthMetres / 2 - frame / 2), sill + visibleHeight / 2, 0]}>
        <boxGeometry args={[frame, visibleHeight, o.thicknessMetres * .55]} /><meshStandardMaterial color={m.detail} roughness={.8} />
      </mesh>)}
      {[sill + frame / 2, head - frame / 2].map((y, i) => <mesh key={i} position={[0, y, 0]}>
        <boxGeometry args={[o.widthMetres, frame, o.thicknessMetres * .55]} /><meshStandardMaterial color={m.detail} roughness={.8} />
      </mesh>)}
    </> : null)}
  </group>;
}

function CameraRig({ scene, action, mode }: { scene: PlanScene3D; action: CameraAction; mode: ViewMode }) {
  const { camera: rawCamera, size, invalidate } = useThree();
  const camera = rawCamera as OrthographicCamera;
  const controls = useRef<OrbitControlsImpl>(null);
  const bounds = useMemo(() => sceneBounds(scene), [scene]);
  const modeRef = useRef(mode);
  const previousScene = useRef<PlanScene3D | null>(null);
  modeRef.current = mode;

  const fit = useCallback((reset: boolean) => {
    if (size.width <= 0 || size.height <= 0) return;
    const height = scene.wallHeightMetres;
    const extent = Math.max(bounds.width, bounds.depth, height, 2);
    const target = new Vector3(0, height * .22, 0);
    if (reset) {
      if (modeRef.current === 'top') {
        camera.up.set(0, 0, -1);
        camera.position.set(0, extent * 2, .001);
      } else {
        camera.up.set(0, 1, 0);
        camera.position.set(extent * .9, extent * .95, extent * 1.1);
      }
    } else if (controls.current) {
      // Recenter a panned view without changing its orbit direction.
      camera.position.add(target.clone().sub(controls.current.target));
    }
    camera.lookAt(target);
    controls.current?.target.copy(target);
    camera.near = .01; camera.far = extent * 20;
    fitOrthographicCamera(camera, bounds.width, bounds.depth, height, size.width, size.height);
    controls.current?.update(); invalidate();
  }, [scene, bounds, camera, size.width, size.height, invalidate]);

  const fitRef = useRef(fit);
  fitRef.current = fit;
  useLayoutEffect(() => {
    fit(previousScene.current !== scene);
    previousScene.current = scene;
  }, [fit, scene]);
  useEffect(() => {
    if (action.kind === 'in' || action.kind === 'out') {
      camera.zoom = Math.max(.5, Math.min(2000, camera.zoom * (action.kind === 'in' ? 1.2 : 1 / 1.2)));
      camera.updateProjectionMatrix(); invalidate();
    } else fitRef.current(action.kind !== 'fit');
  }, [action, camera, invalidate]);

  return <OrbitControls ref={controls} makeDefault enableDamping={false} enableRotate={mode === 'isometric'}
    mouseButtons={{ LEFT: mode === 'top' ? MOUSE.PAN : MOUSE.ROTATE, MIDDLE: MOUSE.DOLLY, RIGHT: MOUSE.PAN }}
    touches={{ ONE: mode === 'top' ? TOUCH.PAN : TOUCH.ROTATE, TWO: TOUCH.DOLLY_PAN }}
    maxPolarAngle={Math.PI / 2 - .04} minPolarAngle={.01} minZoom={.5} maxZoom={2000}
    enablePan screenSpacePanning={mode === 'top'} zoomToCursor />;
}

function Scene({ scene, action, mode, furniture, cutaway, theme, onSelect, onReady, onLost }: {
  scene: PlanScene3D; action: CameraAction; mode: ViewMode; furniture: boolean; cutaway: boolean;
  theme: 'light' | 'dark'; onSelect: (name: string) => void; onReady: () => void; onLost: () => void;
}) {
  const { gl, invalidate } = useThree();
  const materials = theme === 'dark' ? dark : light;
  const bounds = useMemo(() => sceneBounds(scene), [scene]);
  const wallHeight = cutaway ? Math.min(1.05, scene.wallHeightMetres) : scene.wallHeightMetres;
  const extent = Math.max(bounds.width, bounds.depth, 3);
  useEffect(() => { onReady(); invalidate(); }, [onReady, invalidate, scene, theme]);
  useEffect(() => {
    const element = gl.domElement;
    const lost = (event: Event) => { event.preventDefault(); onLost(); };
    element.addEventListener('webglcontextlost', lost);
    return () => { element.removeEventListener('webglcontextlost', lost); };
  }, [gl, onLost]);
  return <>
    <color attach="background" args={[materials.ground]} />
    <ambientLight intensity={.65} />
    <hemisphereLight args={['#ffffff', theme === 'dark' ? '#777a70' : '#ddd7c9', .7]} />
    <directionalLight position={[-extent * .6, extent * 1.6, extent * .8]} intensity={2.1} castShadow
      shadow-mapSize={[1024, 1024]} shadow-camera-left={-extent} shadow-camera-right={extent}
      shadow-camera-top={extent} shadow-camera-bottom={-extent} shadow-camera-far={extent * 5}
      shadow-bias={-.0005} shadow-normalBias={.025} />
    <group position={[-bounds.center.x, 0, -bounds.center.y]}>
      {scene.footprint.map((polygon, i) => <PolygonSolid key={`slab-${i}`} polygon={polygon} height={.16} bottom={-.16} color={materials.floor} />)}
      {scene.rooms.map(room => <group key={room.id}>{room.polygons.map((polygon, i) =>
        <PolygonSolid key={i} polygon={polygon} height={.008} bottom={.005} color={materials.room} onSelect={() => onSelect(room.name)} />)}</group>)}
      {scene.walls.map((polygon, i) => <PolygonSolid key={`wall-${i}`} polygon={polygon} height={wallHeight} color={materials.wall} line={materials.line} />)}
      {scene.openings.map(opening => <Opening key={opening.id} opening={opening} wallHeight={wallHeight} materials={materials} />)}
      {furniture && scene.furniture.map(item => <Furniture key={item.id} item={item} materials={materials} />)}
      {scene.stairs.map(stair => <Stair key={stair.id} stair={stair} materials={materials} />)}
    </group>
    <CameraRig scene={scene} action={action} mode={mode} />
  </>;
}

export function PlanViewer({ input, callbacks }: { input: ViewerInput; callbacks: ViewerCallbacks }) {
  const { scene, theme, units } = input;
  const [mode, setMode] = useState<ViewMode>('isometric');
  const [furniture, setFurniture] = useState(true);
  const [cutaway, setCutaway] = useState(true);
  const [selected, setSelected] = useState('');
  const [ready, setReady] = useState(false);
  const [lost, setLost] = useState(false);
  const [action, setAction] = useState<CameraAction>({ kind: 'isometric', serial: 0 });
  const [webGL] = useState(supportedWebGL);
  const readyHandler = useCallback(() => setReady(true), []);
  const lostHandler = useCallback(() => { setLost(true); callbacks.onError?.('The 3D connection was interrupted. Reopen this floor to try again.'); }, [callbacks]);
  const act = (kind: CameraAction['kind']) => {
    if (kind === 'isometric' || kind === 'top') setMode(kind);
    setAction(previous => ({ kind, serial: previous.serial + 1 }));
  };
  useEffect(() => { setSelected(''); }, [scene]);
  const area = useMemo(() => scene.footprint.reduce((sum, p) => sum + polygonArea(p), 0), [scene]);

  return <div className={`pv-root pv-${theme}`} role="region" aria-label={`${scene.label} 3D concept view`}
    tabIndex={0} onKeyDown={event => {
      if ((event.target as HTMLElement).tagName === 'INPUT' || event.altKey || event.ctrlKey || event.metaKey) return;
      const key = event.key.toLowerCase();
      const command = key === 'f' ? 'fit' : key === '1' ? 'isometric' : key === '2' ? 'top' : ['+', '='].includes(key) ? 'in' : key === '-' ? 'out' : null;
      if (command) { event.preventDefault(); act(command); }
    }}>
    {!webGL ? <ViewerMessage title="3D isn’t available in this browser" detail="Enable hardware acceleration or try a browser with WebGL 2 support. Your 2D plan remains available." />
      : lost ? <ViewerMessage title="3D view interrupted" detail="Switch to 2D, then reopen this floor to try again." />
      : <Canvas orthographic shadows={{ type: PCFShadowMap }} dpr={[1, 1.5]} frameloop="demand" camera={{ position: [12, 12, 14], zoom: 30, near: .01, far: 1000 }}
        gl={{ antialias: true, powerPreference: 'low-power', alpha: false }}
        onPointerMissed={() => setSelected('')} fallback={<ViewerMessage title="3D isn’t available" detail="Use 2D to continue reviewing this floor." />}>
        <Suspense fallback={null}><Scene scene={scene} action={action} mode={mode} furniture={furniture} cutaway={cutaway}
          theme={theme} onSelect={setSelected} onReady={readyHandler} onLost={lostHandler} /></Suspense>
      </Canvas>}
    {webGL && !lost && <>
      <div className="pv-toolbar" role="toolbar" aria-label="3D view options">
        <div className="pv-segment">
          <Tool label="Isometric view" active={mode === 'isometric'} onClick={() => act('isometric')}><Icon name="cube" /><span>Isometric</span></Tool>
          <Tool label="Top view" active={mode === 'top'} onClick={() => act('top')}><Icon name="top" /><span>Top</span></Tool>
        </div>
        <span className="pv-divider" />
        <Tool label={cutaway ? 'Show full-height walls' : 'Show cutaway walls'} active={cutaway} onClick={() => setCutaway(v => !v)}><Icon name="wall" /><span>Cutaway</span></Tool>
        <Tool label={furniture ? 'Hide furniture' : 'Show furniture'} active={furniture} onClick={() => setFurniture(v => !v)}><Icon name="furniture" /><span>Furniture</span></Tool>
      </div>
      <div className="pv-zoom" role="toolbar" aria-label="3D camera controls">
        <Tool label="Zoom in" onClick={() => act('in')}><Icon name="plus" /></Tool>
        <Tool label="Zoom out" onClick={() => act('out')}><Icon name="minus" /></Tool>
        <span className="pv-zoom-divider" />
        <Tool label="Fit model to view (F)" onClick={() => act('fit')}><Icon name="fit" /></Tool>
      </div>
      {!ready && <div className="pv-loading" role="status">Preparing 3D view…</div>}
      <div className="pv-footer">
        <div className="pv-caption"><span className="pv-concept">Concept view</span><span>{selected || scene.label}</span><span className="pv-area">{new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 }).format(units === 'FEET_INCHES' ? area / .09290304 : area)} {units === 'FEET_INCHES' ? 'sq ft' : 'm²'}</span></div>
        <span className="pv-hint">{mode === 'top' ? 'Drag to pan · Scroll to zoom' : 'Drag to orbit · Right-drag to pan · Scroll to zoom'}</span>
      </div>
    </>}
  </div>;
}

function Tool({ label, active, onClick, children }: { label: string; active?: boolean; onClick: () => void; children: ReactNode }) {
  return <button type="button" className={`pv-tool${active ? ' pv-active' : ''}`} title={label} aria-label={label}
    aria-pressed={active} onClick={onClick}>{children}</button>;
}

export function Icon({ name }: { name: string }) {
  const paths: Record<string, ReactNode> = {
    cube: <><path d="m12 3 8 4.5v9L12 21l-8-4.5v-9L12 3Z" /><path d="m4 7.5 8 4.7 8-4.7M12 12v9M8 5.3l8 4.6" /></>,
    top: <><rect x="4" y="4" width="16" height="16" rx="1" /><path d="M4 13h9V4M13 13v7M13 9h7" /></>,
    wall: <><path d="M4 20V8l8-4 8 4v12M4 20l8-4 8 4M12 4v12M4 13l8-4 8 4" /></>,
    furniture: <><path d="M5 11V7a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v4M5 11h14a2 2 0 0 1 2 2v5H3v-5a2 2 0 0 1 2-2ZM6 18v2M18 18v2M3 14h18" /></>,
    plus: <path d="M12 5v14M5 12h14" />,
    minus: <path d="M5 12h14" />,
    fit: <><path d="M8 4H4v4M16 4h4v4M20 16v4h-4M8 20H4v-4" /><path d="m8 8 8 8m0-8-8 8" /></>,
  };
  return <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name] || paths.cube}</svg>;
}
