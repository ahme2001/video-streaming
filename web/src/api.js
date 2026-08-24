const API = '/api/v1/video'

async function parse(response) {
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw new Error(body.message || `Request failed with ${response.status}`)
  }
  return response.json()
}

export function listVideos() {
  return fetch(API).then(parse)
}

export function getVideo(id) {
  return fetch(`${API}/id/${id}`).then(parse)
}

export function uploadVideo(file, description) {
  const form = new FormData()
  form.append('video', file)
  const query = description ? `?description=${encodeURIComponent(description)}` : ''
  return fetch(`${API}${query}`, { method: 'POST', body: form }).then(parse)
}

/**
 * The three strategies this project compares: same video, three different endpoints.
 * `ready` says whether a strategy can be played yet, which for the packaged ones depends
 * on ffmpeg having finished with that protocol.
 */
export const STRATEGIES = [
  {
    key: 'whole',
    label: 'Whole file',
    url: (id) => `${API}/${id}`,
    ready: () => true,
    note: 'The original upload, streamed straight from S3. Range requests are answered with 206 Partial Content, so seeking works — but there is only one quality, fixed when it was uploaded.',
  },
  {
    key: 'hls',
    label: 'HLS',
    url: (id) => `${API}/hls/${id}/master.m3u8`,
    ready: (video) => video?.status === 'READY',
    note: 'A master playlist of 4-second MPEG-TS segments at 360p and 240p, each fetched from S3. The player chooses the rendition and can switch mid-playback. Apple\'s format, played here by hls.js.',
  },
  {
    key: 'dash',
    label: 'DASH',
    url: (id) => `${API}/dash/${id}/manifest.mpd`,
    ready: (video) => video?.dashStatus === 'READY',
    note: 'The same ladder described by an MPD manifest instead of a playlist, with fragmented-mp4 segments. The open ISO standard for adaptive streaming, played here by dash.js.',
  },
]
