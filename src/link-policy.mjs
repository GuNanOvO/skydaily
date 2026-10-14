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

export const noticeLinkPolicy = policy.noticeLinks
const noticeHosts = new Set(noticeLinkPolicy.hosts)

export function normalizeNoticeLink(value) {
  try {
    const url = new URL(value)
    if (url.protocol !== 'https:' || url.port || url.username || url.password) return null
    if (!noticeHosts.has(url.hostname)) return null
    return url.origin + (url.pathname === '' ? '/' : url.pathname)
  } catch { return null }
}
