# syntax=docker/dockerfile:1

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies resolve in their own layer, so editing source does not re-download them.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# The only test is a context load, which would need a database and a bucket to pass.
RUN mvn -B -q clean package -DskipTests

# ---------- run ----------
FROM eclipse-temurin:21-jre-alpine

# HlsPackager shells out to ffmpeg, and HlsStartupCheck refuses to boot without both
# binaries on PATH, so the runtime image needs them just as much as a dev machine does.
RUN apk add --no-cache ffmpeg

# Nothing here needs root, and this process feeds uploaded media to ffmpeg.
# Not named "video": Alpine already ships a system group by that name.
RUN addgroup -S app && adduser -S app -G app

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

# Scratch space for the downloaded source and for each segment between ffmpeg finishing
# it and it reaching S3. Everything else lives in the bucket.
RUN mkdir -p /app/hls-work && chown -R app:app /app
USER app

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
