import { useEffect, useRef, useState } from 'react'
import { api, clock, formatTime } from './practice'
import { usePracticeTimer } from './usePracticeTimer'
import { prepareMicrophone } from './speaking'
import type { SpeakingSession, Microphone } from './speaking'
import SpeakingOptions from './SpeakingOptions'
import SpeakingFlow from './SpeakingFlow'
import AttemptDialog from './AttemptDialog'

export default function TaskFive({session,onHome}:{session:SpeakingSession;onHome:()=>void}) {
  const [images,setImages]=useState<Record<string,string>>()
  const [error,setError]=useState('')
  const [retry,setRetry]=useState(0)
  const [locked,setLocked]=useState<SpeakingSession>()
  const [microphone,setMicrophone]=useState<Microphone>()
  const [busy,setBusy]=useState(false)
  const microphoneBusy=useRef(false)
  const [exitConfirm,setExitConfirm]=useState(false)
  useEffect(()=>{
    let cancelled=false
    const urls:string[]=[]
    async function load(){
      try{
        const result:Record<string,string>={}
        for(const option of session.content.options??[]){
          const response=await fetch(`/api/assets/${option.image}?sessionId=${session.sessionId}`,{cache:'no-store'})
          if(!response.ok)throw Error('Image unavailable')
          const url=URL.createObjectURL(await response.blob());urls.push(url)
          if(cancelled){URL.revokeObjectURL(url);return}
          const image=new Image();image.src=url;await image.decode();result[option.image]=url
        }
        if(!cancelled){setImages(result);setError('')}
      }catch{if(!cancelled)setError('The selection images could not be loaded. Selection timing has not started. Retry or exit.')}
    }
    void load();return()=>{cancelled=true;urls.forEach(url=>URL.revokeObjectURL(url))}
  },[session,retry])
  useEffect(()=>{
    const unload=(e:BeforeUnloadEvent)=>{if(!microphone){e.preventDefault();e.returnValue=''}}
    const hide=()=>{if(!microphone)void fetch(`/api/sessions/${session.sessionId}/interrupt`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({reason:'BROWSER_RELOAD'}),keepalive:true}).catch(()=>{})}
    window.addEventListener('beforeunload',unload);window.addEventListener('pagehide',hide)
    return()=>{window.removeEventListener('beforeunload',unload);window.removeEventListener('pagehide',hide)}
  },[microphone,session.sessionId])
  async function prepare(active:SpeakingSession){
    if(microphoneBusy.current)return
    microphoneBusy.current=true;setLocked(active);setBusy(true);setError('')
    try{setMicrophone(await prepareMicrophone())}catch{setError('Selection is locked. Allow microphone access and retry preparation. Preparation timing has not started.')}
    finally{microphoneBusy.current=false;setBusy(false)}
  }
  async function exit(){setBusy(true);try{microphone?.release();await api(`/sessions/${session.sessionId}/interrupt`,'POST',{reason:'ABANDONED'});onHome()}catch{setError('Could not interrupt the attempt. Retry Exit.')}finally{setBusy(false)}}
  if(locked&&microphone)return <SpeakingFlow session={locked} mic={microphone} onHome={onHome}/>
  if(locked||!images)return <main className="writing-result"><h1>{locked?'Preparing your comparison':'Loading selection images'}</h1><p role="status">{error||'Please wait. Timers have not started.'}</p>{error&&<button disabled={busy} onClick={()=>locked?void prepare(locked):setRetry(retry+1)}>Retry</button>}<button disabled={busy} onClick={()=>setExitConfirm(true)}>Exit practice</button><AttemptDialog speaking confirm={exitConfirm?'exit':null} sending={busy} setConfirm={()=>setExitConfirm(false)} exit={exit} submit={async()=>{}}/></main>
  return <Selection session={session} images={images} onLocked={active=>void prepare(active)} onHome={onHome}/>
}
function Selection({session,images,onLocked,onHome}:{session:SpeakingSession;images:Record<string,string>;onLocked:(active:SpeakingSession)=>void;onHome:()=>void}) {
  const [start]=useState(()=>performance.now())
  const [choice,setChoice]=useState<string|null>(null)
  const [busy,setBusy]=useState(false)
  const pending=useRef(false)
  const frozen=useRef<{attemptId:string;action:string;elapsedMs:number;choice:string|null;timeout:boolean}|undefined>(undefined)
  const [error,setError]=useState('')
  const [exitConfirm,setExitConfirm]=useState(false)
  const [interrupted,setInterrupted]=useState(false)
  const [expired,setExpired]=useState(false)
  const practice=session.mode==='PRACTICE'
  async function lock(timeout=false){
    if(pending.current||interrupted)return
    if(!timeout&&!choice&&!frozen.current)return
    pending.current=true;setBusy(true);setError('')
    frozen.current??={attemptId:session.attemptId,action:'LOCK_SELECTION',elapsedMs:clock(start,performance.now(),null).elapsed,choice,timeout}
    try{
      let active:SpeakingSession
      try{active=await api<SpeakingSession>(`/sessions/${session.sessionId}/advance`,'POST',frozen.current)}
      catch{active=await api<SpeakingSession>(`/sessions/${session.sessionId}`);if(active.stage!=='PREPARATION'||active.status!=='IN_PROGRESS')throw Error('Selection was not confirmed. Retry without changing your selection.')}
      const details={...active,selectionMs:frozen.current.elapsedMs,automaticChoice:frozen.current.choice===null}
      onLocked(details)
    }catch(e){setError(e instanceof Error?e.message:'Could not lock selection. Retry.')}
    finally{pending.current=false;setBusy(false)}
  }
  async function interrupt(reason='ABANDONED'){pending.current=true;setInterrupted(true);setExitConfirm(false);try{await api(`/sessions/${session.sessionId}/interrupt`,'POST',{reason});if(reason==='ABANDONED')onHome()}catch{setError('Could not mark the attempt interrupted. Retry Exit.')}finally{pending.current=false}}
  const timer=usePracticeTimer(start,practice?null:session.timing.selectionSeconds*1000,!busy&&!interrupted&&!expired,()=>{setExpired(true);void lock(true)},()=>{void interrupt('SUSPENDED')})
  return <div className="writing-exam"><header className="exam-header"><h1>Speaking Task 5 — Comparing and Persuading</h1><div role="timer" aria-label={practice?'Selection elapsed':'Selection remaining'}><span>Selection</span><strong>{formatTime(timer.display)}</strong></div></header><main className="task-five-selection"><p>{session.content.prompt}</p><SpeakingOptions options={session.content.options??[]} images={images} selected={choice} disabled={busy||expired||interrupted||!!error} onSelect={id=>{if(!busy&&!expired&&!frozen.current&&!interrupted)setChoice(id)}}/>{error&&<p role="alert">{error}</p>}</main><footer className="exam-actions"><button disabled={busy} onClick={()=>setExitConfirm(true)}>Exit practice</button><button className="navy-button" disabled={busy||interrupted||(!choice&&!expired)} onClick={()=>void lock(expired)}>{busy?'Locking selection…':error?'Retry selection':'Confirm selection'}</button></footer><AttemptDialog speaking confirm={exitConfirm?'exit':null} sending={busy} setConfirm={()=>setExitConfirm(false)} exit={()=>interrupt()} submit={async()=>{}}/></div>
}
