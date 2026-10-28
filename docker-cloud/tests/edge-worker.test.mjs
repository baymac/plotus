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
