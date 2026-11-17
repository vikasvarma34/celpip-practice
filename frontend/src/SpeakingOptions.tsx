import type { SpeakingOption } from './speaking'
export default function SpeakingOptions({options,images,selected,onSelect,disabled=false}:{options:SpeakingOption[];images:Record<string,string>;selected?:string|null;onSelect?:(id:string)=>void;disabled?:boolean}) {
  return <div className="speaking-options" role={onSelect?'group':undefined} aria-label={onSelect?'Choose an option':undefined}>{options.map(option=><article key={option.id} className={`speaking-option ${selected===option.id?'selected':''}`}>
    {onSelect&&<button className="option-select-overlay" disabled={disabled} aria-pressed={selected===option.id} aria-label={`Select option ${option.id}: ${option.title}`} onClick={()=>onSelect(option.id)}/>}
    <h2>{onSelect?`Option ${option.id}`:option.id==='C'?'Their choice':'Your choice'} — {option.title}</h2>
    <div className="option-image"><img src={images[option.image]} alt={`Option ${option.id}: ${option.title}`}/><a href={images[option.image]} target="_blank" rel="noreferrer" aria-label={`Enlarge option ${option.id} image`}>Enlarge image ↗</a></div>
    <ul>{option.details.map((detail,index)=><li key={index}>{detail}</li>)}</ul>
  </article>)}</div>
}
