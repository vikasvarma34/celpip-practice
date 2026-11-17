import { useEffect, useRef, useState } from 'react'
import { api, clock, ResponseWriter } from './practice'
import { usePracticeTimer } from './usePracticeTimer'
import type { Mode } from './practice'

export function useAttempt<R, V>(session: {sessionId: string; attemptId: string; mode: Mode; timing: {answerSeconds: number}}, initial: R, storageKey: string, onHome: () => void) {
  const [response, setResponse] = useState<R>(initial)
  const latest = useRef(response)
  const [start] = useState(() => performance.now())
  const [draftState, setDraftState] = useState('Not saved yet')
  const [error, setError] = useState('')
  const [locked, setLocked] = useState(false)
  const [sending, setSending] = useState(false)
  const [review, setReview] = useState<V>()
  const [confirm, setConfirm] = useState<'exit' | 'submit' | null>(null)
  const [copyStatus, setCopyStatus] = useState('')
  const [fallback, setFallback] = useState('')
  const [writer] = useState(() => new ResponseWriter<R, V>((method, action, body) => api(`/attempts/${session.attemptId}/${action}`, method, body)))
  const finalized = useRef(false)
  const operation = useRef<'idle' | 'submitting' | 'exiting' | 'interrupted'>('idle')
  const [interrupted, setInterrupted] = useState(false)
  const debounce = useRef<number | undefined>(undefined)
  const version = useRef(0)
  const limit = session.mode === 'PRACTICE' ? null : session.timing.answerSeconds * 1000
  function suspend() {
    if (operation.current !== 'idle') return
    operation.current = 'interrupted'; setInterrupted(true); clearTimeout(debounce.current); setLocked(true)
    setError('Device suspension made timing unreliable. Exit to preserve this as an interrupted attempt.')
    void writer.draft(latest.current).then(() => writer.stop())
      .then(() => api(`/sessions/${session.sessionId}/interrupt`, 'POST', { reason: 'SUSPENDED' }))
      .catch(() => setError('Could not mark the suspended attempt interrupted. Use Exit practice to retry.'))
  }

  async function submit() {
    if (operation.current === 'exiting' || operation.current === 'interrupted' || finalized.current) return
    operation.current = 'submitting'
    window.clearTimeout(debounce.current); setConfirm(null); setLocked(true); setSending(true); setError('')
    try {
      const result = await writer.submit(latest.current, clock(start, performance.now(), null).elapsed)
      finalized.current = true; setReview(result); sessionStorage.removeItem(storageKey)
    } catch (e) { setError(e instanceof Error ? e.message : 'Submission failed. Retry with your preserved response.') }
    finally { setSending(false) }
  }
  const time = usePracticeTimer(start, limit, !locked && !review, () => { void submit() }, suspend)
  useEffect(() => {
    const unload = (e: BeforeUnloadEvent) => { if (!finalized.current) { e.preventDefault(); e.returnValue = '' } }
    const pagehide = () => { if (!finalized.current) void fetch(`/api/sessions/${session.sessionId}/interrupt`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ reason: 'BROWSER_RELOAD' }), keepalive: true }).catch(() => {}) }
    window.addEventListener('beforeunload', unload); window.addEventListener('pagehide', pagehide)
    return () => { window.removeEventListener('beforeunload', unload); window.removeEventListener('pagehide', pagehide); window.clearTimeout(debounce.current) }
  }, [session.sessionId])
  function change(value: R) {
    latest.current = value; setResponse(value); setDraftState('Saving…'); const revision = ++version.current
    try { sessionStorage.setItem(storageKey, JSON.stringify({ sessionId: session.sessionId, attemptId: session.attemptId, response: value })) } catch { /* Server drafts remain available if browser storage is full. */ }
    clearTimeout(debounce.current)
    debounce.current = window.setTimeout(() => {
      void writer.draft(value).then(() => { if (revision === version.current) setDraftState('Saved') }).catch(() => { if (revision === version.current) setDraftState('Save failed') })
    }, 600)
  }
  async function exit(saveDraft = true) {
    if (operation.current === 'submitting' || operation.current === 'exiting') return
    operation.current = 'exiting'; setLocked(true); setSending(true); setError(''); clearTimeout(debounce.current)
    try {
      if (saveDraft && !writer.closed) await writer.draft(latest.current)
      await writer.stop()
      await api(`/sessions/${session.sessionId}/interrupt`, 'POST', { reason: 'ABANDONED' })
      finalized.current = true; sessionStorage.removeItem(storageKey); onHome()
    } catch { operation.current = 'interrupted'; setInterrupted(true); setError('Could not exit the attempt. Your response is preserved; keep this page open and retry Exit.') }
    finally { setSending(false); setConfirm(null) }
  }
  async function copy() {
    try { const context = await api<{ text: string }>(`/attempts/${session.attemptId}/evaluation-context`); setFallback(context.text); await navigator.clipboard.writeText(context.text); setCopyStatus('Copied') }
    catch { setCopyStatus('Copy failed. You can select the context below if it loaded, or retry.') }
  }
  return { response, latest, draftState, error, locked, sending, review, confirm, setConfirm, copyStatus, fallback, interrupted, limit, time, change, exit, submit, copy }
}
