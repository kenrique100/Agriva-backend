FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Disable all date checks to bypass the "Release file is not valid yet" error
RUN apt-get -o Acquire::Check-Date=false update && \
    apt-get install -y --no-install-recommends wget && \
    rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 1001 appgroup && \
    useradd  --system --uid 1001 --gid appgroup --create-home --shell /usr/sbin/nologin appuser

COPY target/agriva.jar app.jar

# Create the uploads directory and hand the whole /app tree to appuser
# BEFORE switching user. Fixes the no-volume case outright.
RUN mkdir -p /app/uploads \
 && chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]