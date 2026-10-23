import type { Envelope, TaskDetail } from '../src/schema/types'
import type { AppRelease } from '../src/app-release.mjs'

export declare function escapeHtml(s: string): string
export declare function renderDetailBody(d: TaskDetail): string
export declare function renderApiGuide(date: string): string
export declare function renderPage(envelope: Envelope, options?: { archive?: boolean; dates?: string[] }): string
export declare function renderAppPage(release: AppRelease | null, options?: { assets?: string }): string
export declare function integrateAppRelease(options?: { root?: string; bundle?: string | null }): Promise<{ release: AppRelease | null; releases: number }>
