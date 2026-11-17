import { copied as showCopied } from './notifications'
import { useEffect, useState } from 'react'
import { api } from './practice'
import { ReadingResults } from './ReadingExam'
import type { ReadingSession, ReadingReview } from './reading'
import type { ReadingRecoveryData } from './reading'
export default function ReadingRecovery({recovery,onHome}:{recovery:ReadingRecoveryData;onHome:()=>void}) {
  const [status,setStatus]=useState('Recovering interrupted answers…')
  const [done,setDone]=useState(false)
  const [retry,setRetry]=useState(0)
  const [review,setReview]=useState<ReadingReview & Pick<ReadingSession,'taskNumber'|'content'>>()
  const [copied,setCopied]=useState('')
  const [fallback,setFallback]=useState('')
  useEffect(()=>{
    let cancelled=false
    async function recover() {
      try {
        const session=await api<{status:string}>(`/sessions/${recovery.sessionId}`)
        if(session.status==='IN_PROGRESS') await api(`/sessions/${recovery.sessionId}/interrupt`,'POST',{reason:'BROWSER_RELOAD'})
        const stored=await api<ReadingReview & Pick<ReadingSession,'taskNumber'|'content'>>(`/attempts/${recovery.attemptId}/review`)
        if(!cancelled)setReview(stored)
        if(!cancelled){setDone(true);setStatus(session.status==='COMPLETED'?'This attempt was submitted. Its saved results remain in the database.':'This attempt is interrupted. Latest local selections are preserved below; earlier backend drafts remain stored. Start a new attempt to practise again.')}
      }catch{if(!cancelled)setStatus('Could not reconcile the attempt. Keep this page open and retry.')}
    }
    void recover();return ()=>{cancelled=true}
  },[recovery.sessionId,recovery.attemptId,retry])
  function home() { sessionStorage.removeItem('celpip-reading-recovery'); onHome() }
  async function copy() {
    try {const context=await api<{text:string}>(`/attempts/${recovery.attemptId}/evaluation-context`);setFallback(context.text);await navigator.clipboard.writeText(context.text);setCopied('Copied');showCopied('Copied! Ready to paste.')}catch{setCopied('Copy failed. Retry or select the context below.')}
  }
  if(review?.status==='COMPLETED') {
    const images:Record<string,string>={}
    for(const block of review.content.passage) if(block.type==='IMAGE') images[block.image]=`/api/assets/${block.image}?attemptId=${recovery.attemptId}`
    return <ReadingResults session={review} review={review} images={images} onHome={home} copy={copy} copyStatus={copied} fallback={fallback}/>
  }
  const questions=review?.content.questionGroups.flatMap(group=>group.questions)??[]
  return <main className="writing-result"><h1>Recovered Reading answers</h1><p role="status">{status}</p><ul>{Object.entries(recovery.response.answers).map(([id,index])=><li key={id}>{id}: {index==null?'Unanswered':questions.find(q=>q.id===id)?.options[index]??`Option ${index+1}`}</li>)}</ul><button disabled={!done} onClick={home}>Return to homepage</button>{!done && <button onClick={()=>setRetry(retry+1)}>Retry recovery</button>}</main>
}
