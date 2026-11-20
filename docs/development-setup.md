# DineFlow Development Setup Guide

## Prerequisites
- Java 21+ or Java 25 LTS
- Node.js 20+ and npm 10+
- Docker & Docker Compose (optional, for running local PostgreSQL container)

## 1. Clone & Configure
```bash
cp .env.example .env
```

## 2. Start Database (Optional Docker)
```bash
docker compose up -d postgres
```

## 3. Run Backend (Spring Boot)
```bash
cd backend
# Windows PowerShell:
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
# Linux / macOS:
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```
The backend starts on `http://localhost:8080`. Verify with:
```bash
curl http://localhost:8080/api/v1/health
```

## 4. Run Frontend (React + Vite)
```bash
cd frontend
npm install
npm run dev
```
The frontend starts on `http://localhost:5173`.