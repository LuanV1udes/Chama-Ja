# Build em duas etapas: compila com Maven e roda só com o JRE.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/chamaja-*.jar app.jar

# Perfil de produção por padrão: o schema vem das migrações (Flyway), nunca do Hibernate.
ENV SPRING_PROFILES_ACTIVE=prod
# Horário de Brasília: validade das promoções e rotina diária usam o relógio do servidor.
ENV TZ=America/Sao_Paulo
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=America/Sao_Paulo"

# A Railway define a variável PORT; a aplicação a lê (server.port=${PORT:8080}).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
