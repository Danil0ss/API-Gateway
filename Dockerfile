# Этап сборки (Builder)
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
# Скачиваем зависимости (кэширование)
RUN mvn dependency:go-offline -B
COPY src ./src
# Собираем jar
RUN mvn clean package -DskipTests -B

# Этап запуска (Runtime)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Копируем собранный jar из предыдущего этапа
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=docker"]