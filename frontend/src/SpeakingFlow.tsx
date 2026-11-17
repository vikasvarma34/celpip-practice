import { copied as showCopied } from './notifications'
import AttachmentControls from './AttachmentControls'
import { useEffect, useRef, useState } from 'react'
import SpeakingExam from './SpeakingExam'
import type { Saved } from './SpeakingExam'
import AudioConversionStatus from './AudioConversionStatus'
import { api, formatDuration } from './practice'
import { prepareMicrophone, speakingNames } from './speaking'
import type { SpeakingSession, Microphone } from './speaking'
type Completed={session:SpeakingSession;saved:Saved}
export default function SpeakingFlow({session,mic,onHome}:{session:SpeakingSession;mic:Microphone;onHome:()=>void}) {
  const [step,setStep]=useState({session,mic})
  const [images,setImages]=useState<Record<string,string>>({})
  const urls=useRef<string[]>([])
  const [ready,setReady]=useState(!session.content.image)
  const [error,setError]=useState('')
  const [retry,setRetry]=useState(0)
  const [completed,setCompleted]=useState<Completed[]>([])
  const [next,setNext]=useState<SpeakingSession>()
  const [finished,setFinished]=useState(false)
  const [message,setMessage]=useState('')
  const [context,setContext]=useState('')
  const transitioning=useRef(false)
  useEffect(()=>{
    let cancelled=false
    async function load(){
      const id=step.session.content.image
      try{
        if(id&&!images[id]){
          const response=await fetch(`/api/assets/${id}?sessionId=${step.session.sessionId}`,{cache:'no-store'})
          if(!response.ok)throw Error('Image unavailable')
          const url=URL.createObjectURL(await response.blob());urls.current.push(url)
          if(cancelled){URL.revokeObjectURL(url);return}
          const image=new Image();image.src=url;await image.decode()
          if(!cancelled)setImages(current=>({...current,[id]:url}))
        }
        if(!cancelled){setReady(true);setError('')}
      }catch{if(!cancelled)setError('The required exercise image could not be loaded. Timers have not started. Retry loading or exit.')}
    }
    void load();return()=>{cancelled=true}
  },[step,images,retry])
  useEffect(()=>{const owned=urls.current;return()=>{owned.forEach(url=>URL.revokeObjectURL(url))}},[])
  useEffect(()=>{
    if(ready&&!next)return
    const unload=(event:BeforeUnloadEvent)=>{event.preventDefault();event.returnValue=''}
    const hide=()=>{step.mic.release();void fetch(`/api/sessions/${session.sessionId}/interrupt`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({reason:'BROWSER_RELOAD'}),keepalive:true}).catch(()=>{})}
    window.addEventListener('beforeunload',unload);window.addEventListener('pagehide',hide)
    return()=>{window.removeEventListener('beforeunload',unload);window.removeEventListener('pagehide',hide)}
  },[ready,next,step.mic,session.sessionId])
  async function continuePair(active:SpeakingSession){
    if(transitioning.current)return
    transitioning.current=true;setError('')
    try{const microphone=await prepareMicrophone();setStep({session:active,mic:microphone});setNext(undefined);setReady(false)}
    catch{setError('Could not prepare the microphone for Task 4. Task 3 is saved. Allow microphone access and retry; Task 4 has not started timing.')}
    finally{transitioning.current=false}
  }
  function complete(saved:Saved,active:SpeakingSession){
    setCompleted(current=>[...current,{session:step.session,saved}])
    if(step.session.taskNumber===3){setNext(active);setReady(false);void continuePair(active)}else setFinished(true)
  }
  async function exit(){if(!window.confirm('Exit this attempt? Saved recordings remain preserved; unfinished work is interrupted.'))return;step.mic.release();try{await api(`/sessions/${session.sessionId}/interrupt`,'POST',{reason:'ABANDONED'});onHome()}catch{setError('Could not interrupt the session. Retry Exit.')}}
  async function copy(record:Completed,paired=false){try{const value=await api<{text:string}>(`/attempts/${record.session.attemptId}/evaluation-context?paired=${paired}`);setContext(value.text);await navigator.clipboard.writeText(value.text);setMessage('Copied');showCopied('Copied! Ready to paste.')}catch{setMessage('Copy failed. Retry or select the context below.')}}
  if(finished)return <main className="writing-result"><h1>{completed.length===2?'Both recordings successfully saved':'Recording successfully saved'}</h1>{completed.map(record=><section className="speaking-result-card" key={record.session.attemptId}><h2>Speaking Task {record.session.taskNumber} — {speakingNames[record.session.taskNumber]}</h2><p>Preparation: {formatDuration(record.saved.preparationMs)} · Speaking: {formatDuration(record.saved.durationMs)}</p><AudioConversionStatus attemptId={record.session.attemptId} originalUrl={record.saved.audioUrl}/><AttachmentControls attemptId={record.session.attemptId}/ ></section>)}<AttachmentControls attemptId={completed[0].session.attemptId} kind="IMAGE"/><AttachmentControls attemptId={completed[completed.length-1].session.attemptId} kind="ALL" paired={completed.length===2}/><div className="writing-result-actions"><button onClick={onHome}>Return to homepage</button><button className="navy-button" onClick={()=>void copy(completed[completed.length-1],completed.length===2)}>Copy for ChatGPT</button></div><p role="status">{message}</p>{context&&message!=='Copied'&&<textarea readOnly value={context} aria-label="Evaluation context"/>}</main>
  if(next||!ready)return <main className="writing-result"><h1>{next?'Preparing Task 4':'Loading exercise image'}</h1><p role="status">{error||'Preparing the next stage. The preparation timer has not started.'}</p>{error&&<button onClick={()=>next?void continuePair(next):setRetry(retry+1)}>Retry preparation</button>}<button onClick={()=>void exit()}>Exit practice</button></main>
  return <SpeakingExam key={step.session.attemptId} session={step.session} mic={step.mic} imageUrl={step.session.content.image?images[step.session.content.image]:undefined} onHome={onHome} onComplete={step.session.content.image?complete:undefined}/>
}
