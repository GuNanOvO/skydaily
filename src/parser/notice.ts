export interface NoticePayload {
  title: string
  message: string
}

export const NOTICE_TITLE_LIMIT = 200
export const NOTICE_MESSAGE_LIMIT = 20_000

export function normalizeNoticeMessage(message: string): string {
  return message
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/?[a-zA-Z][^<>]{0,200}>/g, '')
    .replace(/[ \t]+$/gm, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

export function parseNotice(raw: unknown): NoticePayload | null {
  if (!raw || typeof raw !== 'object') return null
  const record = raw as Record<string, unknown>
  const title = typeof record.Title === 'string' ? record.Title.trim() : ''
  const rawMessage = typeof record.NeteaseMessage === 'string' ? record.NeteaseMessage : ''
  const message = normalizeNoticeMessage(rawMessage)
  if (!title || title.length > NOTICE_TITLE_LIMIT) return null
  if (!message || message.length > NOTICE_MESSAGE_LIMIT) return null
  return { title, message }
}
