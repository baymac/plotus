import test from "node:test";
import assert from "node:assert/strict";
import worker from "../edge-worker.mjs";

const env = { PUBLIC_ORIGIN: "https://plotus.app", PLOTUS_BACKEND_ORIGIN: "https://origin.example.invalid/plotus",
  ASSETS: { fetch: async request => new Response(new URL(request.url).pathname, { headers: { "Content-Type": "text/html", "Content-Security-Policy": "default-src 'none'" } }) } };
const request = (path, options) => new Request("https://plotus.app" + path, options);

test("every app destination redirects anonymous requests without exposing app HTML", async () => {
  for (const path of ["/projects", "/settings", "/settings/general", "/settings/account"]) {
    const response = await worker.fetch(request(path), env);
    assert.equal(response.status, 302);
    assert.equal(response.headers.get("Location"), "/login?next=" + encodeURIComponent(path));
    assert.equal(response.headers.get("Cache-Control"), "private, no-store");
    assert.equal(await response.text(), "");
  }
});

test("login serves the namespaced entry with the Compose CSP and no cache", async () => {
  const response = await worker.fetch(request("/login"), env);
  assert.equal(response.status, 200);
  assert.equal(await response.text(), "/_app/");
  assert.match(response.headers.get("Content-Security-Policy"), /wasm-unsafe-eval/);
  assert.equal(response.headers.get("Cache-Control"), "private, no-store");
});

test("valid and revoked sessions are verified by the backend, and failures stay closed", async t => {
  let status = 200;
  t.mock.method(globalThis, "fetch", async (url, options) => {
    assert.equal(url.href, "https://origin.example.invalid/plotus/api/auth/token/csrf");
    assert.equal(options.headers.Origin, "https://plotus.app");
    return new Response(null, { status });
  });
  const cookie = { headers: { Cookie: "__Host-plotus_refresh=fixture" } };
  const response = await worker.fetch(request("/settings/account", cookie), env);
  assert.equal(response.status, 200);
  assert.equal(await response.text(), "/_app/");
  for (const next of ["https://example.invalid", "//example.invalid", "/login"]) {
    const signedIn = await worker.fetch(request("/login?next=" + encodeURIComponent(next), cookie), env);
    assert.equal(signedIn.headers.get("Location"), "/projects");
  }
  const returning = await worker.fetch(request("/login?next=/settings/account", cookie), env);
  assert.equal(returning.headers.get("Location"), "/settings/account");
  status = 401;
  assert.equal((await worker.fetch(request("/projects", cookie), env)).status, 302);
  status = 503;
  assert.equal((await worker.fetch(request("/projects", cookie), env)).status, 503);
});

test("API proxy preserves method, proof, body and manual OAuth redirects", async t => {
  t.mock.method(globalThis, "fetch", async (target, options) => {
    assert.equal(target.url, "https://origin.example.invalid/plotus/api/auth/login/google");
    assert.equal(target.method, "POST");
    assert.equal(target.headers.get("Origin"), "https://plotus.app");
    assert.equal(target.headers.get("Cookie"), "plotus_refresh=fixture");
    assert.equal(await target.text(), '{"redirect":"/login"}');
    assert.equal(options.redirect, "manual");
    return new Response(null, { status: 302, headers: { Location: "https://accounts.google.com/" } });
  });
  const response = await worker.fetch(request("/api/auth/login/google", { method: "POST", headers: { Origin: "https://plotus.app", Cookie: "plotus_refresh=fixture" }, body: '{"redirect":"/login"}' }), env);
  assert.equal(response.status, 302);
  assert.equal(response.headers.get("Location"), "https://accounts.google.com/");
});

test("public pages keep their asset response and aliases cannot use the auth API", async () => {
  assert.equal(await (await worker.fetch(request("/privacy/"), env)).text(), "/privacy/");
  const alias = await worker.fetch(new Request("https://plotus-landing.pages.dev/settings/account"), env);
  assert.equal(alias.headers.get("Location"), "https://plotus.app/settings/account");
  assert.equal((await worker.fetch(new Request("https://plotus-landing.pages.dev/api/auth/config"), env)).status, 403);
  assert.equal((await worker.fetch(request("/_app/index.html"), env)).headers.get("Location"), "/projects");
});

test("project plot deep links are protected and survive the sign-in return", async t => {
  const path="/projects/c4f872f5-7c67-4f4e-aef9-b5e67f01ca2a/plot/roads";
  const anonymous=await worker.fetch(request(path),env);
  assert.equal(anonymous.status,302);
  assert.equal(anonymous.headers.get("Location"),"/login?next="+encodeURIComponent(path));
  t.mock.method(globalThis,"fetch",async()=>new Response(null,{status:200}));
  const cookie={headers:{Cookie:"__Host-plotus_refresh=fixture"}};
  assert.equal((await worker.fetch(request(path,cookie),env)).status,200);
  const returned=await worker.fetch(request("/login?next="+encodeURIComponent(path),cookie),env);
  assert.equal(returned.headers.get("Location"),path);
  const unknown=await worker.fetch(request("/projects/not-a-project/plot/roads"),env);
  assert.equal(await unknown.text(),"/projects/not-a-project/plot/roads");
});


test("the project overview has the same session gate as its sheets",async t=>{
  const path="/projects/c4f872f5-7c67-4f4e-aef9-b5e67f01ca2a/plot";
  const anonymous=await worker.fetch(request(path),env);
  assert.equal(anonymous.status,302);
  assert.equal(anonymous.headers.get("Location"),"/login?next="+encodeURIComponent(path));
  t.mock.method(globalThis,"fetch",async()=>new Response(null,{status:200}));
  const cookie={headers:{Cookie:"__Host-plotus_refresh=fixture"}};
  assert.equal((await worker.fetch(request(path,cookie),env)).status,200);
  assert.equal((await worker.fetch(request("/login?next="+encodeURIComponent(path),cookie),env)).headers.get("Location"),path);
});

test("floor-plan sections keep their protected path through sign-in",async t=>{
  const root="/projects/c4f872f5-7c67-4f4e-aef9-b5e67f01ca2a/floor-plan";
  const paths=[root,root+"/setbacks",root+"/requirements",root+"/history",root+"/history/bba14508-f29e-4050-8ca3-2ca31b09a4ec"];
  for(const path of paths) {
    const anonymous=await worker.fetch(request(path),env);
    assert.equal(anonymous.status,302);
    assert.equal(anonymous.headers.get("Location"),"/login?next="+encodeURIComponent(path));
  }
  t.mock.method(globalThis,"fetch",async()=>new Response(null,{status:200}));
  const cookie={headers:{Cookie:"__Host-plotus_refresh=fixture"}};
  for(const path of paths) {
    assert.equal((await worker.fetch(request(path,cookie),env)).status,200);
    assert.equal((await worker.fetch(request("/login?next="+encodeURIComponent(path),cookie),env)).headers.get("Location"),path);
  }
  for(const path of [root+"/history/not-a-plan",root+"/requirements/bba14508-f29e-4050-8ca3-2ca31b09a4ec"]) {
    assert.equal((await worker.fetch(request("/login?next="+encodeURIComponent(path),cookie),env)).headers.get("Location"),"/projects");
  }
});

const exportPath="/v1/projects/c4f872f5-7c67-4f4e-aef9-b5e67f01ca2a/floor-plan/generations/bba14508-f29e-4050-8ca3-2ca31b09a4ec/drawings/floor-0/export/";
test("a deliberate download gets a genuine private attachment through the live session and owned API",async t=>{
  const calls=[];
  t.mock.method(globalThis,"fetch",async(input,options)=>{
    const path=input instanceof Request?new URL(input.url).pathname:input.pathname;calls.push(path);
    if(path.endsWith('/token/csrf')) {
      assert.equal(options.headers.Cookie,"__Host-plotus_refresh=fixture");
      assert.equal(options.headers.Origin,"https://plotus.app");
      return Response.json({csrf_token:"fixture-csrf"});
    }
    if(path.endsWith('/token/access')) {
      assert.equal(options.headers['X-CSRF-Token'],"fixture-csrf");
      return Response.json({access_token:"fixture-access"});
    }
    assert.equal(input.headers.get('Authorization'),'Bearer fixture-access');
    assert.equal(input.headers.get('Cookie'),null);
    assert.equal(input.headers.get('Origin'),'https://plotus.app');
    assert.equal(input.headers.get('Accept'),'application/json');
    assert.equal(new URL(input.url).search,'');
    return Response.json({filename:'plotus-floor-plan.pdf',mimeType:'application/pdf',base64:btoa('%PDF-test')});
  });
  const response=await worker.fetch(request(exportPath+'pdf?download=1',{headers:{Cookie:'__Host-plotus_refresh=fixture','Sec-Fetch-Site':'same-origin'}}),env);
  assert.equal(response.status,200);assert.equal(await response.text(),'%PDF-test');
  assert.equal(response.headers.get('Content-Type'),'application/pdf');
  assert.equal(response.headers.get('Content-Disposition'),'attachment; filename="plotus-floor-plan.pdf"');
  assert.equal(response.headers.get('Cache-Control'),'private, no-store');
  assert.equal(response.headers.get('Authorization'),null);assert.equal(response.headers.get('Set-Cookie'),null);
  assert.equal(calls.length,3);
});

test("anonymous, cross-site and non-GET downloads cannot trigger account requests",async t=>{
  t.mock.method(globalThis,"fetch",()=>{assert.fail('Untrusted download reached the backend');});
  const path=exportPath+'pdf?download=1';
  assert.equal((await worker.fetch(request(path),env)).status,401);
  const cookie={Cookie:'__Host-plotus_refresh=fixture'};
  assert.equal((await worker.fetch(request(path,{headers:{...cookie,'Sec-Fetch-Site':'cross-site'}}),env)).status,403);
  assert.equal((await worker.fetch(request(path,{headers:{...cookie,Origin:'https://elsewhere.invalid'}}),env)).status,403);
  assert.equal((await worker.fetch(request(path,{method:'POST',headers:cookie}),env)).status,405);
});

test("revoked sessions and another owner's plan fail closed without an attachment",async t=>{
  let status=401,ownedRequests=0;
  t.mock.method(globalThis,'fetch',async(input)=>{
    if(!(input instanceof Request))return new Response(null,{status});
    ownedRequests++;return new Response(null,{status:404});
  });
  const path=exportPath+'pdf?download=1';
  const response=await worker.fetch(request(path,{headers:{Cookie:'__Host-plotus_refresh=fixture'}}),env);
  assert.equal(response.status,401);assert.equal(response.headers.get('Content-Disposition'),null);assert.equal(ownedRequests,0);
  status=503;
  assert.equal((await worker.fetch(request(path,{headers:{Cookie:'__Host-plotus_refresh=fixture'}}),env)).status,503);
  const forbidden=await worker.fetch(request(path,{headers:{Authorization:'Bearer fixture-access'}}),env);
  assert.equal(forbidden.status,404);assert.equal(forbidden.headers.get('Content-Disposition'),null);assert.equal(ownedRequests,1);
});

test("attachment formats and filenames stay safe and malformed files do not claim a download",async t=>{
  let valid=true;
  t.mock.method(globalThis,'fetch',async()=>Response.json({filename:'bad\r\n"name.pdf',mimeType:'text/html',base64:valid?btoa('binary-file'):'%%%'}));
  for(const [format,mime]of [['png','image/png'],['pdf','application/pdf'],['dwg','image/vnd.dwg']]) {
    const response=await worker.fetch(request(exportPath+format+'?download=1',{headers:{Authorization:'Bearer fixture-access'}}),env);
    assert.equal(response.status,200);assert.equal(response.headers.get('Content-Type'),mime);
    assert.equal(response.headers.get('Content-Disposition'),'attachment; filename="bad___name.pdf"');
    assert.equal(await response.text(),'binary-file');
  }
  valid=false;
  const malformed=await worker.fetch(request(exportPath+'pdf?download=1',{headers:{Authorization:'Bearer fixture-access'}}),env);
  assert.equal(malformed.status,503);assert.equal(malformed.headers.get('Content-Disposition'),null);
});
