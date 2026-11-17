import { useEffect, useState } from 'react'
import { api } from './practice'
type Status={status:string;message:string;audioUrl:string|null}
export default function AudioConversionStatus({attemptId,originalUrl}:{attemptId:string;originalUrl:string}) {
  const [status,setStatus]=useState<Status>()
  const [error,setError]=useState('')
  const [retry,setRetry]=useState(0)
  const [busy,setBusy]=useState(false)
  useEffect(()=>{
    let cancelled=false,timer:number|undefined
    async function poll(){
      try{
        const value=await api<Status>(`/attempts/${attemptId}/audio-conversion`)
        if(cancelled)return
        setStatus(value);setError('')
        if(['QUEUED','CONVERTING'].includes(value.status))timer=window.setTimeout(()=>void poll(),1500)
      }catch{if(!cancelled)setError('Could not retrieve conversion status. Original recording remains saved.')}
    }
    void poll();return()=>{cancelled=true;clearTimeout(timer)}
  },[attemptId,retry])
  async function convert(){setBusy(true);try{const value=await api<Status>(`/attempts/${attemptId}/audio-conversion`,'POST');setStatus(value);setRetry(n=>n+1);setError('')}catch{setError('Could not request conversion. Keep the original WebM and retry.')}finally{setBusy(false)}}
  return <section><p role="status">{status?.message??'Checking M4A conversion status…'}</p>{error&&<p role="alert">{error}</p>}<p>{status?.status==='READY'?'M4A/AAC — recording intended for ChatGPT evaluation.':'Original WebM — preserved and available for playback.'}</p><audio key={status?.audioUrl??originalUrl} controls src={status?.audioUrl??originalUrl} preload="metadata"/>{(error||status?.status==='FAILED'||status?.status==='UNAVAILABLE')&&<button disabled={busy} onClick={()=>void convert()}>{busy?'Requesting…':'Retry M4A conversion'}</button>}</section>
}
