import { useEffect, useState } from 'react'
import { api, formatDuration } from './practice'
import type { Mode, Session, WritingResponse } from './practice'
import { partNames } from './reading'
import type { ReadingContent, ReadingResponse, ReadingReview } from './reading'
import { speakingNames } from './speaking'
import type { SpeakingSession } from './speaking'
import { ReadingResults } from './ReadingExam'
import { Passage, QuestionGroups } from './ReadingContent'
import SpeakingOptions from './SpeakingOptions'
import AttachmentControls from './AttachmentControls'
import AudioConversionStatus from './AudioConversionStatus'
import ImportScreen from './ImportScreen'
import type { Receipt } from './ImportScreen'
import { copied } from './notifications'

type Attempt={attemptId:string;module:string;taskNumber:number;attemptNumber:number;mode:Mode;status:string;createdAt:string;durationMs:number;preparationMs:number;selectionMs:number;choice:string|null;automaticChoice:boolean;correct?:number;total?:number}
type Entry={sessionId:string;packageId:string;mode:Mode;status:string;createdAt:string;attempts:Attempt[]}
type Stored=Attempt & {content:ReadingContent|Session['content']|SpeakingSession['content'];draft:ReadingResponse|WritingResponse|null;response?:ReadingResponse|WritingResponse|null;result?:ReadingReview['result']|{wordCount:number};audioUrl?:string}
const name=(attempt:Attempt)=>attempt.module==='READING'?`Reading Part ${attempt.taskNumber} — ${partNames[attempt.taskNumber-1]}`:attempt.module==='WRITING'?`Writing Task ${attempt.taskNumber} — ${attempt.taskNumber===1?'Writing an Email':'Responding to Survey Questions'}`:`Speaking Task ${attempt.taskNumber} — ${speakingNames[attempt.taskNumber]}`
const localDate=(date:string)=>new Date(date).toLocaleString(undefined,{dateStyle:'medium',timeStyle:'short'})
export default function History({onStart,onHome}:{onStart:(packageId:string,mode:Mode,module:string,tasks:number[])=>Promise<void>;onHome:()=>void}) {
 const calendarDay=(date:Date)=>`${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`
 const [day,setDay]=useState(()=>calendarDay(new Date()))
 const [timezone]=useState(()=>Intl.DateTimeFormat().resolvedOptions().timeZone)
 const [filter,setFilter]=useState('ALL')
 const [reload,setReload]=useState(0)
 const key=`${day}:${timezone}:${reload}`
 const [loaded,setLoaded]=useState<{key:string;entries?:Entry[];error?:string}>()
 const entries=loaded?.key===key?loaded.entries:undefined
 const [actionError,setActionError]=useState('')
 const error=actionError||(loaded?.key===key?loaded.error:'')
 const [selected,setSelected]=useState<Entry>()
 const [ready,setReady]=useState<Receipt>()
 const [busy,setBusy]=useState(false)
 function changeDate(value:string){if(!value)return;setActionError('');setDay(value)}
 function shift(offset:number){const value=new Date(`${day}T12:00:00`);value.setDate(value.getDate()+offset);changeDate(calendarDay(value))}
 useEffect(()=>{
  const controller=new AbortController()
  async function load(){try{
   const response=await fetch(`/api/history?date=${encodeURIComponent(day)}&timezone=${encodeURIComponent(timezone)}`,{cache:'no-store',signal:controller.signal})
   if(!response.ok)throw Error('History unavailable')
   const value:Entry[]=await response.json()
   if(!controller.signal.aborted)setLoaded({key,entries:value})
  }catch{if(!controller.signal.aborted)setLoaded({key,error:'Could not load history for this date. Check the server and retry.'})}}
  void load();return()=>controller.abort()
 },[day,timezone,key])
 async function retry(entry:Entry){setBusy(true);setActionError('');try{setReady(await api<Receipt>(`/packages/${encodeURIComponent(entry.packageId)}`))}catch{setActionError('Could not load the ready screen. Your saved attempt is unchanged.')}finally{setBusy(false)}}
 if(ready)return <ImportScreen initialReady={ready} onStart={onStart}/>
 if(selected)return <>{error&&<p role="alert">{error}</p>}<StoredResults entry={selected} onBack={()=>setSelected(undefined)} onHome={onHome} onRetry={()=>void retry(selected)}/></>
 const shown=entries?.filter(entry=>entry.attempts.some(attempt=>filter==='ALL'||attempt.module===filter))
 return <section className="history-screen"><a href="#/" className="back-link">← Back to home</a><h1>Practice history</h1><div className="history-dates"><button aria-label="Previous day" onClick={()=>shift(-1)}>← Previous day</button><label>Date <input type="date" value={day} onChange={e=>changeDate(e.target.value)}/></label><button aria-label="Next day" onClick={()=>shift(1)}>Next day →</button><button onClick={()=>changeDate(calendarDay(new Date()))}>Today</button></div><p className="history-timezone">Dates shown in {timezone}</p><div className="history-filters" aria-label="Filter by module">{['ALL','READING','WRITING','SPEAKING'].map(module=><button key={module} aria-pressed={filter===module} onClick={()=>setFilter(module)}>{module[0]+module.slice(1).toLowerCase()}</button>)}</div>{error&&<p role="alert">{error} <button onClick={()=>setReload(reload+1)}>Retry loading</button></p>}{!entries&&!error&&<p role="status">Loading history…</p>}{shown?.length===0&&<p>No {filter==='ALL'?'practice':filter.toLowerCase()} attempts on {day}. Completed and interrupted attempts will appear here.</p>}{shown?.map(entry=><article className="history-entry" key={entry.sessionId}><h2>{entry.attempts.some(attempt=>attempt.module==='SPEAKING'&&[3,4].includes(attempt.taskNumber))?'Speaking Tasks 3 and 4':entry.attempts[0]?name(entry.attempts[0]):'Practice session'}</h2><p>{localDate(entry.createdAt)} · {entry.mode} · <strong>{entry.status.replaceAll('_',' ').toLowerCase()}</strong></p>{entry.attempts.map(attempt=><div className="history-attempt" key={attempt.attemptId}><h3>{name(attempt)}</h3><p>Attempt {attempt.attemptNumber} · {localDate(attempt.createdAt)} · {attempt.status.replaceAll('_',' ').toLowerCase()} · {formatDuration(attempt.durationMs)} {attempt.module==='SPEAKING'?'speaking':'response'}{attempt.module==='SPEAKING'?` · ${formatDuration(attempt.preparationMs)} preparation`:''}{attempt.module==='READING'&&attempt.correct!==undefined?` · Raw score: ${attempt.correct}/${attempt.total}`:''}</p></div>)}<div className="writing-result-actions"><button disabled={!entry.attempts.some(a=>a.status!=='IN_PROGRESS')} onClick={()=>setSelected(entry)}>View results</button><button disabled={busy} onClick={()=>void retry(entry)}>Retry</button></div></article>)}</section>
}
function StoredResults({entry,onBack,onHome,onRetry}:{entry:Entry;onBack:()=>void;onHome:()=>void;onRetry:()=>void}) {
 const [records,setRecords]=useState<Stored[]>()
 const [error,setError]=useState('')
 const [reload,setReload]=useState(0)
 const [notice,setNotice]=useState('')
 const [fallback,setFallback]=useState('')
 useEffect(()=>{let cancelled=false;void api<Entry[]>(`/sessions/${entry.sessionId}/history`).then(groups=>Promise.all((groups[0]?.attempts??[]).filter(a=>a.status!=='IN_PROGRESS').map(a=>api<Stored>(`/attempts/${a.attemptId}/review`)))).then(value=>{if(!cancelled){setRecords(value);setError('')}}).catch(()=>{if(!cancelled)setError('Could not load stored results. Retry after checking the server.')});return()=>{cancelled=true}},[entry,reload])
 async function copy(record:Stored,paired=false){try{const value=await api<{text:string}>(`/attempts/${record.attemptId}/evaluation-context?paired=${paired}`);setFallback(value.text);await navigator.clipboard.writeText(value.text);setNotice('Copied');copied('Copied! Ready to paste.')}catch{setNotice('Copy failed. Retry or copy the details below.')}}
 async function exportZip(record:Stored,paired=false){try{const value=await api<{downloadUrl:string}>('/exports','POST',{attemptId:record.attemptId,paired});const anchor=document.createElement('a');anchor.href=value.downloadUrl;anchor.download='evaluation.zip';anchor.click();setNotice('Evaluation ZIP prepared.')}catch{setNotice('Could not prepare the evaluation ZIP. Retry.')}}
 const imageMap=(record:Stored)=>{
  const urls:Record<string,string>={}
  function walk(node:unknown){if(Array.isArray(node))node.forEach(walk);else if(node&&typeof node==='object')for(const [key,value] of Object.entries(node)){if(key==='image'&&typeof value==='string')urls[value]=`/api/assets/${value}?attemptId=${record.attemptId}`;else walk(value)}}
  walk(record.content);return urls
 }
 const completePair=records?.length===2&&records.every(record=>record.status==='COMPLETED')&&records[0].taskNumber===3&&records[1].taskNumber===4
 return <section className="history-review"><div className="writing-result-actions"><button onClick={onBack}>← Back to history</button><button onClick={onRetry}>Retry exercise</button></div><h1>Stored results</h1><p>{entry.status.toLowerCase()} · {localDate(entry.createdAt)}</p>{error&&<p role="alert">{error}<button onClick={()=>setReload(reload+1)}>Retry loading</button></p>}{!records&&!error&&<p role="status">Loading saved attempt…</p>}{entry.attempts.some(a=>a.status==='IN_PROGRESS')&&<p>The active attempt cannot be reviewed until it is completed or interrupted.</p>}{records?.map((record,index)=>{
  const completed=record.status==='COMPLETED'
  return <article className="stored-attempt" key={record.attemptId}><h2>{name(record)}</h2><p>Attempt {record.attemptNumber} · {record.mode} · {record.status.toLowerCase()}</p>
   {record.module==='READING'?<>{completed?<ReadingResults session={{taskNumber:record.taskNumber,content:record.content as ReadingContent}} review={record as unknown as ReadingReview} images={imageMap(record)} onHome={onHome} copy={()=>copy(record)} copyStatus={notice} fallback={fallback}/>:<><p>Interrupted — no score or completed response.</p><Passage blocks={(record.content as ReadingContent).passage} images={imageMap(record)}/><QuestionGroups groups={(record.content as ReadingContent).questionGroups} response={(record.draft as ReadingResponse)??{answers:{}}} disabled onChange={()=>{}}/></>}</>:record.module==='WRITING'?(()=>{const content=record.content as Session['content'];const response=(completed?record.response:record.draft) as WritingResponse|null;return <section className="writing-result"><h3>Original question</h3>{content.title&&<h4>{content.title}</h4>}<p className="preserved-text">{content.scenario}</p><p className="preserved-text">{content.instructions}</p>{content.requirements&&<ul>{content.requirements.map((text,i)=><li key={i}>{text}</li>)}</ul>}{content.options?.map(option=><p key={option.id}>Option {option.id}: {option.text}</p>)}{response?.choice&&<p>Selected option: {response.choice}</p>}<h3>{completed?'Original response':'Saved draft — interrupted'}</h3><pre className="original-response">{response?.text??'No saved text.'}</pre>{completed&&<p>{(record.result as {wordCount:number}).wordCount} words · {formatDuration(record.durationMs)} actual duration</p>}</section>})():<section className="speaking-result-card"><p className="preserved-text">{(record.content as SpeakingSession['content']).prompt}</p>{(record.content as SpeakingSession['content']).persuasionPrompt&&<p className="preserved-text">{(record.content as SpeakingSession['content']).persuasionPrompt}</p>}{(record.content as SpeakingSession['content']).alternatives?.map((text,i)=><p key={i}>{text}</p>)}{(record.content as SpeakingSession['content']).options&&<SpeakingOptions options={(record.content as SpeakingSession['content']).options!} images={imageMap(record)}/>}<p>Preparation: {formatDuration(record.preparationMs)} · Speaking: {formatDuration(record.durationMs)}{record.taskNumber===5?` · Selection: ${formatDuration(record.selectionMs)} · Choice: ${record.choice??'Not selected'} · ${record.automaticChoice?'Automatic fallback':'Manual selection'}`:''}</p>{(record.content as SpeakingSession['content']).image&&(index===0||record.taskNumber!==4)&&<a href={imageMap(record)[(record.content as SpeakingSession['content']).image!]} target="_blank" rel="noreferrer"><img className="stored-scene" src={imageMap(record)[(record.content as SpeakingSession['content']).image!]} alt="Original speaking scene"/></a>}{completed&&record.audioUrl?<><AudioConversionStatus attemptId={record.attemptId} originalUrl={record.audioUrl}/><AttachmentControls attemptId={record.attemptId}/>{Object.keys(imageMap(record)).length>0&&(index===0||record.taskNumber!==4)&&<>{record.taskNumber===5?(record.content as SpeakingSession['content']).options?.map(option=><div key={option.id}><h4>{option.title}</h4><AttachmentControls attemptId={record.attemptId} kind="IMAGE" assetId={option.image}/></div>):<AttachmentControls attemptId={record.attemptId} kind="IMAGE"/>}</>}<AttachmentControls attemptId={record.attemptId} kind="ALL"/></>:<p>Interrupted — no completed recording is available. Earlier completed recordings remain preserved.</p>}</section>}
   {completed&&<div className="writing-result-actions"><button className="navy-button" onClick={()=>void copy(record)}>Copy for ChatGPT</button><button onClick={()=>void exportZip(record)}>Download evaluation ZIP</button></div>}
  </article>
 })}{completePair&&records&&<section className="speaking-result-card"><h2>Both tasks</h2><AttachmentControls attemptId={records[1].attemptId} kind="ALL" paired/><div className="writing-result-actions"><button onClick={()=>void copy(records[1],true)}>Copy combined details</button><button onClick={()=>void exportZip(records[1],true)}>Download paired evaluation ZIP</button></div></section>}<p role="status">{notice}</p>{fallback&&notice!=='Copied'&&<textarea readOnly value={fallback} aria-label="Evaluation details"/>}<div className="writing-result-actions"><button onClick={onHome}>Return to homepage</button></div></section>
}
