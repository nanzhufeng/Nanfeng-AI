const MAX_BYTES = 2 * 1024 * 1024

export function verifiedGoogleAvatarUrl(identities) {
  const google = Array.isArray(identities) ? identities.find((value) => value?.provider === 'google') : null
  const metadata = google?.identity_data
  const candidate = typeof metadata?.picture === 'string' ? metadata.picture : metadata?.avatar_url
  return assertGoogleusercontentUrl(candidate).toString()
}

export function assertGoogleusercontentUrl(value) {
  if (typeof value !== 'string' || value.length === 0 || value.length > 2048) throw new Error('avatar source rejected')
  const url = new URL(value)
  const hostname = url.hostname.toLowerCase()
  if (url.protocol !== 'https:' || url.username || url.password || url.port || !hostname.endsWith('.googleusercontent.com')) throw new Error('avatar source rejected')
  return url
}

export function resolveVerifiedRedirect(location, current) {
  if (typeof location !== 'string' || location.length === 0) throw new Error('avatar redirect rejected')
  return assertGoogleusercontentUrl(new URL(location, current).toString())
}

export function requireImageResponse(contentType, contentLength, actualBytes) {
  if (typeof contentType !== 'string' || !contentType.toLowerCase().startsWith('image/')) throw new Error('avatar content rejected')
  if (contentLength != null && (!Number.isSafeInteger(contentLength) || contentLength < 0 || contentLength > MAX_BYTES)) throw new Error('avatar content rejected')
  if (!Number.isSafeInteger(actualBytes) || actualBytes < 1 || actualBytes > MAX_BYTES) throw new Error('avatar content rejected')
}

export { MAX_BYTES }
