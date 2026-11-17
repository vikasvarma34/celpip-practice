import { test } from 'node:test'
import assert from 'node:assert/strict'
import { clock, formatTime, wordCount, ResponseWriter } from '../src/practice.ts'
import type { Review } from '../src/practice.ts'
const result = { response: { text: 'exact' }, result: { wordCount: 1 }, durationMs: 100, status: 'COMPLETED' } as Review

test('word count matches backend Unicode whitespace rule without modifying text', () => {
  for (const [text, count] of [['',0], [' \n\t',0], ['  Hello\n\nworld!\u00a0three\u2003four ',4], ['well-being isn’t two\u200bwords',3], ['a\u0085b\u2028c\u3000d',4]] as const) assert.equal(wordCount(text), count)
})
for (const task of [1,2]) for (const mode of ['PRACTICE','TIMED','EXAM']) test(`task ${task} ${mode}: absolute clock survives throttling`, () => {
  const duration = (task === 1 ? 1620 : 1560) * 1000
  const limit = mode === 'PRACTICE' ? null : duration
  assert.equal(clock(500, 500, limit).display, limit ?? 0)
  assert.equal(clock(500, 60500, limit).display, limit === null ? 60000 : limit - 60000)
  assert.equal(clock(500, duration+10500, limit).expired, limit !== null)
  assert.equal(clock(500, duration+10500, limit).display, limit === null ? duration+10000 : 0)
})
test('timer formatting and nonnegative durations', () => {
  assert.equal(formatTime(1620000), '27:00'); assert.equal(formatTime(1560000),'26:00'); assert.equal(formatTime(1),'00:01'); assert.equal(clock(2,1,null).elapsed,0)
})
test('drafts serialize and preserve exact paragraphs and survey option', async () => {
  const calls: unknown[] = []; let release!: () => void
  const gate = new Promise<void>(r => { release = r })
  const writer = new ResponseWriter(async (_method, _action, body) => { calls.push(body); if(calls.length === 1) await gate })
  const first = writer.draft({text:'one',choice:'A'}); const next = writer.draft({text:'  final\n\nresponse  ',choice:'B'})
  await Promise.resolve(); assert.equal(calls.length,1); release(); await Promise.all([first,next])
  assert.deepEqual(calls,[{text:'one',choice:'A'},{text:'  final\n\nresponse  ',choice:'B'}])
})
test('manual and expiry race submits once, after outstanding drafts; later drafts ignored', async () => {
  const calls: string[] = []; const writer = new ResponseWriter(async (_method, action) => { calls.push(action); return result })
  const draft = writer.draft({text:'draft'}); const manual = writer.submit({text:'latest'},100); const expiry = writer.submit({text:'other'},200)
  assert.equal(manual, expiry); await writer.draft({text:'stale'}); await Promise.all([draft,manual,expiry]); await writer.submit({text:'changed'},300)
  assert.deepEqual(calls,['draft','submit'])
})
test('failed submit retries the immutable original text, choice and duration', async () => {
  const calls: unknown[] = []; const writer = new ResponseWriter(async (_method,_action,body) => { calls.push(body); if(calls.length === 1) throw new Error('lost response'); return result })
  await assert.rejects(writer.submit({text:'  original\n',choice:null},123)); await writer.submit({text:'changed',choice:'B'},999)
  assert.deepEqual(calls,[{response:{text:'  original\n',choice:null},durationMs:123},{response:{text:'  original\n',choice:null},durationMs:123}])
})
test('failed draft does not block saving a newer draft or latest submission', async () => {
  let count=0; const writer = new ResponseWriter(async () => { if(++count === 1) throw new Error('offline'); return result })
  await assert.rejects(writer.draft({text:'old'})); await writer.draft({text:'new'}); assert.equal(await writer.submit({text:'latest'},42),result)
})
test('interruption stops queued and future drafts and prevents submission', async () => {
  let count=0; const writer = new ResponseWriter(async () => { count++ }); const draft = writer.draft({text:'x'}); await writer.stop(); await draft; await writer.draft({text:'y'}); await assert.rejects(writer.submit({text:'z'},1)); assert.equal(count,0)
})
test('expiry can save an empty response with no survey selection', async () => {
  const writer = new ResponseWriter(async (_m,_a,body) => { assert.deepEqual(body,{response:{text:'',choice:null},durationMs:1560000}); return result })
  await writer.submit({text:'',choice:null},1560000)
})
