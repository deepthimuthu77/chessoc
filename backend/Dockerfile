FROM debian:bookworm-slim AS engine
ARG TARGETARCH
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /engine
RUN case "$TARGETARCH" in \
      amd64) platform=linux-x86-64-v2; all_hash=fa7f3188697ed2733fded4ad788f90976452b8c90540bd79122c0f5052e1b99d; validator_hash=be8f7b6523558f2b7190da0b025b47af18ec2943f1561e0ed4ce8e4bf9163ed6 ;; \
      arm64) platform=linux-arm64; all_hash=350efd423e61f5cf1dbdf3419981a2f1d000232002a7739ffa89a424ae56d95c; validator_hash=5b0c9a0bc0e0c9099199fd44760f24abce7e69661b2e5843bd6bc9154d8fc4a7 ;; \
      *) echo "Unsupported engine architecture: $TARGETARCH"; exit 1 ;; \
    esac \
    && curl -fL --retry 3 "https://github.com/fcmv/Vector-64/releases/download/v1.1.1/STK-Vector-64-$platform-all" -o vector64-all \
    && curl -fL --retry 3 "https://github.com/fcmv/Vector-64/releases/download/v1.1.1/STK-Vector-64-$platform-validator" -o vector64-validator \
    && printf '%s  vector64-all\n%s  vector64-validator\n' "$all_hash" "$validator_hash" | sha256sum -c - \
    && curl -fL --retry 3 https://github.com/fcmv/Vector-64/releases/download/v1.1.1/release-notices.zip -o release-notices.zip \
    && echo 'dc3844693a4d8becf2f6791d2ce6015e88b348c8d674430feff8c84c54e044dc  release-notices.zip' | sha256sum -c - \
    && chmod 755 vector64-all vector64-validator

FROM gradle:8.10.2-jdk17 AS build
WORKDIR /home/gradle/src
COPY --chown=gradle:gradle . .
RUN gradle --no-daemon clean installDist

FROM eclipse-temurin:17-jre
WORKDIR /opt/chessoc
COPY --from=build /home/gradle/src/build/install/chessoc-server ./
COPY --from=engine /engine /opt/vector64
EXPOSE 8080 8081
ENTRYPOINT ["/opt/chessoc/bin/chessoc-server"]
