import type { Mode } from './practice'
export type Block = {type:'TEXT';text:string} | {type:'PARAGRAPH';text:string;label:string} | {type:'IMAGE';image:string} | {type:'TABLE';headers:string[];rows:string[][]}
export type Question = {id:string;prompt?:string;options:string[]}
export type Group = {type:'MULTIPLE_CHOICE'|'INLINE_BLANK'|'PARAGRAPH_MATCH';text?:string;questions:Question[]}
export type ReadingContent = {instructions:string;passage:Block[];questionGroups:Group[]}
export type ReadingResponse = {answers:Record<string,number|null>}
export type ReadingSession = {sessionId:string;attemptId:string;taskNumber:number;mode:Mode;timing:{answerSeconds:number};content:ReadingContent}
export type ReadingReview = {response:ReadingResponse;durationMs:number;status:string;result:{correct:number;total:number;questions:{questionId:string;correct:boolean;selectedOption:number|null;correctOption:number;explanation:string}[]}}
export const partNames = ['Correspondence','Applying a Diagram','Reading for Information','Reading for Viewpoints']
export function inlineSegments(text:string) { return text.split(/(\{\{Q\d{2}\}\})/g) }
export function selectedIndex(value:string) { return value === '' ? null : Number(value) }
export function resultCounts(review:ReadingReview) {
  const unanswered = review.result.questions.filter(q=>q.selectedOption == null).length
  return {correct:review.result.correct,incorrect:review.result.total-review.result.correct-unanswered,unanswered}
}

export type ReadingRecoveryData={sessionId:string;attemptId:string;response:ReadingResponse}
export function readReadingRecovery():ReadingRecoveryData|undefined {
  try {const value=JSON.parse(sessionStorage.getItem('celpip-reading-recovery')??'null');if(value?.response?.answers && typeof value.sessionId==='string' && typeof value.attemptId==='string') return value} catch { /* Nothing recoverable. */ }
}
