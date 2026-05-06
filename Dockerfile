# =====================================================================
# Етап 1: збірка проєкту через Maven
# =====================================================================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Кешуємо залежності окремим шаром
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Збираємо проєкт
COPY src ./src
RUN mvn clean package -DskipTests -B