import { useCallback, useEffect, useState } from 'react'
import { listVideos } from '../api.js'
import PlayerPanel from '../components/PlayerPanel.jsx'
import VideoList from '../components/VideoList.jsx'

export default function WatchPage() {
  const [videos, setVideos] = useState([])
  const [selected, setSelected] = useState(null)
  const [error, setError] = useState(null)

  const refresh = useCallback(async () => {
    try {
      const loaded = await listVideos()
      setVideos(loaded)
      setError(null)
      // Keep the selection pointing at the freshly loaded row, so a video that finished
      // packaging enables its HLS button without needing to be reselected.
      setSelected((current) =>
        current ? loaded.find((video) => video.videoId === current.videoId) ?? current : null,
      )
    } catch (e) {
      setError(e.message)
    }
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  return (
    <section className="watch">
      <div className="panel half">
        <div className="panel-head">
          <h2>Videos</h2>
          <button type="button" onClick={refresh}>Refresh</button>
        </div>
        {error && <p className="error">{error}</p>}
        <VideoList videos={videos} selectedId={selected?.videoId} onSelect={setSelected} />
      </div>

      <div className="panel half">
        <h2>Playback</h2>
        <PlayerPanel video={selected} />
      </div>
    </section>
  )
}
