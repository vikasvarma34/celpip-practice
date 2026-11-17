import { useEffect, useRef, useState } from 'react'
import type { Mode } from './practice'
type Receipt = { packageId: string; module: string; taskNumbers: number[]; exerciseCount: number; status: string }
export default function ImportScreen({ module, onStart }: { module?: string; onStart: (packageId: string, mode: Mode) => Promise<void> }) {
  const [tab, setTab] = useState('upload')
  const [file, setFile] = useState<File>()
  const [json, setJson] = useState('')
  const [assets, setAssets] = useState<{ file: File; path: string }[]>([])
  const [paths, setPaths] = useState<string[]>([])
  const [parsed, setParsed] = useState(false)
  const [mapping, setMapping] = useState<Record<string, string>>({})
  useEffect(() => {
    let cancelled = false
    async function detect() {
      try {
        const value = JSON.parse(tab === 'paste' ? json : file?.name.toLowerCase().endsWith('.json') ? await file.text() : '{}')
        const found = new Set<string>()
        function walk(node: unknown) { if (typeof node === 'string' && /^images\//.test(node)) found.add(node); else if (Array.isArray(node)) node.forEach(walk); else if (node && typeof node === 'object') Object.values(node).forEach(walk) }
        walk(value); if (!cancelled) { setPaths([...found]); setParsed(true); setMapping({}) }
      } catch { if (!cancelled) { setPaths([]); setParsed(false) } }
    }
    void detect(); return () => { cancelled = true }
  }, [tab, json, file])
  const chosen = (path: string) => {
    if (mapping[path] !== undefined) return mapping[path]
    const matches = assets.map((asset, index) => ({asset,index})).filter(({asset}) => asset.file.name === path.split('/').pop())
    return matches.length === 1 && paths.filter(required => required.split('/').pop() === path.split('/').pop()).length === 1 ? String(matches[0].index) : ''
  }
  const needsMapping = tab === 'paste' || file?.name.toLowerCase().endsWith('.json')
  const complete = !needsMapping || (parsed && paths.every(path => chosen(path) !== '' && !!assets[Number(chosen(path))]))
  const [busy, setBusy] = useState(false)
  const [confirmStart, setConfirmStart] = useState(false)
  const lock = useRef(false)
  const [error, setError] = useState('')
  const [ready, setReady] = useState<Receipt>()
  const [mode, setMode] = useState('Practice')
  const select = (value?: File) => { setError(''); if (value && !/\.(zip|json)$/i.test(value.name)) { setError('Choose a ZIP or JSON file.'); return } setParsed(false); setFile(value) }
  async function submit() {
    if (lock.current) return
    lock.current = true; setBusy(true); setError('')
    try {
      const data = new FormData()
      if (tab === 'upload') { if (!file) throw new Error('Select a ZIP or JSON file.'); data.append('file', file) }
      else { if (!json.trim()) throw new Error('Paste the exercise JSON.'); data.append('json', json) }
      if (module) data.append('expectedModule', module)
      if (!complete) throw new Error('Select a file for each required image.')
      if (needsMapping) for (const path of paths) { data.append('assets', assets[Number(chosen(path))].file); data.append('assetPaths', path) }
      const response = await fetch('/api/imports', { method: 'POST', body: data })
      const result = await response.json()
      if (!response.ok) throw new Error(typeof result.message === 'string' ? result.message : 'Import failed. Check your package and retry.')
      if (!['READING', 'WRITING', 'SPEAKING'].includes(result.module) || !Array.isArray(result.taskNumbers) || (module && result.module !== module)) throw new Error('The imported module does not match your selection.')
      setReady({ packageId: result.packageId, module: result.module, taskNumbers: result.taskNumbers, exerciseCount: result.exerciseCount, status: result.status }); setJson(''); setFile(undefined); setAssets([]); setMode('Practice')
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not import. Check the local server and retry.') }
    finally { lock.current = false; setBusy(false) }
  }
  async function start() {
    if (lock.current || !ready || !['WRITING', 'READING'].includes(ready.module)) return
    setConfirmStart(false)
    lock.current = true; setBusy(true); setError('')
    try { await onStart(ready.packageId, mode.toUpperCase() as Mode) }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not start. Check the local server. A lost connection may have created an unfinished attempt; starting again creates a new attempt.') }
    finally { lock.current = false; setBusy(false) }
  }
  return <section className="import-screen"><a className="back-link" href="#/">← Back to home</a>
    {ready ? <div className="ready-card"><h1>Exercise ready</h1>{ready.status === 'EXISTING' && <p role="status">This exercise already exists. Your saved material was reused.</p>}<dl><dt>Module</dt><dd>{ready.module}</dd><dt>{ready.module === 'READING' ? 'Part' : 'Task'}</dt><dd>{ready.taskNumbers.join(' & ')}</dd><dt>Exercise count</dt><dd>{ready.exerciseCount}</dd></dl><div className="practice-modes" role="radiogroup" aria-labelledby="practice-mode-label"><h2 id="practice-mode-label">Practice mode</h2>{[['Practice','No time limit.'],['Timed','CELPIP time limits.'],['Exam','Strict examination conditions.']].map(([name, description]) => <label className="mode-option" key={name}><input type="radio" name="mode" disabled={busy} checked={mode === name} onChange={() => setMode(name)} /><span><strong>{name}</strong><br />{description}</span></label>)}</div><div className="ready-actions"><button id="start-practice" className="navy-button" disabled={busy || !['WRITING', 'READING'].includes(ready.module)} onClick={() => setConfirmStart(true)} aria-describedby={!['WRITING', 'READING'].includes(ready.module) ? 'pending' : undefined}>{busy ? 'Starting…' : 'Start practice'}</button>{!['WRITING', 'READING'].includes(ready.module) && <p id="pending">The examination interface is pending for this module.</p>}{error && <p role="alert" className="import-error">{error}</p>}<button disabled={busy} onClick={() => setReady(undefined)}>Load another exercise</button></div></div> : <><h1>Load exercise</h1>{module && <p>Selected module: {module}</p>}<div className="import-tabs" aria-label="Exercise loading method">{['upload','paste'].map(t => <button key={t} disabled={busy} aria-pressed={tab === t} onClick={() => { setTab(t); setError('') }}>{t === 'upload' ? 'Upload file' : 'Paste JSON'}</button>)}</div><fieldset disabled={busy} className="import-form">{tab === 'upload' ? <div className="drop-area" onDragOver={e => e.preventDefault()} onDrop={e => { e.preventDefault(); if (!busy) { if (e.dataTransfer.files.length !== 1) setError('Drop one ZIP or JSON file.'); else select(e.dataTransfer.files[0]) } }}><svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><path d="M12 16V3m-5 5 5-5 5 5M4 15v6h16v-6" /></svg><p>Drag and drop your exercise here</p><label className="file-label">Browse files<input type="file" accept=".zip,.json" onChange={e => { select(e.target.files?.[0]); e.target.value = '' }} /></label>{file && <p>{file.name} · {(file.size / 1024).toFixed(1)} KB <button onClick={() => setFile(undefined)}>Remove</button></p>}</div> : <label>Exercise JSON<textarea rows={12} spellCheck={false} value={json} onChange={e => { setParsed(false); setJson(e.target.value) }} /></label>}
    {(tab === 'paste' || file?.name.toLowerCase().endsWith('.json')) && <div className="attachments"><label>Optional image attachments<input type="file" multiple accept=".png,.jpg,.jpeg" onChange={e => { setMapping({}); setAssets([...assets, ...Array.from(e.target.files ?? []).map(f => ({ file: f, path: '' }))]); e.target.value = '' }} /></label><p>Required images are matched by filename. Select a file below if needed. Images are not previewed.</p>{paths.map(path => <label key={path}>{path}<select value={chosen(path)} onChange={e => setMapping({...mapping, [path]: e.target.value})}><option value="">Select image file</option>{assets.map((asset, i) => <option key={i} value={i}>{asset.file.name} ({i + 1})</option>)}</select></label>)}</div>}
    <button className="navy-button" disabled={!complete} onClick={() => void submit()}>{busy ? 'Loading…' : 'Load exercise'}</button></fieldset>{error && <p role="alert" className="import-error">{error}</p>}</>}
    {confirmStart && <div className="dialog-shade"><section className="exam-dialog" role="alertdialog" aria-modal="true" aria-labelledby="start-confirm-title" aria-describedby="start-confirm-description" onKeyDown={e => {
      if (e.key === 'Escape') setConfirmStart(false)
      if (e.key === 'Tab') {
        const buttons = e.currentTarget.querySelectorAll('button')
        if (e.shiftKey && document.activeElement === buttons[0]) { e.preventDefault(); buttons[1].focus() }
        else if (!e.shiftKey && document.activeElement === buttons[1]) { e.preventDefault(); buttons[0].focus() }
      }
    }}><h2 id="start-confirm-title">Start the test?</h2><p id="start-confirm-description">Do you want to start the test? Your selected practice mode and timer will begin when you confirm.</p><div className="exam-dialog-actions"><button autoFocus onClick={() => { setConfirmStart(false); document.getElementById('start-practice')?.focus() }}>Cancel</button><button className="navy-button" onClick={() => void start()}>Start test</button></div></section></div>}
  </section>
}
