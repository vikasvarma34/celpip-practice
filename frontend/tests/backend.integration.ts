import { test } from 'node:test'
import assert from 'node:assert/strict'
import { spawn, execFileSync } from 'node:child_process'
import { mkdtemp, readFile, realpath, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { once } from 'node:events'
import { wordCount } from '../src/practice.ts'

test('isolated backend: Writing tasks/all modes, drafts, submissions, duplicates, interruption, neutral responses', { timeout: 60000 }, async () => {
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
  } finally {
    if(backend.exitCode===null) { const stopped=once(backend,'exit'); backend.kill('SIGTERM'); await stopped }
    await rm(data,{recursive:true,force:true})
  }
})
