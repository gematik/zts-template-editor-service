FROM gematik1/osadl-alpine-openjdk25-jre@sha256:b562d62fdbfc77fa00bfc98b5bde97cc6add0e0c32da351b3acb97ffef1adce3

# The STOPSIGNAL instruction sets the system call signal that will be sent to the container to exit
# SIGTERM = 15 - https://de.wikipedia.org/wiki/Signal_(Unix)
STOPSIGNAL SIGTERM

EXPOSE 8080

# Defining Healthcheck
HEALTHCHECK --interval=15s \
            --timeout=10s \
            --start-period=10s \
            --retries=3 \
            CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# Default USERID and GROUPID
ARG USERID=10000
ARG GROUPID=10000

# Run as User (not root)
USER $USERID:$USERID
COPY --chown=$USERID:$GROUPID ./build/libs/template-editor.jar /template-editor.jar

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=85.0", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=200", "-jar", "/template-editor.jar"]

# Git Args
ARG COMMIT_HASH
ARG VERSION

###########################
# Labels
###########################
LABEL de.gematik.vendor="gematik GmbH" \
      maintainer="zts@gematik.de" \
      de.gematik.app="ZTS template editor service" \
      de.gematik.git-repo-name="https://gitlab.prod.ccs.gematik.solutions/zts/services/template-editor-service" \
      de.gematik.commit-sha=$COMMIT_HASH \
      de.gematik.version=$VERSION