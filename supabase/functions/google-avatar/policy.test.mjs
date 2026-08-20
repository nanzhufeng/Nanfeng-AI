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
