import { useEffect, useRef, useState } from 'react'
import { api, clock, formatTime, ResponseWriter, wordCount } from './practice'
import { usePracticeTimer } from './usePracticeTimer'
import type { Session, Review, WritingResponse } from './practice'

export default function WritingExam({ session, onHome }: { session: Session; onHome: () => void }) {
  const [response, setResponse] = useState<WritingResponse>(() => session.taskNumber === 2 ? { text: '', choice: null } : { text: '' })
  const latest = useRef(response)
  const [start] = useState(() => performance.now())
  const [draftState, setDraftState] = useState('Not saved yet')
  const [error, setError] = useState('')
  const [locked, setLocked] = useState(false)
  const [sending, setSending] = useState(false)
  const [review, setReview] = useState<Review>()
  const [confirm, setConfirm] = useState<'exit' | 'submit' | null>(null)
  const [copyStatus, setCopyStatus] = useState('')
  const [fallback, setFallback] = useState('')
  const [writer] = useState(() => new ResponseWriter((method, action, body) => api(`/attempts/${session.attemptId}/${action}`, method, body)))
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
      finalized.current = true; setReview(result); sessionStorage.removeItem('celpip-writing-recovery')
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
  function change(value: WritingResponse) {
    latest.current = value; setResponse(value); setDraftState('Saving…'); const revision = ++version.current
    try { sessionStorage.setItem('celpip-writing-recovery', JSON.stringify({ sessionId: session.sessionId, attemptId: session.attemptId, response: value })) } catch { /* Server drafts remain available if browser storage is full. */ }
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
      finalized.current = true; sessionStorage.removeItem('celpip-writing-recovery'); onHome()
    } catch { operation.current = 'interrupted'; setInterrupted(true); setError('Could not exit the attempt. Your response is preserved; keep this page open and retry Exit.') }
    finally { setSending(false); setConfirm(null) }
  }
  async function copy() {
    try { const context = await api<{ text: string }>(`/attempts/${session.attemptId}/evaluation-context`); setFallback(context.text); await navigator.clipboard.writeText(context.text); setCopyStatus('Copied') }
    catch { setCopyStatus('Copy failed. You can select the context below if it loaded, or retry.') }
  }
  const title = session.taskNumber === 1 ? 'Writing an Email' : 'Responding to Survey Questions'
  if (review) return (
    <main className="writing-result">
      <h1>Response saved</h1>
      <p>Writing Task {session.taskNumber} · {title}</p>
      {session.taskNumber === 2 && <p>Selected option: {review.response.choice
        ? `${review.response.choice} — ${session.content.options?.find(option => option.id === review.response.choice)?.text ?? ''}`
        : 'Not selected'}</p>}
      <p>{review.result.wordCount} words · {formatTime(review.durationMs)} actual duration</p>
      <pre className="original-response">{review.response.text || '(Empty response)'}</pre>
      <div className="writing-result-actions">
        <button onClick={onHome}>Return to homepage</button>
        <button className="navy-button" onClick={() => void copy()}>Copy for ChatGPT</button>
      </div>
      <p role="status">{copyStatus}</p>
      {fallback && copyStatus !== 'Copied' && <textarea aria-label="Evaluation context" readOnly value={fallback} />}
    </main>
  )
  return (
    <div className="writing-exam">
      <header className="exam-header">
        <h1>Writing Task {session.taskNumber} — {title}</h1>
        <div role="timer" aria-label={limit === null ? 'Elapsed time' : 'Time remaining'}>
          <span>{limit === null ? 'Elapsed' : 'Time remaining'}</span>
          <strong>{formatTime(time.display)}</strong>
        </div>
      </header>
      <main className="exam-columns">
        <section className="question-panel" aria-label="Writing question">
          {session.content.title && <h2>{session.content.title}</h2>}
          <p>{session.content.scenario}</p>
          <p>{session.content.instructions}</p>
          {session.content.requirements && <ul>{session.content.requirements.map((text, i) => <li key={i}>{text}</li>)}</ul>}
          {session.content.options && (
            <fieldset disabled={locked}>
              <legend>Choose an option</legend>
              {session.content.options.map(option => (
                <label className="survey-option" key={option.id}>
                  <input type="radio" name="survey" checked={response.choice === option.id}
                    onChange={() => change({ ...latest.current, choice: option.id })} />
                  <span><strong>Option {option.id}</strong><br />{option.text}</span>
                </label>
              ))}
            </fieldset>
          )}
        </section>
        <section className="editor-panel">
          <label htmlFor="writing-response">Your response</label>
          <textarea id="writing-response" value={response.text}
            onChange={e => change({ ...latest.current, text: e.target.value })}
            readOnly={locked} spellCheck autoCorrect="off" autoCapitalize="off" autoComplete="off"
            data-gramm="false" data-gramm_editor="false" data-enable-grammarly="false" />
          <div className="editor-status">
            <span>{wordCount(response.text)} words · Recommended: 150–200</span>
            <span role="status">{draftState}</span>
          </div>
          {draftState === 'Save failed' && !locked && <button onClick={() => change({ ...latest.current })}>Retry draft save</button>}
          <small>Basic spell-check is enabled. Browser or OS extensions may still offer writing assistance; disable those for exam practice.</small>
        </section>
      </main>
      <footer className="exam-actions">
        <button disabled={sending || (locked && !interrupted)} onClick={() => setConfirm('exit')}>Exit practice</button>
        <button className="navy-button" disabled={sending || interrupted}
          onClick={() => locked ? void submit() : setConfirm('submit')}>
          {sending ? 'Saving response…' : interrupted ? 'Attempt interrupted' : locked ? 'Retry submission' : 'Submit response'}
        </button>
      </footer>
      {error && <p className="exam-error" role="alert">{error} Your response is preserved on this page.</p>}
      {confirm && (
        <div className="dialog-shade">
          <section className="exam-dialog" role="alertdialog" aria-modal="true" aria-labelledby="confirm-title"
            onKeyDown={e => {
              if (e.key === 'Escape' && !sending) setConfirm(null)
              if (e.key === 'Tab') {
                if (sending) { e.preventDefault(); return }
                const buttons = e.currentTarget.querySelectorAll('button:not(:disabled)')
                const first = buttons[0] as HTMLButtonElement, last = buttons[buttons.length - 1] as HTMLButtonElement
                if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus() }
                else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus() }
              }
            }}>
            <h2 id="confirm-title">{confirm === 'exit' ? 'Exit this attempt?' : 'Submit your response?'}</h2>
            <p>{confirm === 'exit' ? 'Save draft and exit saves your latest response. Exit without saving skips the final save; earlier autosaved drafts remain stored. Either choice marks this attempt interrupted.'
              : 'Submission is final. Your original response will be saved exactly as written.'}</p>
            <div className="exam-dialog-actions">
              <button autoFocus disabled={sending} onClick={() => setConfirm(null)}>Keep writing</button>
              <button disabled={sending} className="navy-button" onClick={() => void (confirm === 'exit' ? exit(true) : submit())}>
                {confirm === 'exit' ? 'Save draft and exit' : 'Submit response'}
              </button>
              {confirm === 'exit' && <button disabled={sending} onClick={() => void exit(false)}>Exit without saving</button>}
            </div>
          </section>
        </div>
      )}
    </div>
  )
}
