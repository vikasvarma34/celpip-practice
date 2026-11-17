import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { readFile } from 'node:fs/promises'

test('both Writing tasks render complete canonical content and accessible editor in every mode', async () => {
  const vite = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
  try {
    const { default: WritingExam } = await vite.ssrLoadModule('/src/WritingExam.tsx')
    for (const task of [1,2]) for (const mode of ['PRACTICE','TIMED','EXAM']) {
      const fixture = JSON.parse(await readFile(new URL(`../../fixtures/writing-${task}.json`, import.meta.url),'utf8'))
      const content = fixture.exercises[0].content
      const html = renderToStaticMarkup(createElement(WritingExam,{session:{sessionId:'synthetic',attemptId:'synthetic',taskNumber:task,mode,timing:{answerSeconds:task===1?1620:1560},content},onHome:()=>{}}))
      for (const text of [content.scenario,content.instructions,...(content.requirements??[]),...(content.options??[]).map((o:{text:string})=>o.text)]) assert.ok(html.includes(text))
      assert.ok(html.includes('spellCheck="true"')); assert.ok(html.includes('autoCorrect="off"')); assert.ok(html.includes('Recommended: 150–200'))
      assert.ok(html.includes(mode==='PRACTICE'?'Elapsed':'Time remaining'))
      assert.ok(html.includes(mode==='PRACTICE'?'00:00':task===1?'27:00':'26:00'))
      assert.equal((html.match(/type="radio"/g)??[]).length,task===1?0:2)
      assert.ok(!html.includes('My target')); assert.ok(!html.includes('Choose your practice'))
    }
  } finally { await vite.close() }
})
test('initial module import pages fetch no session or question and reveal no exercise content', async () => {
  const vite = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
  try {
    const { default: ImportScreen } = await vite.ssrLoadModule('/src/ImportScreen.tsx')
    for(const module of ['READING','WRITING','SPEAKING']) {
      const html = renderToStaticMarkup(createElement(ImportScreen,{module,onStart:()=>{throw new Error('Unexpected start')}}))
      assert.ok(html.includes('Load exercise')); assert.ok(!html.includes('Synthetic scenario')); assert.ok(!html.includes('writing-response'))
    }
  } finally { await vite.close() }
})
