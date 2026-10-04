export interface Env {
  SKYDAILY_KV: KVNamespace
  UPDATE_COORDINATOR: DurableObjectNamespace
  API_RATE_LIMIT: RateLimit

  NETEASE_TOKEN_API: string
  NETEASE_TASK_API: string
  NETEASE_TASK_ORIGIN: string
  NETEASE_TASK_REFERER: string
  NETEASE_USER_AGENT: string

  SKY_UID: string
  SKY_GAME_UID: string
  SKY_GAME_SERVER: string

  ADMIN_SECRET: string
  GITHUB_REPOSITORY?: string
  GITHUB_DISPATCH_TOKEN?: string
  HISTORY_BASE_URL?: string

  REFRESH_JITTER_MS?: string
}
