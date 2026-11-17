import { useId, useState } from 'react'
import { formatDuration } from './practice'
import { Passage } from './ReadingContent'
import { inlineSegments, partNames, resultCounts } from './reading'
import type { ReadingSession, ReadingReview, Group } from './reading'

type Props={session:Pick<ReadingSession,'taskNumber'|'content'>;review:ReadingReview;images:Record<string,string>;onHome:()=>void;copy:()=>Promise<void>;copyStatus:string;fallback:string}
function blankContext(group:Group,id:string){
 const text=group.text??'',token=`{{${id}}}`,index=text.indexOf(token)
 if(index<0)return `Blank ${Number(id.slice(1))}`
 const before=text.slice(0,index),after=text.slice(index+token.length)
 const boundaries=[...before.matchAll(/[.!?](?:\s+)|\n/g)]
 let start=boundaries.length?boundaries[boundaries.length-1].index!+boundaries[boundaries.length-1][0].length:0
 const endMatch=after.match(/[.!?](?=\s|$)|\n/)
 let end=endMatch?index+token.length+endMatch.index!+1:text.length
 // Keep long single-sentence email/comment groups from becoming repeated passages.
 if(end-start>360){start=Math.max(start,index-130);end=Math.min(end,index+token.length+180)}
 return `${start>0?'… ':''}${text.slice(start,end).replace(/\{\{(Q\d{2})\}\}/g,(_,question:string)=>`[Blank ${Number(question.slice(1))}]`)}${end<text.length?' …':''}`
}
export default function ReadingResults({session,review,images,onHome,copy,copyStatus,fallback}:Props){
 const counts=resultCounts(review)
 const [filter,setFilter]=useState(()=>counts.incorrect+counts.unanswered?'mistakes':'all')
 const [view,setView]=useState('mine')
 const [expanded,setExpanded]=useState<Record<string,boolean>>({})
 const prefix=useId()
 const questions=session.content.questionGroups.flatMap(group=>group.questions.map(question=>({question,group})))
 const byId=new Map(review.result.questions.map(result=>[result.questionId,result]))
 const status=(result:ReadingReview['result']['questions'][number])=>result.selectedOption==null?'Unanswered':result.correct?'Correct':'Incorrect'
 const visible=questions.filter(({question})=>{const result=byId.get(question.id);return result&&(filter==='all'||(filter==='correct'?result.correct:!result.correct))})
 function openBlank(id:string){
  setFilter('all');setExpanded(current=>({...current,[id]:true}))
  requestAnimationFrame(()=>{const button=document.getElementById(`${prefix}-${id}`);button?.focus({preventScroll:true});button?.scrollIntoView({block:'nearest'})})
 }
 function toggleAll(open:boolean){setExpanded(current=>{const next={...current};visible.forEach(({question})=>{next[question.id]=open});return next})}
 const groups=session.content.questionGroups.filter(group=>group.type==='INLINE_BLANK')
 return <main className="reading-results reading-review-results">
  <h1>Reading Part {session.taskNumber} — {partNames[session.taskNumber-1]}</h1>
  <div className="reading-result-panels">
   <section className="reading-reference review-scroll" aria-label="Reference material"><h2>Reference material</h2><Passage blocks={session.content.passage} images={images}/>
    {groups.length>0&&<section className="completed-passage"><h2>Completed passage</h2><div className="review-tabs" aria-label="Completed passage view"><button aria-pressed={view==='mine'} onClick={()=>setView('mine')}>My answers</button><button aria-pressed={view==='correct'} onClick={()=>setView('correct')}>Correct answers</button></div>
     {groups.map((group,index)=><p key={index} className="completed-text">{inlineSegments(group.text!).map((segment,position)=>{
      const question=group.questions.find(question=>`{{${question.id}}}`===segment)
      if(!question)return <span key={`text-${position}`}>{segment}</span>
      const result=byId.get(question.id)
      if(!result)return <span key={question.id}>[Unanswered]</span>
      const unanswered=result.selectedOption==null
      const answer=view==='correct'?question.options[result.correctOption]:unanswered?`Unanswered — ${Number(question.id.slice(1))}`:question.options[result.selectedOption!]
      const tone=view==='correct'||result.correct?'correct':unanswered?'unanswered':'incorrect'
      return <button key={question.id} className={`blank-answer ${tone}`} onClick={()=>openBlank(question.id)} aria-label={`Question ${Number(question.id.slice(1))}, ${view==='correct'?'correct answer':status(result)}: ${answer}. Open explanation.`} title={`Question ${Number(question.id.slice(1))} · ${view==='correct'?'Correct answer':status(result)}`}>{answer}</button>
     })}</p>)}<p className="review-hint">Select a highlighted answer to open its explanation.</p>
    </section>}
   </section>
   <section className="reading-answer-panel review-scroll" aria-label="Answer review"><h2>Answer review</h2>
    <dl className="reading-score-grid">{[['Total questions',review.result.total],['Correct',counts.correct],['Incorrect',counts.incorrect],['Unanswered',counts.unanswered],['Raw score',`${review.result.correct} / ${review.result.total}`]].map(([label,value])=><div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl><p className="review-hint">{formatDuration(review.durationMs)} actual duration</p>
    <div className="review-tabs" aria-label="Question filters">{[['all','All'],['mistakes','Incorrect and unanswered'],['correct','Correct']].map(([value,label])=><button key={value} aria-pressed={filter===value} onClick={()=>setFilter(value)}>{label}</button>)}</div>
    <div className="review-expand-controls"><button onClick={()=>toggleAll(true)}>Expand all</button><button onClick={()=>toggleAll(false)}>Collapse all</button></div>
    {visible.length===0&&<p role="status">No questions in this filter.</p>}
    {visible.map(({question,group})=>{
     const result=byId.get(question.id)!,label=status(result),open=!!expanded[question.id]
     const prompt=question.prompt??blankContext(group,question.id)
     return <article key={question.id} className={`reading-review-card ${label.toLowerCase()}`}>
      <button id={`${prefix}-${question.id}`} className="review-card-toggle" aria-expanded={open} aria-controls={`${prefix}-${question.id}-details`} onClick={()=>setExpanded(current=>({...current,[question.id]:!open}))}>
       <span className="review-card-title"><strong>Question {Number(question.id.slice(1))}</strong><span className={`review-status ${label.toLowerCase()}`}>{label}</span><span aria-hidden="true">{open?'−':'+'}</span></span>
       <span className="review-question-text">{prompt}</span><span className="review-selected">My answer: {result.selectedOption==null?'Unanswered':question.options[result.selectedOption]}</span>
      </button>
      {open&&<div className="review-card-details" id={`${prefix}-${question.id}-details`}><h3>{group.type==='INLINE_BLANK'?'Sentence containing the blank':'Complete question'}</h3><p className="review-question-text">{prompt}</p><ul className="review-answer-options">{question.options.map((option,index)=>{
       const chosen=result.selectedOption===index,right=result.correctOption===index
       return <li key={index} className={right?'correct':chosen?'incorrect':'neutral'}><span>{option}</span><span className="option-answer-label">{chosen?'Your selection':''}{chosen&&right?' · ':''}{right?'Correct answer':''}</span></li>
      })}</ul><h3>Explanation</h3><p className="review-question-text">{result.explanation?.trim()?result.explanation:'No explanation was supplied for this question.'}</p></div>}
     </article>
    })}
   </section>
  </div>
  <div className="writing-result-actions"><button onClick={onHome}>Return to homepage</button><button className="navy-button" onClick={()=>void copy()}>Copy for ChatGPT</button></div><p role="status">{copyStatus}</p>{fallback&&copyStatus!=='Copied'&&<textarea readOnly aria-label="Evaluation context" value={fallback}/>}
 </main>
}
