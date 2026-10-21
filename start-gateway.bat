@echo off
REM Sobe o Gateway/BFF na porta 8080 (treinos-service em 8081 e progresso-service em 8082)
set TREINOS_SERVICE_URL=http://localhost:8081
set PROGRESSO_SERVICE_URL=http://localhost:8082
mvn spring-boot:run
