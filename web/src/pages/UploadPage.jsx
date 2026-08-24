import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { getVideo, uploadVideo } from '../api.js'
import StatusBadge from '../components/StatusBadge.jsx'

const TERMINAL = ['READY', 'FAILED']

// Two independent ffmpeg jobs, so the upload is only finished once both have settled.
const settled = (video) => TERMINAL.includes(video.status) && TERMINAL.includes(video.dashStatus)

export default function UploadPage() {
  const [file, setFile] = useState(null)
  const [description, setDescription] = useState('')
  const [uploading, setUploading] = useState(false)
  const [uploaded, setUploaded] = useState(null)
  const [error, setError] = useState(null)
  const fileInput = useRef(null)

  // Packaging happens after the response, so the upload is only half the story: poll until
  // ffmpeg has either produced the playlists or given up.
  useEffect(() => {
    if (!uploaded || settled(uploaded)) return

    const timer = setInterval(async () => {
      try {
        setUploaded(await getVideo(uploaded.videoId))
      } catch (e) {
        setError(e.message)
        clearInterval(timer)
      }
    }, 4000)
    return () => clearInterval(timer)
  }, [uploaded])

  async function submit(event) {
    event.preventDefault()
    if (!file) return

    setUploading(true)
    setError(null)
    setUploaded(null)
    try {
      setUploaded(await uploadVideo(file, description))
      setFile(null)
      setDescription('')
      if (fileInput.current) fileInput.current.value = ''
    } catch (e) {
      setError(e.message)
    } finally {
      setUploading(false)
    }
  }

  return (
    <section className="panel">
      <h2>Upload a video</h2>

      <form onSubmit={submit} className="upload-form">
        <label>
          Video file
          <input
            ref={fileInput}
            type="file"
            accept="video/*"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />
        </label>

        <label>
          Description
          <input
            type="text"
            value={description}
            placeholder="optional"
            onChange={(e) => setDescription(e.target.value)}
          />
        </label>

        <button type="submit" disabled={!file || uploading}>
          {uploading ? 'Uploading…' : 'Upload'}
        </button>
      </form>

      {error && <p className="error">{error}</p>}

      {uploaded && (
        <div className="result">
          <h3>{uploaded.title}</h3>
          <dl>
            <dt>Id</dt>
            <dd><code>{uploaded.videoId}</code></dd>
            <dt>Type</dt>
            <dd>{uploaded.contentType}</dd>
            <dt>HLS</dt>
            <dd><StatusBadge status={uploaded.status} /></dd>
            <dt>DASH</dt>
            <dd><StatusBadge status={uploaded.dashStatus} /></dd>
          </dl>

          {uploaded.status === 'READY' || uploaded.dashStatus === 'READY' ? (
            <p>Packaged into adaptive renditions. <Link to="/watch">Watch it</Link>.</p>
          ) : settled(uploaded) ? (
            <p className="error">
              Packaging failed, so there are no renditions to choose from. The original file still plays.
            </p>
          ) : (
            <p className="muted">
              The upload finished immediately; ffmpeg is packaging it in the background, once
              per protocol. Watching these statuses is the whole reason adaptive streaming
              needs them.
            </p>
          )}
        </div>
      )}
    </section>
  )
}
