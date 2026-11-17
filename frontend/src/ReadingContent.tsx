import { inlineSegments, selectedIndex } from './reading'
import type { Block, Group, Question, ReadingResponse } from './reading'
export function Passage({ blocks, images }: {blocks:Block[];images:Record<string,string>}) {
  return <>{blocks.map((block,i)=> <div key={i} className="passage-block">{block.type === 'TEXT' ? <p>{block.text}</p> : block.type === 'PARAGRAPH' ? <section aria-label={`Paragraph ${block.label}`}><h2>Paragraph {block.label}</h2><p>{block.text}</p></section> : block.type === 'IMAGE' ? <a href={images[block.image]} target="_blank" rel="noreferrer" aria-label="Open diagram at full size"><img src={images[block.image]} alt="Reading passage diagram" /></a> : <div className="reading-table"><table><thead><tr>{block.headers.map((h,j)=><th scope="col" key={j}>{h}</th>)}</tr></thead><tbody>{block.rows.map((row,j)=><tr key={j}>{row.map((cell,k)=><td key={k}>{cell}</td>)}</tr>)}</tbody></table></div>}</div>)}</>
}
export function AnswerSelect({question,value,disabled,onChange}:{question:Question;value:number|null|undefined;disabled:boolean;onChange:(value:number|null)=>void}) {
  return <select id={`answer-${question.id}`} aria-label={`${question.id}${question.prompt ? `: ${question.prompt}` : ''}`} disabled={disabled} value={value ?? ''} onChange={e=>onChange(selectedIndex(e.target.value))}><option value="">{question.id} — Select answer</option>{question.options.map((text,index)=><option key={index} value={index}>{text}</option>)}</select>
}
export function QuestionGroups({groups,response,disabled,onChange}:{groups:Group[];response:ReadingResponse;disabled:boolean;onChange:(id:string,value:number|null)=>void}) {
  const control=(q:Question)=><AnswerSelect key={q.id} question={q} value={response.answers[q.id]} disabled={disabled} onChange={value=>onChange(q.id,value)} />
  return <>{groups.map((group,i)=><section className="question-group" key={i}>{group.type === 'INLINE_BLANK' ? <p className="inline-questions">{inlineSegments(group.text!).map((segment,j)=>{ const q=group.questions.find(q=>`{{${q.id}}}`===segment); return q ? control(q) : <span key={`text-${j}`}>{segment}</span> })}</p> : group.questions.map(q=><div className="reading-question" key={q.id}><label htmlFor={`answer-${q.id}`}>{q.id}. {q.prompt}</label><div id={`label-${q.id}`}>{control(q)}</div></div>)}</section>)}</>
}
