import { copied } from './notifications'
import { useState } from 'react'
import { api } from './practice'
export default function AttachmentControls({attemptId,kind='AUDIO',paired=false}:{attemptId:string;kind?:'AUDIO'|'IMAGE'|'ALL';paired?:boolean}){
 const [status,setStatus]=useState('')
 async function copy(filesOnly=false){try{await api('/clipboard','POST',{attemptId,kind:filesOnly?'ALL_FILES':kind,paired,path:false});setStatus('');copied('Copied! Ready to paste.')}catch(e){setStatus(e instanceof Error?e.message:'Clipboard failed. Use Finder.')}}
 async function metadata(){try{const value=await api<{text:string}>(`/attempts/${attemptId}/evaluation-context`);await navigator.clipboard.writeText(value.text);copied('Copied! Ready to paste.');setStatus('')}catch{setStatus('Could not copy details. Please retry.')}}
 async function reveal(){try{if(kind==='IMAGE'){const context=await api<{assets:{id:string;kind:string}[]}>(`/attempts/${attemptId}/evaluation-context`);const image=context.assets.find(a=>a.kind==='IMAGE');if(!image)throw Error('Image unavailable');await api('/reveal','POST',{kind:'IMAGE',id:image.id,attemptId})}else await api('/reveal','POST',{kind:'AUDIO',id:attemptId,attemptId:null});setStatus('Revealed in Finder')}catch{setStatus('Could not reveal file in Finder.')}}
 return <div className="attachment-controls"><button onClick={()=>void copy()}>{kind==='ALL'?'Copy everything for ChatGPT':kind==='IMAGE'?'Copy image':'Copy audio'}</button>{kind==='ALL'&&<button onClick={()=>void copy(true)} title="Use if ChatGPT pastes only text">Copy files + details</button>}{kind==='AUDIO'&&<button onClick={()=>void metadata()}>Copy details</button>}{kind!=='ALL'&&<><button title="Show in Finder" aria-label="Show in Finder" onClick={()=>void reveal()}>↗</button></>}<span role="status">{status}</span></div>
}
