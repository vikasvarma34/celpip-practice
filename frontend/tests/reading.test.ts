import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { readFile } from 'node:fs/promises'
import { inlineSegments,selectedIndex,resultCounts } from '../src/reading.ts'
import { ResponseWriter,clock } from '../src/practice.ts'
import type { ReadingResponse,ReadingReview } from '../src/reading.ts'

test('inline blanks preserve surrounding text, punctuation and question order',()=>{
  assert.deepEqual(inlineSegments('Dear {{Q01}},\n{{Q02}}.'),['Dear ','{{Q01}}',',\n','{{Q02}}','.'])
  assert.equal(selectedIndex(''),null);assert.equal(selectedIndex('0'),0);assert.equal(selectedIndex('4'),4)
})
test('Reading nested answers are snapshotted; failed submission retries exact answers and duration',async()=>{
  const sent:unknown[]=[];let fail=true
  const writer=new ResponseWriter<ReadingResponse,ReadingReview>(async(_m,action,body)=>{sent.push(structuredClone(body));if(action==='submit' && fail){fail=false;throw Error('offline')}return {status:'COMPLETED'}})
  const response={answers:{Q01:0 as number|null}};const draft=writer.draft(response);response.answers.Q01=3;await draft
  assert.deepEqual(sent[0],{answers:{Q01:0}})
  await assert.rejects(writer.submit(response,100));response.answers.Q01=null
  await writer.submit(response,200);assert.deepEqual(sent[1],sent[2]);assert.deepEqual(sent[1],{response:{answers:{Q01:3}},durationMs:100})
})
test('Reading result counts use backend correctness and distinguish unanswered',()=>{
  assert.deepEqual(resultCounts({result:{correct:1,total:3,questions:[{selectedOption:0},{selectedOption:2},{selectedOption:null}]}} as ReadingReview),{correct:1,incorrect:1,unanswered:1})
})
for(const part of [1,2,3,4]) test(`Reading Part ${part}: canonical rendering, dropdowns, all modes and no answer key`,async()=>{
  const vite=await createServer({server:{middlewareMode:true,hmr:false,ws:false},appType:'custom'})
  try {
    const {ReadingAttempt}=await vite.ssrLoadModule('/src/ReadingExam.tsx')
    const fixture=JSON.parse(await readFile(new URL(`../../fixtures/reading-${part}.json`,import.meta.url),'utf8'))
    const content=fixture.exercises[0].content
    for(const mode of ['PRACTICE','TIMED','EXAM']) {
      const html=renderToStaticMarkup(createElement(ReadingAttempt,{session:{sessionId:'synthetic',attemptId:'synthetic',taskNumber:part,mode,timing:{answerSeconds:600},content},images:{},onHome:()=>{}}))
      assert.equal((html.match(/<select /g)??[]).length,[11,8,9,10][part-1])
      assert.ok(!html.includes('{{Q'));assert.ok(!html.includes('Synthetic validation key'));assert.ok(!html.includes('Correct answer:'))
      assert.ok(html.includes(mode==='PRACTICE'?'Elapsed':'Time remaining'))
      for(const group of content.questionGroups) for(const q of group.questions) {
        const options=q.options.map((text:string,index:number)=>`value="${index}">${text}</option>`)
        for(const option of options) assert.ok(html.includes(option))
      }
      if(part===2)assert.ok(html.includes('<table>'))
      if(part===3)for(const letter of ['A','B','C','D'])assert.ok(html.includes(`Paragraph ${letter}`))
      assert.ok(clock(0,700000,mode==='PRACTICE'?null:600000).expired===(mode!=='PRACTICE'))
    }
  }finally{await vite.close()}
})
test('image passage keeps proportions via shared renderer and supports full-size opening',async()=>{
  const vite=await createServer({server:{middlewareMode:true,hmr:false,ws:false},appType:'custom'})
  try{const {Passage}=await vite.ssrLoadModule('/src/ReadingContent.tsx');const html=renderToStaticMarkup(createElement(Passage,{blocks:[{type:'IMAGE',image:'opaque-id'}],images:{'opaque-id':'blob:test'}}));assert.ok(html.includes('src="blob:test"'));assert.ok(html.includes('Open diagram at full size'))}finally{await vite.close()}
})

test('Reading results show raw counts, saved selections, correct answers, explanations and action order',async()=>{
  const vite=await createServer({server:{middlewareMode:true,hmr:false,ws:false},appType:'custom'})
  try {
    const {ReadingResults}=await vite.ssrLoadModule('/src/ReadingExam.tsx')
    const fixture=JSON.parse(await readFile(new URL('../../fixtures/reading-1.json',import.meta.url),'utf8'))
    const review={status:'COMPLETED',durationMs:1234,response:{answers:{Q01:0,Q02:1}},result:{correct:1,total:3,questions:[{questionId:'Q01',correct:true,selectedOption:0,correctOption:0,explanation:'Evidence one'},{questionId:'Q02',correct:false,selectedOption:1,correctOption:0,explanation:'Evidence two'},{questionId:'Q03',correct:false,selectedOption:null,correctOption:0,explanation:'Evidence three'}]}}
    const html=renderToStaticMarkup(createElement(ReadingResults,{session:{taskNumber:1,content:fixture.exercises[0].content},review,images:{},onHome:()=>{},copy:async()=>{},copyStatus:'',fallback:''}))
    for(const text of ['1 / 3 correct','1 correct · 1 incorrect · 1 unanswered','Q01 · Correct','Q02 · Incorrect','Q03 · Unanswered','Your answer: Wednesday','Correct answer: Tuesday','Evidence three','Synthetic Part 1 passage']) assert.ok(html.includes(text))
    assert.ok(html.indexOf('Return to homepage')<html.indexOf('Copy for ChatGPT'))
  }finally{await vite.close()}
})
