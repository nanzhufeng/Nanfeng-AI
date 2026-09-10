import assert from 'node:assert/strict'
import test from 'node:test'
import { assertGoogleusercontentUrl, requireImageResponse, resolveVerifiedRedirect, verifiedGoogleAvatarUrl } from './policy.mjs'

test('only a verified Google identity avatar on a googleusercontent subdomain is accepted', () => {
  const identities = [{ provider: 'google', identity_data: { picture: 'https://lh3.googleusercontent.com/a-/safe' } }]
  assert.equal(verifiedGoogleAvatarUrl(identities), 'https://lh3.googleusercontent.com/a-/safe')
  for (const value of ['https://googleusercontent.com/a', 'https://googleusercontent.com@evil.invalid/a', 'http://lh3.googleusercontent.com/a', 'https://127.0.0.1/a']) {
    assert.throws(() => assertGoogleusercontentUrl(value))
  }
  assert.throws(() => verifiedGoogleAvatarUrl([{ provider: 'google', identity_data: { picture: 'https://evil.invalid/a' } }]))
})

test('redirects are revalidated and response limits are fail-closed', () => {
  assert.equal(resolveVerifiedRedirect('/next', new URL('https://lh3.googleusercontent.com/a')).hostname, 'lh3.googleusercontent.com')
  assert.throws(() => resolveVerifiedRedirect('https://evil.invalid/a', new URL('https://lh3.googleusercontent.com/a')))
  requireImageResponse('image/png', 1024, 1024)
  assert.throws(() => requireImageResponse('text/html', 10, 10))
  assert.throws(() => requireImageResponse('image/png', 2 * 1024 * 1024 + 1, 1))
})

test('bounded reader rejects declared oversize before reading and cancels unknown or false lengths at the limit', async () => {
  const { readBoundedImage, MAX_BYTES } = await import('./policy.mjs')
  for (const declared of [MAX_BYTES + 1, null, 1]) {
    let reads = 0; let cancelled = false
    const body = new ReadableStream({
      pull(controller) { reads++; controller.enqueue(new Uint8Array(1024 * 1024)) },
      cancel() { cancelled = true },
    }, { highWaterMark: 0 })
    await assert.rejects(readBoundedImage(body, 'image/png', declared))
    assert.equal(reads, declared === MAX_BYTES + 1 ? 0 : 3)
    assert.equal(cancelled, true)
  }
})

test('bounded reader accepts exact limit and rejects empty, invalid type, and interrupted streams', async () => {
  const { readBoundedImage, MAX_BYTES } = await import('./policy.mjs')
  const data = new Uint8Array(MAX_BYTES).fill(7)
  const bytes = await readBoundedImage(new Response(data).body, 'image/png', MAX_BYTES)
  assert.deepEqual(bytes, data)
  await assert.rejects(readBoundedImage(new Response('').body, 'image/png', null))
  await assert.rejects(readBoundedImage(new Response('x').body, 'text/html', 1))
  await assert.rejects(readBoundedImage(new ReadableStream({ start(c) { c.error(new Error('interrupted')) } }), 'image/png', null))
})
