import { useEffect, useState } from 'react'
export default function CopyToast(){
 const [message,setMessage]=useState('')
 useEffect(()=>{let timeout:number|undefined;const show=(event:Event)=>{setMessage((event as CustomEvent<string>).detail);clearTimeout(timeout);timeout=window.setTimeout(()=>setMessage(''),3000)};window.addEventListener('celpip-copy-success',show);return()=>{window.removeEventListener('celpip-copy-success',show);clearTimeout(timeout)}},[])
 return <div role="status" aria-live="polite" aria-atomic="true" className={message?'copy-toast':'copy-toast hidden'}>{message&&<>✓ {message}</>}</div>
}
