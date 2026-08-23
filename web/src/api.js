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

/** The two strategies this project compares: same video, two different endpoints. */
export const STRATEGIES = [
  {
    key: 'whole',
    label: 'Whole file',
    url: (id) => `${API}/${id}`,
    note: 'The original upload, streamed straight from S3. Range requests are answered with 206 Partial Content, so seeking works — but there is only one quality, fixed when it was uploaded.',
  },
  {
    key: 'hls',
    label: 'HLS',
    url: (id) => `${API}/hls/${id}/master.m3u8`,
    note: 'A master playlist of 4-second segments at 360p and 240p, each segment fetched from S3. The player chooses the rendition and can switch mid-playback.',
  },
]
