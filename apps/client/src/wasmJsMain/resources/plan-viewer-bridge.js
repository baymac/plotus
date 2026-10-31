/* The small, same-origin bridge is eager; React and WebGL load only on opening 3D. */
(() => {
  const script = document.currentScript;
  const base = new URL(script?.src || "plan-viewer-bridge.js", document.baseURI);
  const viewerURL = new URL("plan-viewer/viewer.js", base);
  const version = base.searchParams.get("v");
  if (version) viewerURL.searchParams.set("v", version);
  const states = new WeakMap();
  let modulePromise;
  let fontPromise;
  let importAttempt = 0;

  function loadModule() {
    if (!modulePromise) {
      const url = new URL(viewerURL);
      if (importAttempt++ > 0) url.searchParams.set("retry", String(importAttempt));
      modulePromise = import(url.href).catch((error) => {
        modulePromise = undefined;
        throw error;
      });
    }
    return modulePromise;
  }

  function loadFont() {
    if (!fontPromise && typeof FontFace !== "undefined") {
      const url = new URL("composeResources/com.plotus.resources/font/jakarta_regular.ttf", base);
      fontPromise = new FontFace("Plotus Jakarta", `url(${JSON.stringify(url.href)})`)
        .load().then((font) => document.fonts.add(font)).catch(() => {});
    }
  }

  function showState(element, state, message, retry) {
    if (state.disposed) return;
    const dark = state.input.theme === "dark";
    const panel = document.createElement("div");
    panel.style.cssText = `height:100%;display:flex;box-sizing:border-box;align-items:center;justify-content:center;flex-direction:column;gap:14px;padding:32px;text-align:center;font:14px 'Plotus Jakarta',system-ui,sans-serif;color:${dark ? "#eeeae2" : "#22221e"};background:${dark ? "#141513" : "#fbf9f2"}`;
    panel.setAttribute("role", retry ? "alert" : "status");
    const text = document.createElement("p");
    text.style.margin = "0";
    text.textContent = message;
    panel.appendChild(text);
    if (retry) {
      const button = document.createElement("button");
      button.textContent = "Try again";
      button.style.cssText = "font:inherit;color:inherit;background:transparent;border:1px solid currentColor;border-radius:8px;padding:10px 16px;cursor:pointer";
      button.addEventListener("click", () => start(element, state));
      panel.appendChild(button);
    }
    element.replaceChildren(panel);
  }

  function start(element, state) {
    if (state.disposed) return;
    state.handle?.dispose();
    state.handle = undefined;
    const attempt = ++state.attempt;
    showState(element, state, "Opening 3D view…", false);
    loadFont();
    loadModule().then((module) => {
      if (state.disposed || state.attempt !== attempt || states.get(element) !== state) return;
      element.replaceChildren();
      state.handle = module.mountPlanViewer(element, state.input);
    }).catch(() => {
      if (!state.disposed && state.attempt === attempt) {
        showState(element, state, "The 3D view couldn’t load. Check your connection and try again.", true);
      }
    });
  }

  const bridge = {
    mount(element, payload) {
      bridge.dispose(element);
      const state = { input: JSON.parse(payload), payload, handle: undefined, disposed: false, attempt: 0 };
      states.set(element, state);
      start(element, state);
    },
    update(element, payload) {
      const state = states.get(element);
      if (!state || state.disposed) return;
      const input = JSON.parse(payload);
      if (state.payload === payload) return;
      state.payload = payload;
      state.input = input;
      if (state.handle) {
        try { state.handle.update(input); }
        catch (_) {
          state.handle.dispose();
          state.handle = undefined;
          showState(element, state, "This floor’s 3D view couldn’t be opened. Try reopening the plan.", true);
        }
      }
    },
    dispose(element) {
      const state = states.get(element);
      if (!state) return;
      state.disposed = true;
      state.attempt++;
      state.handle?.dispose();
      states.delete(element);
      element.replaceChildren();
    },
  };
  window.PlotusPlanViewerBridge = Object.freeze(bridge);
})();
