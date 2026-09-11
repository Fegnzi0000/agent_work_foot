FROM maven:3-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 --create-home appuser
COPY deploy/certs/tencent-root-ca.crt /tmp/tencent-root-ca.crt
COPY deploy/certs/tencentdb-crs-ca.crt /tmp/tencentdb-crs-ca.crt
RUN keytool -importcert -noprompt -trustcacerts -alias tencent-root-ca \
        -file /tmp/tencent-root-ca.crt -cacerts -storepass changeit \
    && keytool -importcert -noprompt -trustcacerts -alias tencentdb-crs-ca \
        -file /tmp/tencentdb-crs-ca.crt -cacerts -storepass changeit \
    && rm -f /tmp/tencent-root-ca.crt /tmp/tencentdb-crs-ca.crt
WORKDIR /app
COPY --from=build --chown=appuser:appuser /workspace/target/agent_work_foot-*.jar /app/application.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Dfile.encoding=UTF-8", "-jar", "/app/application.jar"]
