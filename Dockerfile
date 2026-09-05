FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S agentic && adduser -S agentic -G agentic

WORKDIR /app
COPY target/agentic-sdlc-platform-0.1.0-SNAPSHOT.jar /app/app.jar

RUN mkdir -p /app/workspaces /tmp/agentic && chown -R agentic:agentic /app /tmp/agentic

USER agentic

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
