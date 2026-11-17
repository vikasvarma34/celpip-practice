import { useEffect, useEffectEvent, useState } from 'react'
import { clock } from './practice'

/** Absolute monotonic deadline; missed ticks never extend an attempt. */
export function usePracticeTimer(start: number, limit: number | null, active: boolean, onExpire: () => void, onSuspend: () => void) {
  const [now, setNow] = useState(start)
  const expire = useEffectEvent(onExpire)
  const suspend = useEffectEvent(onSuspend)
  useEffect(() => {
    if (!active) return
    let lastWall = Date.now(), lastMono = performance.now(), finished = false
    const update = () => {
      if (finished) return
      const mono = performance.now(), wall = Date.now()
      setNow(mono)
      // macOS may suspend performance.now during sleep. Never silently extend a timed attempt.
      if (Math.abs((wall - lastWall) - (mono - lastMono)) > 5000) { finished = true; suspend() }
      else if (clock(start, mono, limit).expired) { finished = true; expire() }
      lastMono = mono; lastWall = wall
    }
    const interval = window.setInterval(update, 250)
    document.addEventListener('visibilitychange', update)
    return () => { clearInterval(interval); document.removeEventListener('visibilitychange', update) }
  }, [start, limit, active])
  const value = clock(start, now, limit)
  return { ...value, display: limit === null ? Math.floor(value.display / 1000) * 1000 : value.display }
}
