import { mountPlanViewer } from '../src/index';
import { sampleScene } from './sample';

const root = document.querySelector<HTMLElement>('#viewer')!;
const theme = new URLSearchParams(location.search).get('theme') === 'dark' ? 'dark' : 'light';
export const viewer = mountPlanViewer(root, { scene: sampleScene, theme });
window.addEventListener('pagehide', () => viewer.dispose(), { once: true });
