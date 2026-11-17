export type Mode = 'PRACTICE' | 'TIMED' | 'EXAM'
export type WritingResponse = { text: string; choice?: string | null }
export type Session = { sessionId: string; attemptId: string; taskNumber: number; mode: Mode; timing: { answerSeconds: number }; content: { title?: string; scenario: string; instructions: string; requirements?: string[]; options?: { id: string; text: string }[] } }
export type Review = { response: WritingResponse; result: { wordCount: number }; durationMs: number; status: string }
export function wordCount(text: string) { return text.match(/[^\p{White_Space}]+/gu)?.length ?? 0 }
export function clock(start: number, now: number, limit: number | null) {
  const elapsed = Math.max(0, Math.floor(now - start))
  return { elapsed, display: limit === null ? elapsed : Math.max(0, limit - elapsed), expired: limit !== null && elapsed >= limit }
}
export function formatTime(ms: number) { const seconds = Math.ceil(ms / 1000); return `${Math.floor(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}` }
export async function api<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const response = await fetch(`/api${path}`, { method, cache: 'no-store', headers: body === undefined ? undefined : { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) })
  const data = await response.json()
  if (!response.ok) throw new Error(data.message ?? 'Request failed. Keep this page open and retry.')
  return data as T
}
/** One ordered draft queue; submission freezes one payload and drains writes before finalizing. */
export class ResponseWriter<R = WritingResponse, V = Review> {
  private queue: Promise<void> = Promise.resolve()
  private frozen?: { response: R; durationMs: number }
  private pending?: Promise<V>
  private saved?: V
  closed = false
  private send: (method: string, action: string, body: unknown) => Promise<unknown>
  constructor(send: (method: string, action: string, body: unknown) => Promise<unknown>) { this.send = send }
  draft(response: R): Promise<void> {
    if (this.closed || this.frozen) return Promise.resolve()
    const snapshot = structuredClone(response)
    const task = this.queue.then(async () => { if (!this.closed) await this.send('PATCH', 'draft', snapshot) })
    this.queue = task.catch(() => {})
    return task
  }
  submit(response: R, durationMs: number): Promise<V> {
    if (this.saved) return Promise.resolve(this.saved)
    if (this.pending) return this.pending
    if (this.closed) return Promise.reject(new Error('This attempt was interrupted.'))
    this.frozen ??= { response: structuredClone(response), durationMs }
    this.pending = this.queue.then(async () => {
      const result = await this.send('POST', 'submit', this.frozen) as V
      this.saved = result; this.closed = true; return result
    }).finally(() => { this.pending = undefined })
    return this.pending
  }
  async stop() { this.closed = true; await this.queue }
}

export type Recovery = { sessionId: string; attemptId: string; response: WritingResponse }
export function readRecovery(): Recovery | undefined {
  try {
    const value = JSON.parse(sessionStorage.getItem('celpip-writing-recovery') ?? 'null')
    if (value && typeof value.sessionId === 'string' && typeof value.attemptId === 'string' && typeof value.response?.text === 'string') return value
  } catch { /* No recoverable local draft. */ }
}

export function formatDuration(ms:number) { const seconds=Math.round(Math.max(0,ms)/1000);return `${String(Math.floor(seconds/60)).padStart(2,'0')}:${String(seconds%60).padStart(2,'0')}` }
