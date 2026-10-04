import type { Envelope, TaskDetail } from '../src/schema/types'

export declare function escapeHtml(s: string): string
export declare function renderDetailBody(d: TaskDetail): string
export declare function renderApiGuide(date: string): string
export declare function renderPage(envelope: Envelope, options?: { archive?: boolean; dates?: string[] }): string
