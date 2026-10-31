import { createRoot } from 'react-dom/client';
import { normaliseInput } from './schema';
import type { ViewerCallbacks, ViewerHandle, ViewerInput } from './schema';
import { PlanViewer, ViewerBoundary, ViewerMessage } from './viewer';
import styles from './styles.css?inline';

export type { PlanScene3D, ViewerInput, ViewerCallbacks, ViewerHandle } from './schema';

/** Mount one isolated React root. Account requests and plan ownership remain with the host app. */
export function mountPlanViewer(container: HTMLElement, initial: ViewerInput, callbacks: ViewerCallbacks = {}): ViewerHandle {
  if (!(container instanceof HTMLElement)) throw new Error('The 3D view needs an HTML container.');
  let input: ViewerInput;
  try { input = normaliseInput(initial); } catch (error) {
    callbacks.onError?.((error as Error).message); throw error;
  }
  const style = document.createElement('style');
  style.dataset.plotusPlanViewer = 'true'; style.textContent = styles;
  const host = document.createElement('div');
  host.style.cssText = 'position:relative;width:100%;height:100%;min-height:0;';
  container.append(style, host);
  const root = createRoot(host);
  let disposed = false;
  const render = () => root.render(<ViewerBoundary key={input.scene.label} onError={callbacks.onError}><PlanViewer input={input} callbacks={callbacks} /></ViewerBoundary>);
  render();
  return {
    update(next) {
      if (disposed) return;
      try { input = normaliseInput(next); render(); } catch (error) {
        root.render(<div className={`pv-root pv-${input.theme}`}><ViewerMessage title="Couldn’t open this 3D view" detail="The saved plan could not be displayed. Switch to 2D to continue reviewing it." /></div>);
        callbacks.onError?.((error as Error).message);
        throw error;
      }
    },
    dispose() {
      if (disposed) return;
      disposed = true; root.unmount(); host.remove(); style.remove();
    },
  };
}
