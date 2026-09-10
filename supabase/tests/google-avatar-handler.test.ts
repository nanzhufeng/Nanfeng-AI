import { MAX_BYTES } from "../functions/google-avatar/policy.mjs";
let handler: (request: Request) => Promise<Response>;
Object.defineProperty(Deno, "serve", { value: (callback: typeof handler) => { handler = callback; } });
Object.defineProperty(Deno.env, "get", { value: (name: string) => name === "SUPABASE_URL" ? "https://synthetic.invalid" : "synthetic-fixture" });
await import("../functions/google-avatar/index.ts");
const outcomes = [];
for (const declared of [MAX_BYTES * 4, null, 1]) {
  let reads = 0; let cancelled = false;
  globalThis.fetch = async () => {
    const headers = new Headers({ "content-type": "image/png" });
    if (declared !== null) headers.set("content-length", String(declared));
    return new Response(new ReadableStream({
      pull(c) { reads++; c.enqueue(new Uint8Array(MAX_BYTES / 2)); },
      cancel() { cancelled = true; },
    }, { highWaterMark: 0 }), { headers });
  };
  const response = await handler!(new Request("https://synthetic.invalid/avatar", { headers: { authorization: "Bearer synthetic" } }));
  if (response.status !== 502 || !cancelled || reads !== (declared === MAX_BYTES * 4 ? 0 : 3)) throw new Error("Bounded rejection failed");
  outcomes.push({ declared, status: response.status, reads, cancelled });
}
for (const size of [1, MAX_BYTES]) {
  globalThis.fetch = async () => new Response(new Uint8Array(size).fill(7), { headers: { "content-type": "image/png" } });
  const response = await handler!(new Request("https://synthetic.invalid/avatar", { headers: { authorization: "Bearer synthetic" } }));
  if (response.status !== 200 || (await response.arrayBuffer()).byteLength !== size) throw new Error("Valid image rejected");
  outcomes.push({ size, status: response.status });
}
console.log(JSON.stringify({ network: "mocked; no network permission", outcomes }));
