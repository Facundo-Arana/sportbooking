# Build: compila el aggregator completo con el wrapper y Java 17 (misma version que java.version).
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# El contexto de build es la raiz del repo: el aggregator necesita todos los modulos.
COPY . .

# El wrapper descarga Maven; .gitattributes fuerza LF en mvnw.
RUN chmod +x mvnw && ./mvnw -B -DskipTests package

# Runtime: solo el jar ejecutable de app, con un usuario sin privilegios.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=build /workspace/app/target/app-*.jar /app/app.jar

USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
