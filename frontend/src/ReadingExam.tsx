import { useEffect, useState } from 'react'
import { api, formatTime } from './practice'
import { useAttempt } from './useAttempt'
import AttemptDialog from './AttemptDialog'
import { Passage, QuestionGroups } from './ReadingContent'
import { partNames, resultCounts } from './reading'
import type { ReadingSession, ReadingResponse, ReadingReview } from './reading'

/** Decode all controlled assets before mounting the timed examination. */
export default function ReadingExam({session,onHome}:{session:ReadingSession;onHome:()=>void}) {
  const [images,setImages]=useState<Record<string,string>>()
  const [error,setError]=useState('')
  const [retry,setRetry]=useState(0)
  useEffect(()=>{
    let cancelled=false
    const urls:string[]=[]
    async function load() {
      try {
        const loaded:Record<string,string>={}
        for(const block of session.content.passage) if(block.type === 'IMAGE' && !loaded[block.image]) {
          const response=await fetch(`/api/assets/${encodeURIComponent(block.image)}?sessionId=${session.sessionId}`,{cache:'no-store'})
          if(!response.ok) throw new Error('Diagram could not be loaded. Check the local server and retry.')
          const url=URL.createObjectURL(await response.blob()); urls.push(url)
          if(cancelled) { URL.revokeObjectURL(url); return }
          const image=new Image(); image.src=url; await image.decode()
          loaded[block.image]=url
        }
        if(!cancelled) { setImages(loaded); setError('') }
      } catch { if(!cancelled) setError('The required diagram could not be loaded. The timer has not started. Retry loading or exit.') }
    }
    void load()
    return ()=>{cancelled=true;urls.forEach(url=>URL.revokeObjectURL(url))}
  },[session,retry])
  async function exitLoading() {
    if(!window.confirm('Exit this attempt? It will be marked interrupted.')) return
    try { await api(`/sessions/${session.sessionId}/interrupt`,'POST',{reason:'ABANDONED'}); sessionStorage.removeItem('celpip-reading-recovery'); onHome() }
    catch {setError('Could not exit. Check the server and retry.')}
  }
  if(!images) return <main className="writing-result"><h1>Loading Reading exercise</h1><p role="status">{error || 'Preparing the passage and required diagrams. The timer has not started.'}</p>{error && <button onClick={()=>setRetry(retry+1)}>Retry loading</button>} <button onClick={()=>void exitLoading()}>Exit practice</button></main>
  return <ReadingAttempt session={session} images={images} onHome={onHome} />
}
export function ReadingAttempt({session,images,onHome}:{session:ReadingSession;images:Record<string,string>;onHome:()=>void}) {
  const attempt=useAttempt<ReadingResponse,ReadingReview>(session,{answers:{}},'celpip-reading-recovery',onHome)
  const {response,review,locked,sending,interrupted,confirm,setConfirm,draftState,error,time,limit,change,submit,exit,copy,copyStatus,fallback}=attempt
  const questions=session.content.questionGroups.flatMap(group=>group.questions)
  const answered=questions.filter(q=>response.answers[q.id]!=null).length
  const title=`Reading Part ${session.taskNumber} — ${partNames[session.taskNumber-1]}`
  if(review) return <ReadingResults session={session} review={review} images={images} onHome={onHome} copy={copy} copyStatus={copyStatus} fallback={fallback}/>
  return <div className="writing-exam reading-exam"><header className="exam-header"><h1>{title}</h1><div role="timer" aria-label={limit===null?'Elapsed time':'Time remaining'}><span>{limit===null?'Elapsed':'Time remaining'}</span><strong>{formatTime(time.display)}</strong></div></header>
    <main className="reading-columns"><section className="question-panel" aria-label="Reading passage"><Passage blocks={session.content.passage} images={images}/></section><section className="question-panel" aria-label="Reading questions"><p>{session.content.instructions}</p><QuestionGroups groups={session.content.questionGroups} response={response} disabled={locked} onChange={(id,value)=>change({answers:{...attempt.latest.current.answers,[id]:value}})}/><p role="status">{draftState}</p>{draftState==='Save failed' && !locked && <button onClick={()=>change(response)}>Retry draft save</button>}</section></main>
    <footer className="exam-actions"><button disabled={sending || (locked && !interrupted)} onClick={()=>setConfirm('exit')}>Exit practice</button><span>{answered} / {questions.length} answered</span><button className="navy-button" disabled={sending || interrupted} onClick={()=>locked?void submit():setConfirm('submit')}>{sending?'Saving answers…':interrupted?'Attempt interrupted':locked?'Retry submission':'Submit answers'}</button></footer>
    {error && <p className="exam-error" role="alert">{error} Your answers are preserved on this page.</p>}
    <AttemptDialog reading confirm={confirm} sending={sending} setConfirm={setConfirm} exit={exit} submit={submit}/>
  </div>
}

export function ReadingResults({session,review,images,onHome,copy,copyStatus,fallback}:{session:Pick<ReadingSession,'taskNumber'|'content'>;review:ReadingReview;images:Record<string,string>;onHome:()=>void;copy:()=>Promise<void>;copyStatus:string;fallback:string}) {
  const title=`Reading Part ${session.taskNumber} — ${partNames[session.taskNumber-1]}`
  const questions=session.content.questionGroups.flatMap(group=>group.questions)
  const counts=resultCounts(review)
    return <main className="reading-results"><h1>{title}</h1><h2>{review.result.correct} / {review.result.total} correct</h2><p>{counts.correct} correct · {counts.incorrect} incorrect · {counts.unanswered} unanswered · {formatTime(review.durationMs)} actual duration</p>
      <div className="reading-columns"><section className="question-panel" aria-label="Original passage"><Passage blocks={session.content.passage} images={images}/></section><section className="question-panel" aria-label="Answer review">
        {review.result.questions.map(result=>{
          const q=questions.find(q=>q.id===result.questionId)!
          const group=session.content.questionGroups.find(g=>g.questions.some(item=>item.id===q.id))!
          const status=result.selectedOption==null?'Unanswered':result.correct?'Correct':'Incorrect'
          return <article className={`answer-review ${status.toLowerCase()}`} key={q.id}><h3>{q.id} · {status}</h3><p>{q.prompt ?? group.text?.replace(/\{\{(Q\d{2})\}\}/g, '[$1]')}</p><p>Your answer: {result.selectedOption==null?'Unanswered':q.options[result.selectedOption]}</p><p>Correct answer: {q.options[result.correctOption]}</p><p>{result.explanation}</p></article>
        })}
      </section></div><div className="writing-result-actions"><button onClick={onHome}>Return to homepage</button><button className="navy-button" onClick={()=>void copy()}>Copy for ChatGPT</button></div><p role="status">{copyStatus}</p>{fallback && copyStatus!=='Copied' && <textarea readOnly aria-label="Evaluation context" value={fallback}/>}</main>
}
