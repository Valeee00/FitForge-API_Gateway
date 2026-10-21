#!/usr/bin/env bash
# Sobe o Gateway/BFF na porta 8080 (treinos-service em 8081 e progresso-service em 8082)
export TREINOS_SERVICE_URL=${TREINOS_SERVICE_URL:-http://localhost:8081}
export PROGRESSO_SERVICE_URL=${PROGRESSO_SERVICE_URL:-http://localhost:8082}
mvn spring-boot:run
