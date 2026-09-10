import { createClient } from 'jsr:@supabase/supabase-js@2'
import { readBoundedImage, resolveVerifiedRedirect, verifiedGoogleAvatarUrl } from './policy.mjs'

const timeoutMs = 8_000

function failure(status: number) { return new Response(JSON.stringify({ error: 'avatar unavailable' }), { status, headers: { 'content-type': 'application/json', 'cache-control': 'no-store' } }) }

Deno.serve(async (request) => {
  const authorization = request.headers.get('authorization')
  if (!authorization?.startsWith('Bearer ')) return failure(401)
  const token = authorization.slice('Bearer '.length)
  const url = Deno.env.get('SUPABASE_URL'); const key = Deno.env.get('SUPABASE_ANON_KEY')
  if (!url || !key) return failure(503)
  const auth = createClient(url, key, { auth: { persistSession: false, autoRefreshToken: false } })
  const { data, error } = await auth.auth.getUser(token)
  if (error || !data.user) return failure(401)
  let target: URL
  try { target = new URL(verifiedGoogleAvatarUrl(data.user.identities)) } catch { return failure(403) }
  const controller = new AbortController(); const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    for (let hop = 0; hop <= 3; hop += 1) {
      const response = await fetch(target, { redirect: 'manual', signal: controller.signal })
      if (response.status >= 300 && response.status < 400) { await response.body?.cancel(); target = resolveVerifiedRedirect(response.headers.get('location'), target); continue }
      if (!response.ok) { await response.body?.cancel(); return failure(502) }
      const contentType = response.headers.get('content-type'); const declared = response.headers.get('content-length')
      const bytes = await readBoundedImage(response.body, contentType, declared === null ? null : Number(declared))
      return new Response(bytes, { status: 200, headers: { 'content-type': contentType!, 'cache-control': 'private, max-age=300', 'x-content-type-options': 'nosniff' } })
    }
    return failure(502)
  } catch { return failure(502) } finally { clearTimeout(timer) }
})
