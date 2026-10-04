import { z } from 'zod'

export const taskSchema = z.object({
  number: z.number().int().min(1).max(4),
  text: z.string().trim().min(1),
  keyword: z.string().nullable(),
})

export const detailSectionSchema = z.object({
  label: z.string(),
  text: z.string(),
})

export const taskDetailSchema = z.object({
  keyword: z.string(),
  title: z.string(),
  sections: z.array(detailSectionSchema),
  images: z.array(z.string().url()),
  videos: z.array(z.string().url()),
  links: z.array(z.string().url()),
  text: z.string(),
})

export const weatherSchema = z.object({
  text: z.string(),
  images: z.array(z.string().url()),
})

export const calendarSchema = z.object({
  images: z.array(z.string().url()),
})

export const dailyDataSchema = z.object({
  tasks: z.array(taskSchema),
  taskDetails: z.array(taskDetailSchema),
  weather: weatherSchema.nullable(),
  calendar: calendarSchema.nullable(),
  candles: z.array(taskDetailSchema),
  seasonCandleMap: z.string().nullable(),
})

export const sectionErrorSchema = z.object({
  section: z.string(),
  code: z.string(),
})

export const metaSchema = z.object({
  fetched_at: z.string().datetime(),
  cached: z.boolean(),
  stale: z.boolean(),
})

export const envelopeSchema = z.object({
  schema_version: z.literal(1),
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).refine(value => {
    const time = Date.parse(value + 'T00:00:00Z')
    return Number.isFinite(time) && new Date(time).toISOString().slice(0, 10) === value
  }),
  timezone: z.literal('Asia/Shanghai'),
  data: dailyDataSchema,
  meta: metaSchema,
  errors: z.array(sectionErrorSchema),
})

export type Task = z.infer<typeof taskSchema>
export type DetailSection = z.infer<typeof detailSectionSchema>
export type TaskDetail = z.infer<typeof taskDetailSchema>
export type Weather = z.infer<typeof weatherSchema>
export type Calendar = z.infer<typeof calendarSchema>
export type DailyData = z.infer<typeof dailyDataSchema>
export type SectionError = z.infer<typeof sectionErrorSchema>
export type Envelope = z.infer<typeof envelopeSchema>
