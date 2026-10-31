import { useEffect, useMemo } from 'react';
import { Edges } from '@react-three/drei';
import { BufferGeometry, DoubleSide, Float32BufferAttribute } from 'three';
import { polygonGeometry, stairTreadSegments, worldRotation } from './geometry';
import type { PlanFurniture, PlanPolygon, PlanStair } from './schema';

export interface Materials {
  floor: string; room: string; wall: string; cap: string; line: string;
  furnishing: string; soft: string; detail: string; glass: string; ground: string;
}

export function PolygonSolid({ polygon, height, bottom = 0, color, line, onSelect }: {
  polygon: PlanPolygon; height: number; bottom?: number; color: string; line?: string; onSelect?: () => void;
}) {
  const geometry = useMemo(() => polygonGeometry(polygon, height), [polygon, height]);
  useEffect(() => () => geometry.dispose(), [geometry]);
  return <mesh geometry={geometry} position={[0, bottom, 0]} castShadow={height > .1} receiveShadow
    onClick={onSelect ? e => { e.stopPropagation(); onSelect(); } : undefined}>
    <meshStandardMaterial color={color} roughness={.88} side={DoubleSide} />
    {line && <Edges threshold={30} color={line} transparent opacity={.32} />}
  </mesh>;
}

function Block({ size, at, color, rounded = false }: {
  size: [number, number, number]; at: [number, number, number]; color: string; rounded?: boolean;
}) {
  // Plain solids deliberately communicate the plan without adding product-style decoration.
  return <mesh position={at} castShadow receiveShadow>
    <boxGeometry args={size.map(v => Math.max(.015, v)) as [number, number, number]} />
    <meshStandardMaterial color={color} roughness={rounded ? .95 : .8} />
  </mesh>;
}

/** Geometry is authored locally; source CAD artwork and external models are never loaded. */
export function Furniture({ item, materials: m }: { item: PlanFurniture; materials: Materials }) {
  const w = item.widthMetres, d = item.depthMetres, k = item.kind.toLowerCase();
  const body = m.furnishing, soft = m.soft, detail = m.detail;
  if (item.polygons?.length) return <group>
    {item.polygons.map((p, i) => <PolygonSolid key={i} polygon={p} height={.85} color={body} />)}
  </group>;

  let contents;
  if ((k.includes('bed') && !k.includes('side')) || k === 'mattress') {
    // Engine source assemblies place the head at local -X, not -Y.
    contents = <>
      <Block size={[w, .16, d]} at={[0, .12, 0]} color={body} />
      <Block size={[w * .96, .2, d * .96]} at={[0, .3, 0]} color={m.cap} rounded />
      <Block size={[.07, .78, d]} at={[-w / 2 + .035, .39, 0]} color={body} />
      <Block size={[w * .2, .1, d * .38]} at={[-w * .32, .45, -d * .23]} color={soft} rounded />
      {d > 1.05 && <Block size={[w * .2, .1, d * .38]} at={[-w * .32, .45, d * .23]} color={soft} rounded />}
      <Block size={[w * .53, .025, d * .97]} at={[w * .2, .415, 0]} color={body} />
    </>;
  } else if (k === 'sofa_l' || k.includes('sectional')) {
    // Source L-sofa opens to local +X / -Z. Its bounding rectangle is not a seat.
    const seatX = Math.min(.82, w * .44), seatZ = Math.min(.82, d * .44);
    const back = Math.min(.13, seatX * .2, seatZ * .2);
    contents = <>
      <Block size={[w, .24, seatZ]} at={[0, .2, (d - seatZ) / 2]} color={body} rounded />
      <Block size={[seatX, .24, d - seatZ]} at={[-(w - seatX) / 2, .2, -seatZ / 2]} color={body} rounded />
      <Block size={[w - back, .15, seatZ - back]} at={[back / 2, .395, (d - seatZ - back) / 2]} color={soft} rounded />
      <Block size={[seatX - back, .15, d - seatZ]} at={[-(w - seatX - back) / 2, .395, -seatZ / 2]} color={soft} rounded />
      <Block size={[w, .48, back]} at={[0, .59, (d - back) / 2]} color={body} rounded />
      <Block size={[back, .48, d - back]} at={[-(w - back) / 2, .59, -back / 2]} color={body} rounded />
      <Block size={[back, .28, seatZ - back]} at={[(w - back) / 2, .48, (d - seatZ - back) / 2]} color={body} rounded />
      <Block size={[seatX - back, .28, back]} at={[-(w - seatX - back) / 2, .48, -(d - back) / 2]} color={body} rounded />
    </>;
  } else if (k.includes('sofa') || k.includes('couch')) {
    contents = <>
      <Block size={[w, .28, d]} at={[0, .22, 0]} color={body} rounded />
      <Block size={[w * .77, .15, d * .82]} at={[w * .07, .435, 0]} color={soft} rounded />
      <Block size={[w * .2, .55, d]} at={[-w * .4, .5, 0]} color={body} rounded />
      {[-1, 1].map(sign => <Block key={sign} size={[w, .39, Math.min(.13, d * .13)]}
        at={[0, .42, sign * (d / 2 - Math.min(.065, d * .065))]} color={body} rounded />)}
    </>;
  } else if (k.includes('chair') || k.includes('seat')) {
    contents = <>
      <Block size={[w, .1, d]} at={[0, .44, 0]} color={body} />
      <Block size={[.07, .44, d]} at={[-w / 2 + .035, .68, 0]} color={body} />
      {[-1, 1].flatMap(x => [-1, 1].map(z => <Block key={`${x}${z}`} size={[.05, .4, .05]}
        at={[x * w * .35, .2, z * d * .35]} color={detail} />))}
    </>;
  } else if (k.includes('table') || k.includes('desk') || k.includes('dining')) {
    const h = k.includes('coffee') || k.includes('side') ? .43 : .74;
    contents = <>
      <Block size={[w, .07, d]} at={[0, h, 0]} color={body} />
      {[-1, 1].flatMap(x => [-1, 1].map(z => <Block key={`${x}${z}`} size={[.055, h - .04, .055]}
        at={[x * w * .37, (h - .04) / 2, z * d * .37]} color={detail} />))}
    </>;
  } else if (k.includes('wardrobe') || k.includes('closet') || k.includes('cabinet') || k.includes('fridge') || k.includes('refrigerator')) {
    contents = <>
      <Block size={[w, 1.9, d]} at={[0, .95, 0]} color={body} />
      <Block size={[.015, 1.62, .018]} at={[w / 2 + .009, .93, 0]} color={detail} />
    </>;
  } else if (k.includes('toilet') || k === 'wc' || k.includes('commode')) {
    contents = <>
      <Block size={[w * .43, .4, d * .82]} at={[-w * .27, .2, 0]} color={m.cap} />
      <mesh position={[w * .12, .33, 0]} rotation={[0, 0, Math.PI / 2]} castShadow>
        <cylinderGeometry args={[d * .42, d * .34, Math.max(.15, w * .57), 16]} />
        <meshStandardMaterial color={soft} roughness={.55} />
      </mesh>
    </>;
  } else if (k.includes('basin') || k.includes('sink')) {
    contents = <>
      <Block size={[w, .76, d]} at={[0, .38, 0]} color={body} />
      <Block size={[w * .67, .035, d * .65]} at={[0, .78, 0]} color={soft} />
      <Block size={[.025, .14, .025]} at={[-w * .34, .86, 0]} color={detail} />
    </>;
  } else if (k.includes('car') || k.includes('vehicle')) {
    contents = <>
      <Block size={[w, .47, d]} at={[0, .44, 0]} color={body} rounded />
      <Block size={[w * .55, .38, d * .87]} at={[-w * .04, .85, 0]} color={detail} rounded />
      {[-1, 1].flatMap(x => [-1, 1].map(z => <mesh key={`${x}${z}`}
        position={[x * w * .31, .24, z * d * .48]} rotation={[Math.PI / 2, 0, 0]}>
        <cylinderGeometry args={[.22, .22, .1, 12]} /><meshStandardMaterial color={detail} roughness={1} />
      </mesh>))}
    </>;
  } else if (k.includes('shower')) {
    // The plan's V marks spray direction, not a solid fixture or a bath tub.
    const back = -d / 2 + .055, arm = Math.min(.32, d * .4);
    contents = <>
      <Block size={[w, .025, d]} at={[0, .0125, 0]} color={soft} />
      <Block size={[.025, 1.6, .025]} at={[0, 1.3, back]} color={detail} />
      <Block size={[.025, .025, arm]} at={[0, 2.1, back + arm / 2]} color={detail} />
      <mesh position={[0, 2.075, back + arm]} castShadow>
        <cylinderGeometry args={[.09, .09, .035, 16]} />
        <meshStandardMaterial color={detail} roughness={.5} />
      </mesh>
      <mesh position={[0, .028, d * .15]}>
        <cylinderGeometry args={[.035, .035, .008, 12]} />
        <meshStandardMaterial color={detail} roughness={.7} />
      </mesh>
    </>;
  } else if (k.includes('bath') || k.includes('tub')) {
    contents = <>
      <Block size={[w, .48, d]} at={[0, .24, 0]} color={soft} />
      <Block size={[w * .83, .015, d * .72]} at={[0, .49, 0]} color={body} />
    </>;
  } else if (k.includes('counter') || k.includes('kitchen') || k.includes('stove') || k.includes('hob') || k.includes('washer') || k.includes('washing')) {
    contents = <>
      <Block size={[w, .82, d]} at={[0, .41, 0]} color={body} />
      <Block size={[w, .035, d]} at={[0, .84, 0]} color={soft} />
    </>;
  } else {
    // Unknown symbols retain their true footprint rather than receiving invented furnishings.
    contents = <Block size={[w, .45, d]} at={[0, .225, 0]} color={body} />;
  }
  return <group position={[item.center.x, .025, item.center.y]} rotation={[0, worldRotation(item.rotationDegrees), 0]}>
    {contents}
  </group>;
}

/** Native flight solids retain the accepted footprint, risers and landings. Older scenes keep schematic marks. */
export function Stair({ stair, materials: m }: { stair: PlanStair; materials: Materials }) {
  const lines = useMemo(() => new BufferGeometry().setAttribute('position', new Float32BufferAttribute(stairTreadSegments(stair.polygons, stair.steps), 3)), [stair]);
  useEffect(() => () => lines.dispose(), [lines]);
  if (stair.parts?.length) return <group>
    {stair.parts.map((part, i) => <group key={i}>{part.polygons.map((polygon, j) => <group key={j}>
      <PolygonSolid polygon={polygon} height={part.heightMetres} bottom={part.bottomMetres} color={m.furnishing} line={m.detail} />
      <PolygonSolid polygon={polygon} height={.008} bottom={part.bottomMetres + part.heightMetres} color={m.soft} />
    </group>)}</group>)}
  </group>;
  return <group>
    {stair.polygons.map((p, i) => <PolygonSolid key={i} polygon={p} height={.14} bottom={.025} color={m.furnishing} />)}
    <lineSegments geometry={lines}><lineBasicMaterial color={m.detail} transparent opacity={.65} /></lineSegments>
  </group>;
}
