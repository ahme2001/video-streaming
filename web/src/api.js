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

/** The three strategies this project compares: same video, three different endpoints. */
export const STRATEGIES = [
  {
    key: 'whole',
    label: 'Whole file',
    url: (id) => `${API}/${id}`,
    note: 'One response carrying the entire file. Ranges are not advertised, so the browser has little to seek with.',
  },
  {
    key: 'range',
    label: 'Chunk by chunk',
    url: (id) => `${API}/stream/${id}`,
    note: 'Range requests answered with 206 Partial Content, capped at 1 MB each. Seeking works, but the quality is fixed.',
  },
  {
    key: 'hls',
    label: 'HLS',
    url: (id) => `${API}/hls/${id}/master.m3u8`,
    note: 'A master playlist of 4-second segments at 360p and 240p. The player chooses the rendition and can switch mid-playback.',
  },
]
