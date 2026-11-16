import { useRef, useState } from 'react'
type Receipt = { module: string; taskNumbers: number[]; exerciseCount: number; status: string }
export default function ImportScreen({ module }: { module?: string }) {
  const [tab, setTab] = useState('upload')
  const [file, setFile] = useState<File>()
  const [json, setJson] = useState('')
  const [assets, setAssets] = useState<{ file: File; path: string }[]>([])
  const [busy, setBusy] = useState(false)
  const lock = useRef(false)
  const [error, setError] = useState('')
  const [ready, setReady] = useState<Receipt>()
  const [mode, setMode] = useState('Practice')
  const select = (value?: File) => { setError(''); if (value && !/\.(zip|json)$/i.test(value.name)) { setError('Choose a ZIP or JSON file.'); return } setFile(value) }
  async function submit() {
    if (lock.current) return
    lock.current = true; setBusy(true); setError('')
    try {
      const data = new FormData()
      if (tab === 'upload') { if (!file) throw new Error('Select a ZIP or JSON file.'); data.append('file', file) }
      else { if (!json.trim()) throw new Error('Paste the exercise JSON.'); data.append('json', json) }
      if (module) data.append('expectedModule', module)
      if (tab === 'paste' || file?.name.toLowerCase().endsWith('.json')) for (const asset of assets) { if (!asset.path.trim()) throw new Error('Enter the exact images/ path for each attachment.'); data.append('assets', asset.file); data.append('assetPaths', asset.path.trim()) }
      const response = await fetch('/api/imports', { method: 'POST', body: data })
      const result = await response.json()
      if (!response.ok) throw new Error(typeof result.message === 'string' ? result.message : 'Import failed. Check your package and retry.')
      if (!['READING', 'WRITING', 'SPEAKING'].includes(result.module) || !Array.isArray(result.taskNumbers) || (module && result.module !== module)) throw new Error('The imported module does not match your selection.')
      setReady({ module: result.module, taskNumbers: result.taskNumbers, exerciseCount: result.exerciseCount, status: result.status }); setJson(''); setFile(undefined); setAssets([]); setMode('Practice')
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not import. Check the local server and retry.') }
    finally { lock.current = false; setBusy(false) }
  }
  return <section className="import-screen"><a className="back-link" href="#/">← Back to home</a>
    {ready ? <div className="ready-card"><h1>Exercise ready</h1>{ready.status === 'EXISTING' && <p role="status">This exercise already exists. Your saved material was reused.</p>}<dl><dt>Module</dt><dd>{ready.module}</dd><dt>{ready.module === 'READING' ? 'Part' : 'Task'}</dt><dd>{ready.taskNumbers.join(' & ')}</dd><dt>Exercise count</dt><dd>{ready.exerciseCount}</dd></dl><div className="practice-modes" role="radiogroup" aria-labelledby="practice-mode-label"><h2 id="practice-mode-label">Practice mode</h2>{[['Practice','No time limit.'],['Timed','CELPIP time limits.'],['Exam','Strict examination conditions.']].map(([name, description]) => <label className="mode-option" key={name}><input type="radio" name="mode" checked={mode === name} onChange={() => setMode(name)} /><span><strong>{name}</strong><br />{description}</span></label>)}</div><button className="navy-button" disabled aria-describedby="pending">Start practice</button><p id="pending">The examination interface is pending.</p><button onClick={() => setReady(undefined)}>Load another exercise</button></div> : <><h1>Load exercise</h1>{module && <p>Selected module: {module}</p>}<div className="import-tabs" aria-label="Exercise loading method">{['upload','paste'].map(t => <button key={t} disabled={busy} aria-pressed={tab === t} onClick={() => { setTab(t); setError('') }}>{t === 'upload' ? 'Upload file' : 'Paste JSON'}</button>)}</div><fieldset disabled={busy} className="import-form">{tab === 'upload' ? <div className="drop-area" onDragOver={e => e.preventDefault()} onDrop={e => { e.preventDefault(); if (!busy) { if (e.dataTransfer.files.length !== 1) setError('Drop one ZIP or JSON file.'); else select(e.dataTransfer.files[0]) } }}><svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><path d="M12 16V3m-5 5 5-5 5 5M4 15v6h16v-6" /></svg><p>Drag and drop your exercise here</p><label className="file-label">Browse files<input type="file" accept=".zip,.json" onChange={e => { select(e.target.files?.[0]); e.target.value = '' }} /></label>{file && <p>{file.name} · {(file.size / 1024).toFixed(1)} KB <button onClick={() => setFile(undefined)}>Remove</button></p>}</div> : <label>Exercise JSON<textarea rows={12} spellCheck={false} value={json} onChange={e => setJson(e.target.value)} /></label>}
    {(tab === 'paste' || file?.name.toLowerCase().endsWith('.json')) && <div className="attachments"><label>Optional image attachments<input type="file" multiple accept=".png,.jpg,.jpeg" onChange={e => { setAssets([...assets, ...Array.from(e.target.files ?? []).map(f => ({ file: f, path: '' }))]); e.target.value = '' }} /></label><p>Enter each image’s exact path from the package, such as images/scene.png. Images are not previewed.</p>{assets.map((asset, i) => <div key={i}><span>{asset.file.name}</span><input aria-label={`Package path for ${asset.file.name}`} value={asset.path} placeholder="images/scene.png" onChange={e => setAssets(assets.map((a, index) => index === i ? { ...a, path: e.target.value } : a))} /><button onClick={() => setAssets(assets.filter((_, index) => index !== i))}>Remove</button></div>)}</div>}
    <button className="navy-button" onClick={() => void submit()}>{busy ? 'Loading…' : 'Load exercise'}</button></fieldset>{error && <p role="alert" className="import-error">{error}</p>}</>}
  </section>
}
