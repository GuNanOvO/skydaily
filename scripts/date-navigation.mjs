export function dateNavigation(dates, current) {
  const valid = value => /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(value + 'T00:00:00Z')) && new Date(value + 'T00:00:00Z').toISOString().slice(0, 10) === value
  const available = [...new Set([...dates, current].filter(valid))].sort()
  const selected = available.indexOf(current)
  const months = [...new Set(available.map(value => value.slice(0, 7)))].map(month => {
    const [year, number] = month.split('-').map(Number)
    const first = new Date(Date.UTC(year, number - 1, 1)).getUTCDay()
    const count = new Date(Date.UTC(year, number, 0)).getUTCDate()
    return { month, year, number, offset: (first + 6) % 7, days: Array.from({ length: count }, (_, i) => {
      const date = month + '-' + String(i + 1).padStart(2, '0')
      return { date, day: i + 1, available: available.includes(date), selected: date === current }
    }) }
  })
  return { dates: available, months, monthIndex: Math.max(0, months.findIndex(m => m.month === current.slice(0, 7))), previous: available[selected - 1] ?? null, next: available[selected + 1] ?? null }
}
