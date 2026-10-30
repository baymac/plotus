const appPaths = new Set(["/projects", "/settings", "/settings/general", "/settings/account"]);
const uuid = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
const projectPath = new RegExp(`^/projects/${uuid}/(?:plot(?:/(boundary|direction|roads))?|floor-plan(?:/(setbacks|requirements|history(?:/${uuid})?))?)$`);
const planExportPath = new RegExp(`^/v1/projects/${uuid}/floor-plan/generations/${uuid}/drawings/[A-Za-z0-9_-]{1,128}/export/(png|pdf|dwg)$`);
const isAppPath = path => appPaths.has(path) || projectPath.test(path);
const entryPaths = new Set(["/login", "/_app", "/_app/", "/_app/index.html"]);
const appCsp = "default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; worker-src 'self' blob:; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'; upgrade-insecure-requests";

function privateResponse(body, status = 503) {
  return new Response(body, { status, headers: { "Cache-Control": "private, no-store", "Content-Type": "text/plain; charset=utf-8", "X-Content-Type-Options": "nosniff" } });
}

function redirect(target) {
  return new Response(null, { status: 302, headers: { Location: target, "Cache-Control": "private, no-store" } });
}

function backendUrl(env, path, search = "") {
  const target = new URL(env.PLOTUS_BACKEND_ORIGIN);
  if (target.protocol !== "https:" || target.username || target.password || target.search || target.hash) throw new Error("Invalid backend origin");
  target.pathname = target.pathname.replace(/\/$/, "") + path;
  target.search = search;
  return target;
}

async function sessionState(request, env, origin) {
  if (!/(?:^|;\s*)(?:__Host-)?plotus_refresh=/.test(request.headers.get("Cookie") || "")) return false;
  const response = await fetch(backendUrl(env, "/api/auth/token/csrf"), {
    headers: { Cookie: request.headers.get("Cookie"), Origin: origin },
    redirect: "manual", signal: AbortSignal.timeout(10000),
  });
  if (response.ok) return true;
  if (response.status === 401 || response.status === 403) return false;
  throw new Error("Session service unavailable");
}

async function downloadAttachment(request,env,origin,url,format) {
  const site=request.headers.get("Sec-Fetch-Site"),suppliedOrigin=request.headers.get("Origin");
  if(request.method!=="GET")return privateResponse("Method not allowed.",405);
  if((site && site!=="same-origin" && site!=="none") || (suppliedOrigin && suppliedOrigin!==origin))return privateResponse("Open this download from Plotus.",403);
  const target=backendUrl(env,url.pathname);const headers=new Headers(request.headers);
  if(!headers.has("Authorization")) {
    const cookie=request.headers.get("Cookie") || "";
    if(!/(?:^|;\s*)(?:__Host-)?plotus_refresh=/.test(cookie))return privateResponse("Sign in to download this plan.",401);
    const authHeaders={Cookie:cookie,Origin:origin};
    const csrf=await fetch(backendUrl(env,"/api/auth/token/csrf"),{headers:authHeaders,redirect:"manual",signal:AbortSignal.timeout(10000)});
    if(!csrf.ok)return privateResponse("Sign in to download this plan.",csrf.status===401||csrf.status===403?401:503);
    const proof=(await csrf.json()).csrf_token;
    if(typeof proof!=="string"||!proof)throw new Error("Invalid session proof");
    const access=await fetch(backendUrl(env,"/api/auth/token/access"),{headers:{...authHeaders,"X-CSRF-Token":proof},redirect:"manual",signal:AbortSignal.timeout(10000)});
    if(!access.ok)return privateResponse("Sign in to download this plan.",access.status===401||access.status===403?401:503);
    const token=(await access.json()).access_token;
    if(typeof token!=="string"||!token)throw new Error("Invalid session access");
    headers.set("Authorization","Bearer "+token);
  }
  headers.delete("Cookie");headers.set("Origin",origin);headers.set("Host",target.host);
  headers.set("Accept","application/json");
  headers.set("X-Forwarded-Host",url.host);
  const response=await fetch(new Request(target,{method:"GET",headers}),{redirect:"manual",signal:AbortSignal.timeout(25000)});
  if(!response.ok)return privateResponse("This plan could not be downloaded. Return to Plotus and try again.",response.status);
  const file=await response.json();
  if(typeof file.base64!=="string" || file.base64.length>32*1024*1024 || typeof file.filename!=="string")throw new Error("Invalid export");
  const raw=atob(file.base64),bytes=Uint8Array.from(raw,c=>c.charCodeAt(0));
  const filename=file.filename.replace(/[^a-zA-Z0-9._-]/g,"_").slice(-120)||`plotus-floor-plan.${format}`;
  return new Response(bytes,{headers:{"Content-Type":{png:"image/png",pdf:"application/pdf",dwg:"image/vnd.dwg"}[format],
    "Content-Disposition":`attachment; filename="${filename}"`,"Cache-Control":"private, no-store","X-Content-Type-Options":"nosniff","Referrer-Policy":"no-referrer"}});
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname.replace(/\/$/, "") || "/";
    const api = path.startsWith("/api/auth/") || path.startsWith("/v1/") || path === "/.well-known/jwks.json";
    const app = isAppPath(path) || entryPaths.has(url.pathname) || path === "/login";
    if (!api && !app) return env.ASSETS.fetch(request);
    const origin = env.PUBLIC_ORIGIN || "https://plotus.app";
    if (url.origin !== origin) {
      if (api) return privateResponse("Use the Plotus application origin.", 403);
      return redirect(new URL(url.pathname + url.search, origin).href);
    }

    if (api) {
      try {
        const exported=planExportPath.exec(path);
        if(exported&&url.searchParams.get("download")==="1")return await downloadAttachment(request,env,origin,url,exported[1]);
        const target = backendUrl(env, url.pathname, url.search);
        const forwarded = new Request(target, request);
        forwarded.headers.set("Host", target.host);
        forwarded.headers.set("X-Forwarded-Host", url.host);
        return await fetch(forwarded, { redirect: "manual", signal: AbortSignal.timeout(25000) });
      } catch (_) { return privateResponse("Plotus is temporarily unavailable. Please try again."); }
    }
    if (request.method !== "GET" && request.method !== "HEAD") return privateResponse("Method not allowed.", 405);
    if (path.startsWith("/_app")) return redirect("/projects");
    if (url.pathname !== path) return redirect(path + url.search);
    let signedIn;
    try { signedIn = await sessionState(request, env, origin); }
    catch (_) { return privateResponse("Sign-in is temporarily unavailable. Please try again."); }

    if (!signedIn && path !== "/login") return redirect("/login?next=" + encodeURIComponent(path));
    if (signedIn && path === "/login") {
      const next = url.searchParams.get("next");
      return redirect(typeof next === "string" && isAppPath(next) ? next : "/projects");
    }

    const asset = await env.ASSETS.fetch(new Request(new URL("/_app/", url), { method: request.method }));
    if (!asset.ok) return privateResponse("The Plotus application is temporarily unavailable.");
    const headers = new Headers(asset.headers);
    headers.set("Content-Security-Policy", appCsp);
    headers.set("Cache-Control", "private, no-store");
    headers.set("X-Frame-Options", "DENY");
    headers.set("X-Content-Type-Options", "nosniff");
    headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
    headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
    return new Response(asset.body, { status: asset.status, headers });
  },
};
