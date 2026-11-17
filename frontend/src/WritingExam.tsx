import AttemptDialog from './AttemptDialog'
import { formatTime, wordCount } from './practice'
import { useAttempt } from './useAttempt'
import type { Session, Review, WritingResponse } from './practice'

export default function WritingExam({ session, onHome }: { session: Session; onHome: () => void }) {
  const { response, latest, draftState, error, locked, sending, review, confirm, setConfirm, copyStatus, fallback, interrupted, limit, time, change, exit, submit, copy } = useAttempt<WritingResponse, Review>(session, session.taskNumber === 2 ? { text: '', choice: null } : { text: '' }, 'celpip-writing-recovery', onHome)
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
      <AttemptDialog confirm={confirm} sending={sending} setConfirm={setConfirm} exit={exit} submit={submit} />
    </div>
  )
}
