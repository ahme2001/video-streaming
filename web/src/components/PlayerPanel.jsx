import { useEffect, useRef, useState } from 'react'
import Hls from 'hls.js'
import { STRATEGIES } from '../api.js'

export default function PlayerPanel({ video }) {
  const videoRef = useRef(null)
  const [strategyKey, setStrategyKey] = useState('whole')
  const [levels, setLevels] = useState([])
  const [currentLevel, setCurrentLevel] = useState(null)
  const [error, setError] = useState(null)

  const strategy = STRATEGIES.find((item) => item.key === strategyKey)
  const videoId = video?.videoId
  const status = video?.status
  const hlsReady = status === 'READY'

  useEffect(() => {
    const element = videoRef.current
    if (!element || !videoId) return undefined

    setError(null)
    setLevels([])
    setCurrentLevel(null)

    const source = strategy.url(videoId)
    const detach = () => {
      element.removeAttribute('src')
      element.load()
    }

    // The first two strategies are plain URLs: the browser is the client, and the only
    // difference between them is how the server answers.
    if (strategyKey !== 'hls') {
      element.src = source
      return detach
    }

    if (!hlsReady) {
      setError(`HLS is not available yet — this video is ${status ?? 'not packaged'}.`)
      return undefined
    }

    // Safari plays HLS natively. Chrome and Firefox do not, so hls.js reads the playlists
    // and feeds segments to the element itself.
    if (element.canPlayType('application/vnd.apple.mpegurl')) {
      element.src = source
      return detach
    }

    const hls = new Hls()
    hls.loadSource(source)
    hls.attachMedia(element)
    hls.on(Hls.Events.MANIFEST_PARSED, (_event, data) => setLevels(data.levels))
    hls.on(Hls.Events.LEVEL_SWITCHED, (_event, data) => setCurrentLevel(data.level))
    hls.on(Hls.Events.ERROR, (_event, data) => {
      if (data.fatal) setError(`${data.type}: ${data.details}`)
    })
    return () => hls.destroy()
  }, [videoId, status, strategyKey, hlsReady, strategy])

  if (!video) {
    return <p className="muted">Pick a video from the list.</p>
  }

  return (
    <div className="player">
      <div className="strategy-buttons">
        {STRATEGIES.map((item) => (
          <button
            key={item.key}
            type="button"
            className={item.key === strategyKey ? 'active' : ''}
            disabled={item.key === 'hls' && !hlsReady}
            title={item.key === 'hls' && !hlsReady ? `HLS status: ${status ?? 'NONE'}` : undefined}
            onClick={() => setStrategyKey(item.key)}
          >
            {item.label}
          </button>
        ))}
      </div>

      <p className="note">{strategy.note}</p>

      <video ref={videoRef} controls playsInline />

      <p className="source">
        <code>{strategy.url(videoId)}</code>
      </p>

      {error && <p className="error">{error}</p>}

      {strategyKey === 'hls' && levels.length > 0 && (
        <div className="levels">
          <strong>Renditions offered:</strong>
          <ul>
            {levels.map((level, index) => (
              <li key={level.url?.[0] ?? index} className={index === currentLevel ? 'active' : ''}>
                {level.height}p · {Math.round(level.bitrate / 1000)} kbps
                {index === currentLevel ? ' ← playing' : ''}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
