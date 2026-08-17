FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

# Extrai o layered jar do Spring Boot em camadas separadas (dependências,
# loader, snapshots e código da aplicação) para melhor cache de build.
# "-Djarmode=tools" substitui o antigo "layertools", deprecado no Boot 3.4.
RUN java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuário/grupo dedicados sem privilégios para executar a aplicação.
RUN addgroup -S app && adduser -S app -G app

# Camadas na ordem do que muda menos (dependencies) para o que muda mais
# (application): rebuilds sem mudança de dependências reaproveitam o cache.
COPY --from=build /app/extracted/dependencies/ ./
COPY --from=build /app/extracted/spring-boot-loader/ ./
COPY --from=build /app/extracted/snapshot-dependencies/ ./
COPY --from=build /app/extracted/application/ ./

USER app

EXPOSE 8080

# Limita a heap ao percentual da memória do container; sobrescreva via JAVA_OPTS.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# wget do busybox (presente na imagem alpine); --spider falha em respostas não-2xx.
# Usa o liveness probe (apenas estado do processo): queda de dependência externa
# (banco/SMTP) não marca o container como unhealthy nem o deixa preso em "starting".
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q --spider "http://localhost:${SERVER_PORT:-8080}/actuator/health/liveness"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
