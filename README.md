# Video Streaming Techniques

A project that delivers the **same video three different ways** so the trade-offs between them can be seen side by side: a plain progressive download, Apple's **HLS**, and the ISO standard **MPEG-DASH**.

A Spring Boot backend accepts an upload, stores it in S3, and runs `ffmpeg` to package it into an adaptive-bitrate ladder for each protocol. A React frontend plays the result and lets you switch between the three strategies while watching.

---

## Table of contents

- [The three strategies](#the-three-strategies)
- [Architecture](#architecture)
- [Tech stack](#tech-stack)
- [Repository layout](#repository-layout)
- [How it works](#how-it-works)
- [Storage layout in S3](#storage-layout-in-s3)
- [API reference](#api-reference)
- [Configuration](#configuration)
- [Running locally](#running-locally)
- [Running with Docker](#running-with-docker)
- [Deployment](#deployment)
- [Operational notes and gotchas](#operational-notes-and-gotchas)
- [Known limitations and next steps](#known-limitations-and-next-steps)

---

## The three strategies

| | Whole file | HLS | DASH |
|---|---|---|---|
| **Endpoint** | `/api/v1/video/{id}` | `/api/v1/video/hls/{id}/master.m3u8` | `/api/v1/video/dash/{id}/manifest.mpd` |
| **Manifest** | none | `.m3u8` playlists | `.mpd` (XML) |
| **Segments** | none — one file | MPEG-TS (`.ts`) | fragmented MP4 (`.m4s`) |
| **Adaptive** | no — one fixed quality | yes, switches mid-playback | yes, switches mid-playback |
| **Seeking** | HTTP range requests (`206`) | by segment | by segment |
| **Played by** | the browser itself | `hls.js` (native on Safari) | `dash.js` (no browser plays DASH natively) |
| **Origin** | — | Apple | MPEG / ISO standard |

All three serve the **same underlying video**, so switching strategies in the player is a like-for-like comparison.

---

## Architecture

```
                    ┌──────────────┐
   upload  ───────► │  Spring Boot │ ──── original ────►  S3  videos/{uuid}.mp4
                    │   backend    │
                    └──────┬───────┘
                           │ queues two packaging jobs
                           ▼
                 ┌───────────────────────┐
                 │  single-thread worker │   (ffmpeg already uses every core,
                 │   HLS job → DASH job  │    so jobs run one at a time)
                 └─────────┬─────────────┘
                           │ downloads original, runs ffmpeg locally
                           ▼
                  hls-work/  (scratch)
                           │ segments uploaded, then deleted from disk
                           ▼
        S3  hls/{id}/...            S3  dash/{id}/...
                           │
                           ▼
                    ┌──────────────┐
   player  ◄─────── │  backend     │  ◄── reads objects back from S3
                    │  serves      │      (bucket stays private)
                    └──────────────┘
```

The bucket is **private**. Every playlist, manifest and segment is proxied through the API rather than exposed directly, so the frontend needs no S3 credentials and no CORS configuration.

---

## Tech stack

**Backend**
- Java 21, Spring Boot 4.1.0 (Spring Web MVC, Spring Data JPA)
- MySQL (Clever Cloud in production)
- AWS SDK for Java v2 — S3
- `ffmpeg` — invoked as an external process
- MapStruct (DTO mapping), Lombok, Apache Tika (magic-number file type detection)

**Frontend**
- React 19, Vite 8, React Router 7
- `hls.js` 1.7 — HLS playback
- `dash.js` 5.2 — DASH playback

**Infrastructure**
- Docker (multi-stage build, Alpine runtime with `ffmpeg`)
- Amazon ECR — image registry
- Amazon ECS Express Mode — Fargate service with an auto-provisioned ALB and HTTPS domain
- Amazon S3 — all video bytes
- Clever Cloud — managed MySQL

---

## Repository layout

```
video-streaming/
├── video/                       # Spring Boot backend
│   ├── Dockerfile
│   ├── docker-compose.yml
│   ├── .dockerignore
│   ├── env.properties           # secrets, git-ignored
│   └── src/main/java/com/stream/video/
│       ├── config/              # AwsProperties, S3Config, HlsProperties,
│       │                        # HlsExecutorConfig, HlsStartupCheck
│       ├── controller/          # VideoController, VideoStreamingController,
│       │                        # HlsStreamingController, DashStreamingController, health
│       ├── dto/                 # VideoResponseDTO, ResourceResponseDTO
│       ├── exception/           # GlobalExceptionHandler + typed exceptions
│       ├── mapper/              # VideoMapper (MapStruct)
│       ├── model/               # Video entity, PackagingStatus enum
│       ├── repository/          # VideoRepository
│       └── service/
│           ├── ffmpeg/          # FfmpegRunner — process execution with timeout
│           ├── storage/         # ObjectStorage + S3 implementation
│           ├── video/           # upload / lookup
│           ├── hls/             # HLS packaging + streaming
│           └── dash/            # DASH packaging + streaming
└── web/                         # React frontend
    └── src/
        ├── api.js               # endpoints + the three strategy definitions
        ├── components/          # PlayerPanel, VideoList, StatusBadge
        └── pages/               # UploadPage, WatchPage
```

Services follow a **feature-package** convention: an interface in `service/<feature>/` and its implementation in `service/<feature>/impl/`.

---

## How it works

### 1. Upload

`POST /api/v1/video` with a multipart `video` field.

1. **Apache Tika** detects the content type from the file's magic numbers — the filename and its extension are deliberately ignored, so renaming a file cannot smuggle it past validation. Only MP4, QuickTime, WebM and Matroska are accepted.
2. The file is streamed straight to S3 as `videos/{uuid}.{ext}` — it never lands on the application's disk.
3. A `Video` row is saved with `status = PENDING` and `dashStatus = PENDING`.
4. Two packaging jobs are queued, and the response returns immediately.

### 2. Packaging

Both jobs run on a **single-threaded executor**. `ffmpeg` already spreads one encode across every core, so running two at once would not finish them any sooner — it would just make them compete.

Each job downloads the original to a scratch directory first. `ffmpeg` seeks all over its input, which it cannot do over a stream, so this one file has to be local.

**HLS** produces a master playlist, one media playlist per rendition, and MPEG-TS segments:

- Both renditions are encoded from a single decode of the source.
- `-force_key_frames` puts keyframes at identical timestamps in every rendition, which is what lets a player switch quality at a segment boundary.
- `-hls_flags temp_file` makes ffmpeg write `segment.ts.tmp` and rename it only once the segment is complete. That rename is the completion signal: a background uploader sweeps every finished `.ts` to S3 and deletes it locally **while the encode is still running**, so the disk only ever holds the segment being written plus whatever is queued for upload.
- The playlists are written as ffmpeg exits and uploaded in a final pass.

**DASH** produces an MPD manifest and fragmented-MP4 segments. Two structural differences from HLS:

- The output is **flat** — ffmpeg numbers representations instead of naming directories, so a 2-rendition ladder becomes `init-stream0..3.m4s` and `chunk-stream{N}-{00001..}.m4s` (streams 0 and 2 are video, 1 and 3 are their audio). `-adaptation_sets` puts the video representations in one set and audio in another, so the player switches video quality without re-picking an audio track.
- The DASH muxer has **no `temp_file` equivalent**, so there is no way to tell a finished segment from one still being written. The whole package is therefore uploaded *after* ffmpeg exits — the only moment every file is known to be complete.

Each protocol updates its own status column, so one being unavailable says nothing about the other. A video becomes playable for a protocol only when that protocol's status reaches `READY`.

### 3. Playback

The API reads objects back from S3 on demand. Playlists, manifests and segments are small, so they are read whole; the original file is streamed lazily so a large video never sits in heap. Because the manifests use **relative** URIs and the API mirrors the same directory shape, no manifest rewriting is needed.

---

## Storage layout in S3

```
s3://<bucket>/
├── videos/
│   └── {uuid}.mp4                          # original upload, kept for re-packaging
├── hls/
│   └── {videoId}/
│       ├── master.m3u8
│       ├── 360p/index.m3u8 + segment_00000.ts …
│       └── 240p/index.m3u8 + segment_00000.ts …
└── dash/
    └── {videoId}/
        ├── manifest.mpd
        ├── init-stream0.m4s … init-stream3.m4s
        └── chunk-stream0-00001.m4s …
```

The original is deliberately **kept** after packaging, so a video can be re-packaged later (a new rendition ladder, or retrying a failed job) without re-uploading.

---

## API reference

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/video?description=…` | Upload (multipart field `video`). Returns immediately with `PENDING`. |
| `GET` | `/api/v1/video` | List all videos |
| `GET` | `/api/v1/video/id/{id}` | One video, including both packaging statuses |
| `GET` | `/api/v1/video/title/{title}` | Look up by stored title |
| `GET` | `/api/v1/video/{id}` | **Whole file** — the original, supports range requests |
| `GET` | `/api/v1/video/hls/{id}/master.m3u8` | HLS master playlist |
| `GET` | `/api/v1/video/hls/{id}/{rendition}/index.m3u8` | HLS media playlist |
| `GET` | `/api/v1/video/hls/{id}/{rendition}/{segment}` | HLS segment (`.ts`) |
| `GET` | `/api/v1/video/dash/{id}/manifest.mpd` | DASH manifest |
| `GET` | `/api/v1/video/dash/{id}/{segment}` | DASH init or media segment (`.m4s`) |
| `GET` | `/health` | Liveness check — used as the load balancer's health check path |

**Status values** (`status` for HLS, `dashStatus` for DASH): `PENDING` → `PROCESSING` → `READY` or `FAILED`. A failure message is stored in `hlsError` / `dashError`.

Requesting a protocol that is not `READY` returns **409 Conflict** with a `packagingStatus` field, and re-queues the packaging job. Unknown ids return **404**, unsatisfiable ranges **416**.

Rendition names and segment filenames are validated against the configured ladder and a strict pattern, so a path variable can never reach outside the video's own prefix.

---

## Configuration

Secrets live in `video/env.properties`, which is **git-ignored** and never baked into the Docker image. Outside a container the file supplies these values; inside one they come from the environment instead (`spring.config.import` is marked `optional:`, so a missing file is not an error).

| Variable | Meaning |
|---|---|
| `DATABASE_URL` | JDBC URL, e.g. `jdbc:mysql://<host>:3306/<db>` |
| `DATABASE_USER` / `DATABASE_PASSWORD` | Database credentials |
| `AWS_REGION` | Region the bucket lives in — a mismatch fails startup |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_KEY` | Credentials for the S3 bucket |
| `AWS_S3_BUCKET` | Bucket holding originals and packaged output |
| `CDN_BASE_URL` | CloudFront distribution (reserved; not currently used) |

Copy `env-example.properties` to `env.properties` and fill it in.

Notable `application.yaml` settings:

| Setting | Value | Why |
|---|---|---|
| `server.port` | `8081` | |
| `spring.jpa.hibernate.ddl-auto` | `update` | Schema is created/updated from the entity |
| `spring.datasource.hikari.maximum-pool-size` | `3` | The managed database caps this account at **5** connections; Hikari's default pool of 10 would exhaust it |
| `spring.servlet.multipart.max-file-size` | `1000MB` | Upload ceiling |
| `hls.work-dir` | `hls-work/` | Scratch space; emptied as packaging proceeds |
| `hls.segment-duration-seconds` | `10` | Target segment length for both protocols |
| `hls.renditions` | 360p @ 800k, 240p @ 400k | The ladder; the sample source is 360p, so nothing higher would add detail |
| `hls.processing-timeout` | `30m` | ffmpeg is killed past this |

---

## Running locally

**Prerequisites:** Java 21, `ffmpeg` on `PATH`, and a MySQL database.

```bash
ffmpeg -version          # must succeed; the app refuses to start without it
```

**Backend**

```bash
cd video
cp env-example.properties env.properties   # then fill in the values
./mvnw spring-boot:run                     # http://localhost:8081
```

At startup the app verifies its environment and fails fast with a clear message if the bucket is unreachable or `ffmpeg` is missing.

**Frontend**

```bash
cd web
npm install
npm run dev                                # http://localhost:5173
```

Vite proxies `/api` to the backend, so the browser sees a single origin. Point `server.proxy` in `web/vite.config.js` at `http://localhost:8081` for local work, or at the deployed URL to use the hosted backend.

**A throwaway database** is handy when the hosted one is busy:

```bash
docker run -d --name test-mysql -e MYSQL_ROOT_PASSWORD=testpw \
  -e MYSQL_DATABASE=videodb -p 3307:3306 mysql:8

cd video && ./mvnw spring-boot:run -Dspring-boot.run.arguments="\
  --spring.datasource.url=jdbc:mysql://127.0.0.1:3307/videodb \
  --spring.datasource.username=root --spring.datasource.password=testpw"
```

---

## Running with Docker

The image is a **multi-stage build**: Maven and a JDK compile the jar, and only a JRE plus `ffmpeg` ship in the runtime layer. It runs as an unprivileged user and is roughly 479 MB, most of which is `ffmpeg`.

```bash
cd video
docker build -t video-streaming:latest .
docker run -d --name video-app -p 8081:8081 --env-file env.properties video-streaming:latest
```

Or with Compose:

```bash
docker-compose up -d      # builds, then runs with env.properties as env_file
docker-compose down
```

Secrets are passed at **run time**, never built into the image — `env.properties` is listed in `.dockerignore` so it cannot leak into a layer.

---

## Deployment

The backend runs on **Amazon ECS (Express Mode)** from an image in **Amazon ECR**, with the database on **Clever Cloud**.

> Replace `<account-id>`, `<region>`, `<bucket>` and the hostnames below with your own values. They are kept out of this file on purpose.

### Step 1 — Database on Clever Cloud

1. Create a Clever Cloud account and choose **Create → an add-on → MySQL**.
2. Pick a plan and a region, and name the add-on.
3. Open the add-on's **Information** tab, which lists the generated host, database name, user and password.
4. Build the JDBC URL from those values:
   ```
   DATABASE_URL=jdbc:mysql://<host>.services.clever-cloud.com:3306/<database>
   DATABASE_USER=<user>
   DATABASE_PASSWORD=<password>
   ```
5. No schema work is needed — `ddl-auto: update` creates and updates the `video` table on first start.

> **Check the plan's connection limit.** The free/entry MySQL plan allows only **5 concurrent connections** for the account, which is why `maximum-pool-size` is pinned to 3. Leaving headroom means a SQL client, a second instance, or a redeploy overlapping the old task cannot lock the application out.

### Step 2 — S3 bucket

1. Create a bucket in the region you will run in, and keep **Block Public Access enabled** — the application proxies every byte, so nothing needs to be public.
2. Create an IAM user with programmatic access, limited to that bucket: `s3:GetObject`, `s3:PutObject`, `s3:DeleteObject`, `s3:ListBucket`.
3. Put the key, secret, region and bucket name into `env.properties`.

### Step 3 — Push the image to ECR

```bash
# 1. create the repository (once)
aws ecr create-repository --repository-name video-streaming --region <region>

# 2. authenticate Docker against the registry
aws ecr get-login-password --region <region> \
  | docker login --username AWS --password-stdin <account-id>.dkr.ecr.<region>.amazonaws.com

# 3. build. --platform matters: Fargate runs x86_64, so an image built on an
#    Apple-silicon or other ARM machine will not start without this flag.
cd video
docker build --platform linux/amd64 -t video-streaming:latest .

# 4. tag and push
docker tag video-streaming:latest <account-id>.dkr.ecr.<region>.amazonaws.com/video-streaming:latest
docker push <account-id>.dkr.ecr.<region>.amazonaws.com/video-streaming:latest
```

### Step 4 — Run it on ECS Express Mode

Express Mode provisions the cluster, the Fargate service, an Application Load Balancer, an HTTPS certificate and a public `*.ecs.<region>.on.aws` domain in one flow — no VPC, target group or listener wiring by hand.

In the ECS console, **Create → Express service**, then:

1. **Container image** — the ECR URI pushed above, e.g.
   `<account-id>.dkr.ecr.<region>.amazonaws.com/video-streaming:latest`
2. **Container port** — `8081`, matching `server.port` and the Dockerfile's `EXPOSE`.
3. **Health check path** — `/health`. This is what the `health` controller exists for; the load balancer marks the task unhealthy and recycles it if this does not return 200.
4. **Environment variables** — added as plain key/value pairs on the task definition:

   | Key | Value |
   |---|---|
   | `DATABASE_URL` | the Clever Cloud JDBC URL |
   | `DATABASE_USER` | Clever Cloud user |
   | `DATABASE_PASSWORD` | Clever Cloud password |
   | `AWS_REGION` | bucket region |
   | `AWS_ACCESS_KEY_ID` | IAM key |
   | `AWS_SECRET_KEY` | IAM secret |
   | `AWS_S3_BUCKET` | bucket name |

5. **CPU / memory** — give it room: `ffmpeg` is the workload, and a starved task makes packaging crawl or time out.
6. Create the service. Express Mode returns a URL of the form
   `https://<name>-<hash>.ecs.<region>.on.aws`.

Verify:

```bash
curl https://<name>-<hash>.ecs.<region>.on.aws/health          # -> hello world
curl https://<name>-<hash>.ecs.<region>.on.aws/api/v1/video    # -> []
```

**Redeploying** is push a new image tag, then force a new deployment on the service so it pulls the updated image.

### Step 5 — Point the frontend at it

In `web/vite.config.js`:

```js
proxy: { '/api': 'https://<name>-<hash>.ecs.<region>.on.aws/' }
```

Then `npm run build` and host `web/dist/` anywhere static (S3 + CloudFront, Netlify, Vercel…). Because the proxy only applies to the Vite dev server, a static deployment needs the API on the same origin or CORS enabled on the backend.

---

## Operational notes and gotchas

Things that cost real debugging time on this project:

- **`ffmpeg` must exist in the runtime image.** The app shells out to it and refuses to boot without it. The Alpine runtime installs it explicitly (`apk add --no-cache ffmpeg`) — a plain JRE image will start and then fail every packaging job.
- **Alpine already has a system group called `video`**, so the container's unprivileged user is named `app` instead.
- **Database connection limits bite before you notice them.** Hikari's default pool (10) is larger than the managed plan's entire allowance (5). A redeploy where the old task overlaps the new one doubles demand again.
- **A 503 from the load balancer usually means no healthy task** — check the container logs before suspecting the network. If the database is unreachable at startup, the task never becomes healthy.
- **The bucket region must match `AWS_REGION`.** A wrong region shows up as a `400 Bad Request` on `HeadBucket`; wrong credentials or a bucket you do not own show up as `403`. Bucket names are globally unique, so a plausible short name is very likely already someone else's.
- **`ffmpeg`'s exit code is not always the truth.** When its HLS muxer writes output over HTTP it reports a failed upload as a warning and still exits `0`. Writing to local files — what this project does — makes the exit code trustworthy again.
- **Two encodes per upload.** HLS and DASH are packaged independently from the source, so a video is encoded twice and packaging takes roughly twice as long.

---

## Known limitations and next steps

- **Single encode for both protocols (CMAF).** Encoding fMP4 segments once and generating *both* an MPD and HLS playlists over the same segments would halve CPU and storage. It is what production systems do; the cost is that HLS stops using classic MPEG-TS.
- **Uploads use a single `PutObject`.** Fine up to 5 GB, but there is no resume. `S3TransferManager` with multipart would fix that.
- **DASH holds its whole package on disk** until ffmpeg exits, because the muxer gives no completion signal. Uploading segment *N* once segment *N+1* appears would bound it.
- **No CDN in the path.** `CDN_BASE_URL` points at a CloudFront distribution that is not used yet — every byte is proxied through the application. Serving segments from CloudFront (with an origin access control onto the private bucket) would cut both latency and egress cost.
- **No authentication.** Every endpoint is public; anyone with the URL can upload.
- **The frontend bundle is ~1.6 MB** because `hls.js` and `dash.js` are both bundled eagerly. Loading each player only when its strategy is selected would cut the initial download substantially.
- **No automated tests.** The only test is a context load, which needs a live database and bucket to pass.
