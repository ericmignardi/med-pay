FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline

COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q clean package -DskipTests

RUN java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

RUN addgroup -g 10001 -S medpay \
    && adduser -u 10001 -S medpay -G medpay \
    && chown medpay:medpay /app
USER 10001:10001

COPY --from=build --chown=medpay:medpay /build/extracted/dependencies/ ./
COPY --from=build --chown=medpay:medpay /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=medpay:medpay /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=medpay:medpay /build/extracted/application/ ./

EXPOSE 8080

ENV TZ=UTC \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
