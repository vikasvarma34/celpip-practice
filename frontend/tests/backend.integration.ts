import { test } from 'node:test'
import assert from 'node:assert/strict'
import { spawn, execFileSync } from 'node:child_process'
import { mkdtemp, readFile, realpath, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { once } from 'node:events'
import { wordCount } from '../src/practice.ts'

test('isolated backend: Writing and Reading/all modes, scoring, drafts, duplicates, interruptions, assets and neutral responses', { timeout: 60000 }, async () => {
  const data = await realpath(await mkdtemp(join(tmpdir(),'celpip-writing-test-')))
  const javaHome = execFileSync('/usr/libexec/java_home',['-v','21'],{encoding:'utf8'}).trim()
  const backend = spawn(join(javaHome,'bin/java'),['-jar',new URL('../../backend/target/celpip-practice-0.0.1-SNAPSHOT.jar',import.meta.url).pathname,'--server.port=0',`--celpip.data-dir=${data}`],{stdio:['ignore','pipe','pipe']})
  let log = ''; backend.stdout.on('data',b=>{log+=b}); backend.stderr.on('data',b=>{log+=b})
  try {
    let port: string | undefined
    for(let i=0;i<200;i++) { port = log.match(/Tomcat started on port (\d+)/)?.[1]; if(port) break; if(backend.exitCode!==null) throw new Error(log.slice(-6000)); await new Promise(r=>setTimeout(r,100)) }
    assert.ok(port,log)
    async function request(path: string, method='GET', body?:unknown, expected=200) {
      const response=await fetch(`http://127.0.0.1:${port}/api${path}`,{method,headers:body===undefined?undefined:{'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)})
      const value=await response.json(); assert.equal(response.status,expected,JSON.stringify(value)); return value
    }
    for(const task of [1,2]) {
      const fixture=JSON.parse(await readFile(new URL(`../../fixtures/writing-${task}.json`,import.meta.url),'utf8'))
      const form=new FormData(); form.append('json',JSON.stringify(fixture)); form.append('expectedModule','WRITING')
      const imported=await fetch(`http://127.0.0.1:${port}/api/imports`,{method:'POST',body:form}); assert.ok(imported.ok); const receipt=await imported.json()
      for(const neutral of [receipt,await request(`/packages/${receipt.packageId}`)]) for(const key of ['content','scenario','instructions','answerKey','images']) assert.ok(!JSON.stringify(neutral).includes(`"${key}"`))
      for(const mode of ['PRACTICE','TIMED','EXAM']) {
        const session=await request('/sessions','POST',{packageId:receipt.packageId,mode})
        assert.equal(session.timing.answerSeconds,task===1?1620:1560); assert.deepEqual(session.content,fixture.exercises[0].content)
        const text='  Synthetic exact response.\n\nSecond\u00a0paragraph\u2003with words.  '
        const response=task===2?{text,choice:'B'}:{text}
        await request(`/attempts/${session.attemptId}/draft`,'PATCH',response)
        assert.deepEqual((await request(`/sessions/${session.sessionId}`)).draft,response)
        const body={response,durationMs:1278}
        const result=await request(`/attempts/${session.attemptId}/submit`,'POST',body)
        assert.deepEqual(result.response,response); assert.equal(result.result.wordCount,wordCount(text)); assert.equal(result.durationMs,1278)
        assert.deepEqual(await request(`/attempts/${session.attemptId}/submit`,'POST',body),result)
        await request(`/attempts/${session.attemptId}/submit`,'POST',{...body,durationMs:1279},409)
        await request(`/attempts/${session.attemptId}/draft`,'PATCH',response,409)
        const context=await request(`/attempts/${session.attemptId}/evaluation-context`); assert.ok(context.text.includes(text))
        assert.deepEqual((await request(`/attempts/${session.attemptId}/review`)).response,response)
      }
      const session=await request('/sessions','POST',{packageId:receipt.packageId,mode:'EXAM'})
      const response=task===2?{text:'',choice:null}:{text:''}
      const result=await request(`/attempts/${session.attemptId}/submit`,'POST',{response,durationMs:session.timing.answerSeconds*1000})
      assert.equal(result.result.wordCount,0); assert.deepEqual(result.response,response)
      const abandoned=await request('/sessions','POST',{packageId:receipt.packageId,mode:'PRACTICE'})
      await request(`/attempts/${abandoned.attemptId}/draft`,'PATCH',response)
      await request(`/sessions/${abandoned.sessionId}/interrupt`,'POST',{reason:'ABANDONED'})
      const review=await request(`/attempts/${abandoned.attemptId}/review`); assert.equal(review.status,'INTERRUPTED'); assert.deepEqual(review.draft,response)
    }
    for(const task of [1,2,3,4]) {
      const fixture=JSON.parse(await readFile(new URL(`../../fixtures/reading-${task}.json`,import.meta.url),'utf8'))
      const form=new FormData();form.append('json',JSON.stringify(fixture));form.append('expectedModule','READING')
      const imported=await fetch(`http://127.0.0.1:${port}/api/imports`,{method:'POST',body:form});assert.ok(imported.ok);const receipt=await imported.json()
      for(const neutral of [receipt,await request(`/packages/${receipt.packageId}`)]) assert.ok(!JSON.stringify(neutral).includes('questionGroups'))
      for(const mode of ['PRACTICE','TIMED','EXAM']) {
        const session=await request('/sessions','POST',{packageId:receipt.packageId,mode})
        assert.deepEqual(session.content,fixture.exercises[0].content)
        assert.ok(!JSON.stringify(session).includes('correctOption'));assert.ok(!JSON.stringify(session).includes('explanation'))
        const response={answers:{Q01:fixture.exercises[0].answerKey.Q01.correctOption,Q02:1,Q03:null}}
        await request(`/attempts/${session.attemptId}/draft`,'PATCH',response)
        assert.deepEqual((await request(`/sessions/${session.sessionId}`)).draft,response)
        const body={response,durationMs:1234}
        const review=await request(`/attempts/${session.attemptId}/submit`,'POST',body)
        assert.equal(review.result.correct,1);assert.equal(review.result.total,[11,8,9,10][task-1]);assert.deepEqual(review.response,response)
        assert.equal(review.result.questions.filter((q:{selectedOption:number|null})=>q.selectedOption==null).length,review.result.total-2)
        for(const q of review.result.questions) {assert.equal(q.correctOption,fixture.exercises[0].answerKey[q.questionId].correctOption);assert.ok(q.explanation)}
        assert.deepEqual(await request(`/attempts/${session.attemptId}/submit`,'POST',body),review)
        await request(`/attempts/${session.attemptId}/submit`,'POST',{...body,durationMs:999},409)
        await request(`/attempts/${session.attemptId}/draft`,'PATCH',response,409)
        assert.ok((await request(`/attempts/${session.attemptId}/evaluation-context`)).text.includes('Q02'))
      }
      const session=await request('/sessions','POST',{packageId:receipt.packageId,mode:'TIMED'})
      const empty=await request(`/attempts/${session.attemptId}/submit`,'POST',{response:{answers:{}},durationMs:session.timing.answerSeconds*1000})
      assert.equal(empty.result.correct,0)
      const abandoned=await request('/sessions','POST',{packageId:receipt.packageId,mode:'PRACTICE'})
      await request(`/attempts/${abandoned.attemptId}/draft`,'PATCH',{answers:{Q01:2}})
      await request(`/sessions/${abandoned.sessionId}/interrupt`,'POST',{reason:'ABANDONED'})
      assert.deepEqual((await request(`/attempts/${abandoned.attemptId}/review`)).draft,{answers:{Q01:2}})
    }
    // An additional Part 2 package tests controlled IMAGE assets beside its table.
    const imageFixture=JSON.parse(await readFile(new URL('../../fixtures/reading-2.json',import.meta.url),'utf8'))
    imageFixture.packageId+='-IMAGE';imageFixture.exercises[0].exerciseId+='-IMAGE'
    imageFixture.exercises[0].content.passage.push({type:'IMAGE',image:'images/diagram.png'})
    const form=new FormData();form.append('json',JSON.stringify(imageFixture));form.append('assets',new Blob([await readFile(new URL('../../fixtures/images/asset-0.png',import.meta.url))],{type:'image/png'}),'diagram.png');form.append('assetPaths','images/diagram.png')
    const imported=await fetch(`http://127.0.0.1:${port}/api/imports`,{method:'POST',body:form});assert.ok(imported.ok,await imported.clone().text());const receipt=await imported.json()
    assert.ok(!JSON.stringify(receipt).includes('diagram'))
    const session=await request('/sessions','POST',{packageId:receipt.packageId,mode:'EXAM'})
    const asset=session.content.passage.find((b:{type:string})=>b.type==='IMAGE').image
    const image=await fetch(`http://127.0.0.1:${port}/api/assets/${asset}?sessionId=${session.sessionId}`);assert.equal(image.status,200);assert.equal(image.headers.get('content-type'),'image/png')
    assert.deepEqual(Buffer.from(await image.arrayBuffer()),await readFile(new URL('../../fixtures/images/asset-0.png',import.meta.url)))
    await request(`/sessions/${session.sessionId}/interrupt`,'POST',{reason:'ABANDONED'})

  } finally {
    if(backend.exitCode===null) { const stopped=once(backend,'exit'); backend.kill('SIGTERM'); await stopped }
    await rm(data,{recursive:true,force:true})
  }
})
