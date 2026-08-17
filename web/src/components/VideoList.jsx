import StatusBadge from './StatusBadge.jsx'

export default function VideoList({ videos, selectedId, onSelect }) {
  if (videos.length === 0) {
    return <p className="muted">No videos yet. Upload one first.</p>
  }

  return (
    <ul className="video-list">
      {videos.map((video) => (
        <li key={video.videoId}>
          <button
            type="button"
            className={video.videoId === selectedId ? 'selected' : ''}
            onClick={() => onSelect(video)}
          >
            <span className="title">{video.description || video.title}</span>
            <StatusBadge status={video.status} />
            <span className="id">{video.videoId}</span>
          </button>
        </li>
      ))}
    </ul>
  )
}
