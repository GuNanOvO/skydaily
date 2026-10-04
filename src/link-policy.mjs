import policy from './link-policy.json' with { type: 'json' }

export const seasonGuidePolicy = policy.seasonGuide
const pathPattern = new RegExp(seasonGuidePolicy.pathPattern)

export function normalizeSeasonGuideLink(value) {
  try {
    const url = new URL(value)
    if (url.origin !== seasonGuidePolicy.origin || url.username || url.password || !pathPattern.test(url.pathname)) return null
    return url.origin + url.pathname.replace(/\/?$/, '/')
  } catch { return null }
}
