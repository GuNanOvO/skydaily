import { IMAGE_BLACKLIST, LINK_BLACKLIST } from '../config.ts'

export function keepImage(src: string): boolean {
  return !IMAGE_BLACKLIST.some((frag) => src.includes(frag))
}

export function isAdKeyword(text: string): boolean {
  return LINK_BLACKLIST.some((w) => text.includes(w))
}

export function isNoise(line: string): boolean {
  return /===|点个赞|如果上面内容有帮到你|看不了图片/.test(line)
}
