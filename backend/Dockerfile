# Material de estudo. Imagem do backend do exercicio de triagem assistida.
#
# A imagem roda a fatia atual: dominio das Specs 001 e 003 com IA falsa e armazenamento
# em memoria. Nao ha banco, broker nem Keycloak porque o codigo ainda nao usa nenhum deles.
# Reiniciar o container apaga todos os dados.

# --- build ---------------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# Camada de dependencias: so e refeita quando o pom muda.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Os testes rodam no desenvolvimento e no CI (`./mvnw test`), nao na construcao da imagem.
RUN mvn -B -q -DskipTests package

# --- runtime -------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Usuario sem privilegios: o processo nao precisa de root.
RUN addgroup -S triagem && adduser -S -G triagem triagem

COPY --from=build /build/target/triagem-backend-*.jar /app/app.jar
USER triagem

EXPOSE 8080
ENV JAVA_OPTS=""

HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
  CMD wget -q -O - http://localhost:8080/api/v1/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
