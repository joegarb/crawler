# Build the jar once on the native build platform; it's architecture-independent.
FROM --platform=$BUILDPLATFORM maven:3-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests -Dcheckstyle.skip clean package

# No official Java 25 image ships 32-bit ARM, so install a per-arch BellSoft
# Liberica JRE (which does) onto a Debian base that supports arm/v7.
FROM debian:bookworm-slim
ARG TARGETARCH
ARG TARGETVARIANT
ARG LIBERICA_VERSION=25.0.3+11
RUN set -eux; \
    case "$TARGETARCH/$TARGETVARIANT" in \
      amd64/*) jre=amd64 ;; \
      arm64/*) jre=aarch64 ;; \
      arm/v7)  jre=arm32-vfp-hflt ;; \
      *) echo "unsupported platform: $TARGETARCH/$TARGETVARIANT" >&2; exit 1 ;; \
    esac; \
    apt-get update; \
    apt-get install -y --no-install-recommends curl ca-certificates; \
    curl -fsSL "https://github.com/bell-sw/Liberica/releases/download/${LIBERICA_VERSION}/bellsoft-jre${LIBERICA_VERSION}-linux-${jre}.tar.gz" -o /tmp/jre.tar.gz; \
    mkdir -p /opt/java; \
    tar -xzf /tmp/jre.tar.gz -C /opt/java --strip-components=1; \
    rm /tmp/jre.tar.gz; \
    apt-get purge -y curl; apt-get autoremove -y; rm -rf /var/lib/apt/lists/*
ENV PATH="/opt/java/bin:$PATH"
WORKDIR /app
COPY --from=build /app/target/crawler-1.0.jar ./crawler.jar
ENTRYPOINT ["java", "-jar", "crawler.jar"]
