import { copied as showCopied } from './notifications'
import { useCallback, useEffect, useState } from 'react'
import { api, formatTime } from './practice'
import type { Review } from './practice'
import type { Recovery } from './practice'
/** Reloaded work is interrupted, never silently resumed as a fresh attempt. */
export default function WritingRecovery({ recovery, onHome }: { recovery: Recovery; onHome: () => void }) {
  const [saved, setSaved] = useState<Review>()
  const [status, setStatus] = useState('Recovering your previous attempt…')
  const [done, setDone] = useState(false)
  const [copied, setCopied] = useState('')
  const [context, setContext] = useState('')
  const recover = useCallback(async () => {
    try {
      const session = await api<{ status: string }>(`/sessions/${recovery.sessionId}`)
      if (session.status === 'IN_PROGRESS') await api(`/sessions/${recovery.sessionId}/interrupt`, 'POST', { reason: 'BROWSER_RELOAD' })
      const review = await api<Review>(`/attempts/${recovery.attemptId}/review`)
      if (review.status === 'COMPLETED') setSaved(review)
      setStatus(review.status === 'COMPLETED' ? 'Your response was submitted successfully.' : 'This attempt is interrupted. Your latest local text is preserved below; saved backend drafts also remain intact. It cannot resume as a fresh attempt.')
      setDone(true)
    } catch { setStatus('Recovery failed. Your local text is preserved below. Check the server and retry.') }
  }, [recovery.sessionId, recovery.attemptId])
  useEffect(() => {
    // Recovery synchronizes with persisted server state; schedule outside effect setup.
    let cancelled = false
    void Promise.resolve().then(() => { if (!cancelled) void recover() })
    return () => { cancelled = true }
  }, [recover])
  const response = saved?.response ?? recovery.response
  async function copy() {
    try {
      const text = saved ? (await api<{ text: string }>(`/attempts/${recovery.attemptId}/evaluation-context`)).text : response.text
      setContext(text)
      await navigator.clipboard.writeText(text)
      setCopied('Copied');showCopied('Copied! Ready to paste.')
    } catch { setCopied('Copy failed. Select the text below to copy, or retry.') }
  }
  return (
    <main className="writing-result">
      <h1>{saved ? 'Response saved' : 'Recovered Writing response'}</h1>
      <p role="status">{status}</p>
      {response.choice && <p>Selected option: {response.choice}</p>}
      {saved && <p>{saved.result.wordCount} words · {formatTime(saved.durationMs)} actual duration</p>}
      <pre className="original-response">{response.text || '(Empty response)'}</pre>
      <div className="writing-result-actions">
        <button disabled={!done} onClick={() => { sessionStorage.removeItem('celpip-writing-recovery'); onHome() }}>Return to homepage</button>
        <button className="navy-button" onClick={() => void copy()}>{saved ? 'Copy for ChatGPT' : 'Copy response'}</button>
        {!done && <button onClick={() => void recover()}>Retry recovery</button>}
      </div>
      <p role="status">{copied}</p>
      {context && copied !== 'Copied' && <textarea aria-label="Copy text" value={context} readOnly />}
    </main>
  )
}
