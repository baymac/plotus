import { cp, mkdir, readFile, writeFile, rm, access } from "node:fs/promises";
import { createHash } from "node:crypto";
import { resolve } from "node:path";

const root = resolve(import.meta.dirname, "../..");
const appIndex = process.argv.indexOf("--app-dist");
const app = appIndex < 0 ? resolve(root, "apps/client/build/dist/wasmJs/productionExecutable") : resolve(process.argv[appIndex + 1]);
const out = resolve(root, "docker-cloud/dist");
await access(resolve(app, "plotus.js"));
await access(resolve(root, "apps/marketing/dist/index.html"));
await rm(out, { recursive: true, force: true });
await mkdir(out, { recursive: true });
await cp(resolve(root, "apps/marketing/dist"), out, { recursive: true });
await cp(app, resolve(out, "_app"), { recursive: true });
const versionHash = createHash("sha256");
for (const file of ["plotus.js", "plan-viewer-bridge.js", "plan-viewer/viewer.js"]) {
  versionHash.update(await readFile(resolve(app, file)));
}
const version = versionHash.digest("hex").slice(0,12);
let html = await readFile(resolve(out, "_app/index.html"), "utf8");
if (!html.includes('src="plotus.js"') || !html.includes('href="/favicon.png"')) throw new Error("Unexpected app entry format");
html = html.replace("</head>", '    <base href="/_app/">\n</head>')
  .replace('src="plotus.js"', `src="/_app/plotus.js?v=${version}"`)
  .replace('src="plan-viewer-bridge.js"', `src="/_app/plan-viewer-bridge.js?v=${version}"`)
  .replace('href="/favicon.png"', 'href="/_app/favicon.png"');
await writeFile(resolve(out, "_app/index.html"), html);
await cp(resolve(root, "docker-cloud/edge-worker.mjs"), resolve(out, "_worker.js"));
await writeFile(resolve(out, "_routes.json"), JSON.stringify({ version: 1,
  include: ["/projects*", "/settings*", "/login*", "/api/auth/*", "/v1/*", "/.well-known/jwks.json", "/_app", "/_app/", "/_app/index.html"], exclude: [] }, null, 2));
console.log("Built the Plotus landing site, protected app and Pages gateway in " + out);
