import type { Mode } from './practice'
export type SpeakingSession = {sessionId:string;attemptId:string;taskNumber:number;mode:Mode;stage:string;status:string;timing:{module:string;preparationSeconds:number;answerSeconds:number};content:{prompt:string;alternatives?:string[]}}
export const speakingTasks = [1,2,6,7]
export const speakingNames:Record<number,string> = {1:'Giving Advice',2:'Talking about a Personal Experience',6:'Dealing with a Difficult Situation',7:'Expressing Opinions'}
export class Microphone {
  stream:MediaStream
  recorder:MediaRecorder
  context:AudioContext
  analyser:AnalyserNode
  private source:MediaStreamAudioSourceNode
  closed=false
  private generation=0
  mount() {
    const generation=++this.generation
    return ()=>queueMicrotask(()=>{if(generation===this.generation){if(this.recorder.state!=='inactive')this.recorder.stop();this.release()}})
  }
  listen(data:(event:BlobEvent)=>void,error:()=>void,stop:()=>void) {
    this.recorder.ondataavailable=data;this.recorder.onerror=error;this.recorder.onstop=stop
  }
  constructor(stream:MediaStream) {
    this.stream=stream
    this.recorder=new MediaRecorder(stream,{mimeType:'audio/webm;codecs=opus',audioBitsPerSecond:64000})
    this.context=new AudioContext()
    this.source=this.context.createMediaStreamSource(stream)
    this.analyser=this.context.createAnalyser();this.analyser.fftSize=256;this.source.connect(this.analyser)
  }
  async cue() {
    try {
      await this.context.resume()
      const oscillator=this.context.createOscillator(),gain=this.context.createGain()
      oscillator.frequency.value=660;gain.gain.value=.08;oscillator.connect(gain);gain.connect(this.context.destination)
      oscillator.start();oscillator.stop(this.context.currentTime+.12)
      return true
    }catch{return false}
  }
  release() {
    if(this.closed)return
    this.closed=true
    this.stream.getTracks().forEach(track=>track.stop());this.source.disconnect();void this.context.close().catch(()=>{})
  }
}
export async function prepareMicrophone() {
  if(!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder==='undefined' || !MediaRecorder.isTypeSupported('audio/webm;codecs=opus')) throw Error('WebM/Opus recording is unavailable. Use desktop Chrome and enable microphone access.')
  let stream:MediaStream|undefined
  try {
    stream=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:true,noiseSuppression:true},video:false})
    const mic=new Microphone(stream)
    if(!stream.getAudioTracks().some(track=>track.readyState==='live')){mic.release();throw Error('No active microphone.')}
    // Unlock audio while still in the confirmed user action; later transitions may be automatic.
    await mic.context.resume().catch(()=>{})
    return mic
  }catch {stream?.getTracks().forEach(track=>track.stop());throw Error('Microphone could not be opened. Allow microphone access in Chrome site settings, connect your microphone, then retry. The exercise is still hidden.')}
}
