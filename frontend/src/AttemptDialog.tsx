type Props = { confirm: 'exit' | 'submit' | null; sending: boolean; setConfirm: (value: 'exit' | 'submit' | null) => void; exit: (save: boolean) => Promise<void>; submit: () => Promise<void>; reading?: boolean }
export default function AttemptDialog({ confirm, sending, setConfirm, exit, submit, reading }: Props) {
  if (!confirm) return null
  return <div className="dialog-shade"><section className="exam-dialog" role="alertdialog" aria-modal="true" aria-labelledby="confirm-title" onKeyDown={e => {
    if (e.key === 'Escape' && !sending) setConfirm(null)
    if (e.key === 'Tab') {
      if (sending) { e.preventDefault(); return }
      const buttons = e.currentTarget.querySelectorAll<HTMLButtonElement>('button:not(:disabled)')
      const first = buttons[0], last = buttons[buttons.length - 1]
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus() }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus() }
    }
  }}><h2 id="confirm-title">{confirm === 'exit' ? 'Exit this attempt?' : reading ? 'Submit your answers?' : 'Submit your response?'}</h2>
    <p>{confirm === 'exit' ? 'Save draft and exit saves your latest response. Exit without saving skips the final save; earlier autosaved drafts remain stored. Either choice marks this attempt interrupted.' : reading ? 'Submission is final. Your selected answers will be saved.' : 'Submission is final. Your original response will be saved exactly as written.'}</p>
    <div className="exam-dialog-actions">
      <button autoFocus disabled={sending} onClick={() => setConfirm(null)}>{reading ? 'Cancel' : 'Keep writing'}</button>
      {confirm === 'exit' && <button disabled={sending} onClick={() => void exit(false)}>Exit without saving</button>}
      <button disabled={sending} className="navy-button" onClick={() => void (confirm === 'exit' ? exit(true) : submit())}>{confirm === 'exit' ? 'Save draft and exit' : reading ? 'Submit answers' : 'Submit response'}</button>
    </div>
  </section></div>
}
