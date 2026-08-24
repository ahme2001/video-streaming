import { useEffect, useRef, useState } from 'react'
import Hls from 'hls.js'
import { MediaPlayer } from 'dashjs'
import { STRATEGIES } from '../api.js'

/** Normalised so HLS levels and DASH representations render through the same list. */
function toLevel(height, bitrate) {
  return { height, bitrate }
}

// Safari plays HLS natively. Chrome and Firefox do not, so hls.js reads the playlists and
// feeds segments to the element itself.
function attachHls(element, source, { setLevels, setCurrentLevel, setError }) {
  if (element.canPlayType('application/vnd.apple.mpegurl')) {
    element.src = source
    return () => {
      element.removeAttribute('src')
      element.load()
    }
  }

  const hls = new Hls()
  hls.loadSource(source)
  hls.attachMedia(element)
  hls.on(Hls.Events.MANIFEST_PARSED, (_event, data) =>
    setLevels(data.levels.map((level) => toLevel(level.height, level.bitrate))),
  )
  hls.on(Hls.Events.LEVEL_SWITCHED, (_event, data) => setCurrentLevel(data.level))
  hls.on(Hls.Events.ERROR, (_event, data) => {
    if (data.fatal) setError(`${data.type}: ${data.details}`)
  })
  return () => hls.destroy()
}

// No browser plays DASH natively, so dash.js does the whole job: it reads the MPD, picks a
// representation and appends segments through Media Source Extensions.
function attachDash(element, source, { setLevels, setCurrentLevel, setError }) {
  const player = MediaPlayer().create()
  player.initialize(element, source, false)

  player.on(MediaPlayer.events.STREAM_INITIALIZED, () => {
    const representations = player.getRepresentationsByType?.('video') ?? []
    setLevels(representations.map((rep) => toLevel(rep.height, rep.bandwidth ?? rep.bitrate)))
  })
  player.on(MediaPlayer.events.QUALITY_CHANGE_RENDERED, (event) => {
    if (event.mediaType !== 'video') return
    const index = event.newRepresentation?.index
    if (typeof index === 'number') setCurrentLevel(index)
  })
  player.on(MediaPlayer.events.ERROR, (event) =>
    setError(event.error?.message ?? event.error ?? 'DASH playback error'),
  )

  return () => player.destroy()
}

export default function PlayerPanel({ video }) {
  const videoRef = useRef(null)
  const [strategyKey, setStrategyKey] = useState('whole')
  const [levels, setLevels] = useState([])
  const [currentLevel, setCurrentLevel] = useState(null)
  const [error, setError] = useState(null)

  const strategy = STRATEGIES.find((item) => item.key === strategyKey)
  const videoId = video?.videoId
  // Each packaged strategy waits on its own ffmpeg job, so they become playable separately.
  const ready = strategy.ready(video)

  useEffect(() => {
    const element = videoRef.current
    if (!element || !videoId) return undefined

    setError(null)
    setLevels([])
    setCurrentLevel(null)

    if (!ready) {
      setError(`${strategy.label} is not available yet for this video.`)
      return undefined
    }

    const source = strategy.url(videoId)
    const handlers = { setLevels, setCurrentLevel, setError }

    if (strategyKey === 'hls') return attachHls(element, source, handlers)
    if (strategyKey === 'dash') return attachDash(element, source, handlers)

    // The whole-file strategy is a plain URL: the browser is the client, and the element
    // handles its own seeking with range requests.
    element.src = source
    return () => {
      element.removeAttribute('src')
      element.load()
    }
  }, [videoId, strategyKey, ready, strategy])

  if (!video) {
    return <p className="muted">Pick a video from the list.</p>
  }

  return (
    <div className="player">
      <div className="strategy-buttons">
        {STRATEGIES.map((item) => {
          const playable = item.ready(video)
          return (
            <button
              key={item.key}
              type="button"
              className={item.key === strategyKey ? 'active' : ''}
              disabled={!playable}
              title={playable ? undefined : `${item.label} is not packaged yet`}
              onClick={() => setStrategyKey(item.key)}
            >
              {item.label}
            </button>
          )
        })}
      </div>

      <p className="note">{strategy.note}</p>

      <video ref={videoRef} controls playsInline />

      <p className="source">
        <code>{strategy.url(videoId)}</code>
      </p>

      {error && <p className="error">{error}</p>}

      {levels.length > 0 && (
        <div className="levels">
          <strong>Renditions offered:</strong>
          <ul>
            {levels.map((level, index) => (
              <li key={`${level.height}-${level.bitrate}-${index}`} className={index === currentLevel ? 'active' : ''}>
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
