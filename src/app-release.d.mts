export interface AppRelease {
  schemaVersion: 1
  packageName: 'com.nankki.skydaily'
  versionName: string
  versionCode: number
  commit: string
  publishedAt: string
  minSdk: number
  size: number
  sha256: string
  apkPath: string
  notes: string[]
}

export declare const APP_PACKAGE: 'com.nankki.skydaily'
export declare const APP_MIN_SDK: 28
export declare const APP_MANIFEST_PATH: '/app/latest.json'
export declare const APP_PAGE_PATH: '/app/'
export declare const APP_MAX_APK_BYTES: number
export declare const APP_SCREENSHOT_FILES: readonly ['tasks.webp', 'meals.webp', 'settings.webp']

export declare function releaseObject(value: unknown): AppRelease | null
export declare function releaseNotes(lines: readonly string[]): string[]
export declare function isEligibleAppPath(path: string): boolean
export declare function apkFileFor(release: Pick<AppRelease, 'versionName'>): string
export declare function releaseDir(versionCode: number): string
