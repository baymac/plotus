import assert from "node:assert/strict";
import { once } from "node:events";
import { test } from "node:test";
import { createApp } from "../src/app.mjs";

test("health works while unimplemented private routes never return sample user data", async () => {
  const app = createApp();
  app.listen(0, "127.0.0.1");
  await once(app, "listening");
  try {
    const base = `http://127.0.0.1:${app.address().port}`;
    assert.equal((await fetch(`${base}/health`)).status, 200);
    for (const route of ["/v1/projects", "/v1/session"]) {
      const response = await fetch(`${base}${route}`);
      assert.equal(response.status, 501);
      assert.equal((await response.json()).error.code, "NOT_IMPLEMENTED");
    }
    assert.equal((await fetch(`${base}/missing`)).status, 404);
  } finally {
    await new Promise((resolve) => app.close(resolve));
  }
});
