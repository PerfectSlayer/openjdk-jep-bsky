FROM eclipse-temurin:21

WORKDIR /app

COPY --chown=185 target/quarkus-app/lib/ /app/lib/
COPY --chown=185 target/quarkus-app/*.jar /app/
COPY --chown=185 target/quarkus-app/app/ /app/app/
COPY --chown=185 target/quarkus-app/quarkus/ /app/quarkus/

CMD ["java", "-jar", "quarkus-run.jar"]
