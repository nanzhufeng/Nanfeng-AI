import { MAX_BYTES } from "../../../../supabase/functions/google-avatar/policy.mjs";

let handler: (request: Request) => Promise<Response>;
Object.defineProperty(Deno, "serve", { value: (callback: typeof handler) => { handler = callback; } });
Object.defineProperty(Deno.env, "get", { value: (name: string) =>
  name === "SUPABASE_URL" ? "https://synthetic.invalid" : name === "SUPABASE_ANON_KEY" ? "synthetic-public-fixture" : undefined });

await import("../../../../supabase/functions/google-avatar/index.ts");
const outcomes = [];
for (const declared of [true, false]) {
  let consumed = 0;
  const payloadBytes = MAX_BYTES * 4;
  globalThis.fetch = async () => {
    const headers = new Headers({ "content-type": "image/png" });
    if (declared) headers.set("content-length", String(payloadBytes));
    return {
      status: 200, ok: true, headers,
      arrayBuffer: async () => { consumed = payloadBytes; return new ArrayBuffer(payloadBytes); },
    } as Response;
  };
  const response = await handler!(new Request("https://synthetic.invalid/avatar", {
    headers: { authorization: "Bearer synthetic-fixture-only" },
  }));
  outcomes.push({ declaredLength: declared, status: response.status, consumedBytes: consumed,
    limitBytes: MAX_BYTES, readBeforeReject: consumed > MAX_BYTES });
}
console.log(JSON.stringify({network: "mocked; Deno network permission not granted", outcomes}));
if (!outcomes.every(x => x.status === 502 && x.readBeforeReject)) throw new Error("Expected boundary behavior was not reproduced");
