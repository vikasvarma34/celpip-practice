import SpeakingOptions from './SpeakingOptions'
import { copied as showCopied } from './notifications'
import AttachmentControls from './AttachmentControls'
import AudioConversionStatus from './AudioConversionStatus'
import AttemptDialog from './AttemptDialog'
import { useEffect, useEffectEvent, useRef, useState } from 'react'
import { api, clock, formatTime, formatDuration } from './practice'
import { usePracticeTimer } from './usePracticeTimer'
import { speakingNames } from './speaking'
import type { Microphone, SpeakingSession } from './speaking'

type Phase='PREPARATION'|'STARTING'|'RECORDING'|'STOPPING'|'UPLOADING'|'RETRY'|'COMPLETED'|'INTERRUPTED'
export type Saved={preparationMs:number;durationMs:number;audioUrl:string}
export default function SpeakingExam({session,mic,onHome,imageUrl,optionsImages,onComplete}:{session:SpeakingSession;mic:Microphone;onHome:()=>void;imageUrl?:string;optionsImages?:Record<string,string>;onComplete?:(saved:Saved,next:SpeakingSession)=>void}) {
  const [phase,setPhase]=useState<Phase>('PREPARATION')
  const current=useRef<Phase>('PREPARATION')
  const [prepStart]=useState(()=>performance.now())
  const [recordStart,setRecordStart]=useState(prepStart)
  const recordAt=useRef(prepStart)
  const prepMs=useRef(0)
  const [recordedElapsed,setRecordedElapsed]=useState<number>()
  const audio=useRef<{blob:Blob;durationMs:number}|undefined>(undefined)
  const [saved,setSaved]=useState<Saved>()
  const [error,setError]=useState('')
  const [notice,setNotice]=useState('')
  const [level,setLevel]=useState(0)
  const [exitConfirm,setExitConfirm]=useState(false)
  const [copyStatus,setCopyStatus]=useState('')
  const [context,setContext]=useState('')
  const busy=useRef(false)
  const stopDuration=useRef(0)
  const practice=session.mode==='PRACTICE'
  function move(value:Phase) {current.current=value;setPhase(value)}
  async function interrupt(reason='ABANDONED') {
    await api(`/sessions/${session.sessionId}/interrupt`,'POST',{reason})
  }
  async function fail(message:string,reason='DEVICE_ERROR') {
    if(['COMPLETED','INTERRUPTED'].includes(current.current))return
    move('INTERRUPTED');setError(message);setExitConfirm(false)
    if(mic.recorder.state!=='inactive')mic.recorder.stop()
    mic.release()
    try{await interrupt(reason)}catch{setError(`${message} Could not mark the attempt interrupted. Use Exit to retry.`)}
  }
  async function cue() {if(!await mic.cue())setNotice('Audible cue unavailable. Follow the highlighted stage and recording status.')}
  async function upload() {
    if(busy.current||!audio.current||current.current==='COMPLETED')return
    busy.current=true;move('UPLOADING');setError('');setExitConfirm(false)
    try {
      const body=new FormData();body.append('audio',audio.current.blob,'response.webm');body.append('metadata',JSON.stringify({durationMs:audio.current.durationMs}))
      const response=await fetch(`/api/attempts/${session.attemptId}/submit`,{method:'POST',body})
      const result=await response.json()
      if(!response.ok||result.saved!==true)throw Error(result.message??'Recording was not confirmed saved. Keep this page open and retry.')
      move('COMPLETED');mic.release()
      const savedAudio={preparationMs:prepMs.current,durationMs:audio.current.durationMs,audioUrl:`/api/attempts/${session.attemptId}/audio`}
      setSaved(savedAudio)
      if(onComplete){onComplete(savedAudio,result.session);return}
      // Backend acknowledgement is sufficient; review enriches durations without controlling success.
      void api<Saved>(`/attempts/${session.attemptId}/review`).then(setSaved).catch(()=>{})
    }catch(e){move('RETRY');setError(e instanceof Error?e.message:'Could not save. Your audio remains in memory; retry without leaving this page.')}
    finally{busy.current=false}
  }
  async function startRecording() {
    if(current.current!=='PREPARATION')return
    move('STARTING');setExitConfirm(false);setError('')
    prepMs.current=clock(prepStart,performance.now(),null).elapsed
    try {
      try {await api(`/sessions/${session.sessionId}/advance`,'POST',{attemptId:session.attemptId,action:'START_RECORDING',elapsedMs:prepMs.current,choice:null,timeout:false})}
      catch {
        const actual=await api<{stage:string;status:string}>(`/sessions/${session.sessionId}`)
        if(actual.status!=='IN_PROGRESS'||actual.stage!=='RECORDING')throw Error('Could not begin recording. Exit and start a new attempt after checking the server.')
      }
      if(String(current.current)!=='STARTING')return
      if(mic.stream.getAudioTracks().some(t=>t.readyState!=='live'))throw Error('Microphone disconnected.')
      const chunks:BlobPart[]=[];let bytes=0
      mic.listen(e=>{
        if(e.data.size){chunks.push(e.data);bytes+=e.data.size}
        if(bytes>50*1024*1024)void fail('Recording exceeded the 50 MiB limit. This attempt is interrupted.')
      },()=>{void fail('Recording failed. Check microphone access and start a new attempt.')},()=>{
        if(current.current==='RECORDING'){void fail('Recording stopped unexpectedly. This attempt is interrupted.');return}
        if(current.current!=='STOPPING')return
        const blob=new Blob(chunks,{type:mic.recorder.mimeType})
        mic.release()
        if(!blob.size){void fail('No audio was captured. Check your microphone and start a new attempt.');return}
        audio.current={blob,durationMs:stopDuration.current};void upload()
      })
      await cue()
      if(String(current.current)!=='STARTING')return
      mic.activate()
      mic.recorder.start(1000)
      recordAt.current=performance.now();setRecordStart(recordAt.current);move('RECORDING')
    }catch(e){void fail(e instanceof Error?e.message:'Could not start recording.')}
  }
  function stopRecording() {
    if(current.current!=='RECORDING')return
    stopDuration.current=clock(recordAt.current,performance.now(),null).elapsed
    setRecordedElapsed(stopDuration.current);move('STOPPING');setExitConfirm(false)
    void cue();try{mic.recorder.stop()}catch{void fail('Recording could not be stopped safely. This attempt is interrupted.')}
  }
  const prepTime=usePracticeTimer(prepStart,practice?null:session.timing.preparationSeconds*1000,phase==='PREPARATION',()=>void startRecording(),()=>void fail('Device suspension made timing unreliable. This attempt is interrupted.','SUSPENDED'))
  const recordingTime=usePracticeTimer(recordStart,practice?null:session.timing.answerSeconds*1000,phase==='RECORDING',stopRecording,()=>void fail('Device suspension made recording timing unreliable. This attempt is interrupted.','SUSPENDED'))
  const deviceEnded=useEffectEvent(()=>{void fail('Microphone access ended. Check permissions or reconnect your microphone.')})
  useEffect(()=>{
    const unmount=mic.mount()
    const samples=new Uint8Array(mic.analyser.fftSize)
    const tick=setInterval(()=>{
      if(mic.closed){setLevel(0);return}
      mic.analyser.getByteTimeDomainData(samples)
      const rms=Math.sqrt(samples.reduce((sum,value)=>sum+((value-128)/128)**2,0)/samples.length)
      setLevel(Math.min(1,rms*4))
    },100)
    const ended=()=>deviceEnded()
    mic.stream.getAudioTracks().forEach(track=>track.addEventListener('ended',ended))
    const unload=(e:BeforeUnloadEvent)=>{if(current.current!=='COMPLETED'){e.preventDefault();e.returnValue=''}}
    const pagehide=()=>{if(current.current!=='COMPLETED')void fetch(`/api/sessions/${session.sessionId}/interrupt`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({reason:'BROWSER_RELOAD'}),keepalive:true}).catch(()=>{})}
    window.addEventListener('beforeunload',unload);window.addEventListener('pagehide',pagehide)
    return ()=>{unmount();clearInterval(tick);mic.stream.getAudioTracks().forEach(t=>t.removeEventListener('ended',ended));window.removeEventListener('beforeunload',unload);window.removeEventListener('pagehide',pagehide)}
  // Device handling reads the current stage through refs, rather than registering new handlers per tick.
  },[mic,session.sessionId])
  async function exit() {
    if(busy.current||['STARTING','STOPPING','UPLOADING'].includes(current.current))return
    move('INTERRUPTED');setExitConfirm(false)
    if(mic.recorder.state!=='inactive')mic.recorder.stop()
    mic.release()
    try{await interrupt();onHome()}catch{setError('Could not mark the attempt interrupted. Retry Exit after checking the server.')}
  }
  async function copy() {
    try{const value=await api<{text:string}>(`/attempts/${session.attemptId}/evaluation-context`);setContext(value.text);await navigator.clipboard.writeText(value.text);setCopyStatus('Copied');showCopied('Copied! Ready to paste.')}catch{setCopyStatus('Copy failed. Retry or copy the context below.')}
  }
  if(phase==='COMPLETED'&&saved)return <main className="writing-result speaking-result-card"><h1>Recording successfully saved</h1><p>Speaking Task {session.taskNumber} — {speakingNames[session.taskNumber]}</p><p>Preparation: {formatDuration(saved.preparationMs)} · Speaking: {formatDuration(saved.durationMs)}</p><AudioConversionStatus attemptId={session.attemptId} originalUrl={saved.audioUrl}/><AttachmentControls attemptId={session.attemptId}/><AttachmentControls attemptId={session.attemptId} kind="ALL"/><p role="status">{notice}</p><div className="writing-result-actions"><button onClick={onHome}>Return to homepage</button><button className="navy-button" onClick={()=>void copy()}>Copy for ChatGPT</button></div><p role="status">{copyStatus}</p>{context&&copyStatus!=='Copied'&&<textarea aria-label="Evaluation context" readOnly value={context}/>}</main>
  const pending=['STARTING','STOPPING','UPLOADING'].includes(phase)
  return <div className="writing-exam"><header className="exam-header"><h1>Speaking Task {session.taskNumber} — {speakingNames[session.taskNumber]}</h1></header><main className={`speaking-main ${imageUrl||session.content.options?'image-speaking':''} ${session.content.options?'comparison-speaking':''}`}><section className="speaking-question"><p>{session.content.persuasionPrompt??session.content.prompt}</p>{session.content.alternatives?.map((text,index)=><p key={index}><strong>{index===0?'EITHER':'OR'}</strong><br/>{text}</p>)}{session.content.options&&<SpeakingOptions options={session.content.options} images={optionsImages??{}}/>}{imageUrl&&<a className="speaking-scene" href={imageUrl} target="_blank" rel="noreferrer" aria-label="Enlarge exercise image in a new tab"><img src={imageUrl} alt="Speaking exercise scene"/><span>Enlarge image ↗</span></a>}</section><aside className="speaking-controls"><div className="speaking-timers"><section className="speaking-timer active"><h2>{phase==='PREPARATION'||phase==='STARTING'?'Preparation':'Recording'}</h2><strong>{formatTime(phase==='PREPARATION'?prepTime.display:phase==='RECORDING'?recordingTime.display:recordedElapsed??0)}</strong></section></div>{phase==='RECORDING'&&<section className="microphone-status recording-panel" aria-label="Microphone recording">
    <div className="microphone-emblem" aria-hidden="true"><svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"><rect x="9" y="2" width="6" height="13" rx="3"/><path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3M8 22h8"/></svg></div>
    <p className="recording-status"><span className="recording-dot"/>Recording</p>
    <div className="voice-meter" role="meter" aria-label="Live microphone audio level" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(level*100)}>
      {Array.from({length:17},(_,index)=>{const shape=1-Math.abs(index-8)/10;return <span key={index} className={level>.02?'has-signal':''} style={{height:`${6+level*64*shape}px`}}/>})}
    </div>
    <p className="voice-meter-label">Speak naturally into your microphone</p>
    {notice&&<p role="status">{notice}</p>}
  </section>}</aside>{error&&<p role="alert" className="exam-error">{error}</p>}</main><footer className="exam-actions"><button disabled={pending} onClick={()=>setExitConfirm(true)}>Exit practice</button>{practice&&phase==='PREPARATION'&&<button className="navy-button" onClick={()=>void startRecording()}>Start recording</button>}{practice&&phase==='RECORDING'&&<button className="navy-button" onClick={stopRecording}>Stop recording and save</button>}{phase==='RETRY'&&<button className="navy-button" onClick={()=>void upload()}>Retry saving recording</button>}</footer><AttemptDialog speaking canSave={phase==='RETRY'} confirm={exitConfirm?'exit':null} sending={pending} setConfirm={()=>setExitConfirm(false)} exit={exit} submit={upload}/></div>
}
