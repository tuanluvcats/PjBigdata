# LedgerMind - Personal Finance Management System

## 1. Project Overview
**LedgerMind** is a personal finance management system built to automate 
expense tracking. It combines a Java Spring Boot backend, a Flutter 
mobile app, and a Python-based OCR service to extract and categorize 
expense data from receipts automatically.

## 2. Tech Stack & Infrastructure
This project uses a **Monorepo** structure, with the backend, mobile 
app, and AI service developed and deployed as separate components.

### Core Backend
- **Framework:** Spring Boot 3.x (Spring Web, Spring Security, Spring Data JPA)
- **Language:** Java 21 (JDK 21)
- **Authentication:** OAuth2 login via Google (Facebook OAuth in progress), JWT-based session handling
- **Database:** PostgreSQL (hosted on Neon), accessed via Hibernate ORM
- **Optimization:** JVM memory tuning (`-XX:MaxRAMPercentage=75.0`), dedicated `prod` Spring profile

### AI Service
- **Purpose:** Automated Vietnamese receipt OCR and data extraction
- **Model:** Fine-tuned Donut (Document Understanding Transformer) model, 
  trained on 1,500 labeled receipts (1,300 from OCR2021 + 200 self-collected)
- **Pipeline:** Python/FastAPI service exposing the model via API; 
  extracted text is cleaned and normalized with regex-based post-processing 
  before being sent to the backend

### Mobile App (Frontend)
- **Framework:** Flutter (Dart)
- **Features:** Secure token storage, real-time API communication over HTTPS
- **Distribution:** APK released via GitHub Releases

### DevOps & Deployment
- **Containerization:** Docker & Docker Compose, multi-stage builds using `eclipse-temurin:21-jre-jammy` and `alpine` base images
- **Process Management:** `tini` as PID 1 for proper signal handling in containers
- **Security & Ops:** Non-root user execution (`spring:spring`), Spring Boot Actuator healthcheck endpoint, timezone synchronization (`Asia/Ho_Chi_Minh`)
- **Infrastructure:** Deployed on an Azure VPS behind an Nginx reverse proxy (SSL/TLS), served at `https://ledgermind.dev`

## 3. Key Features
- **OAuth2 + JWT Authentication:** Google login integrated at the backend, with JWT used for session/API security
- **Automated Receipt Processing:** Fine-tuned OCR model extracts and structures expense data from receipt images, reducing manual entry
- **Containerized Deployment:** Backend, AI service, and database run as separate Dockerized services, deployed behind Nginx with HTTPS
- **Resource-Aware Backend:** JVM memory limits and healthcheck endpoints configured to run reliably on a constrained VPS

## 4. System Architecture
**Data Flow:**
1. **Client Layer:** Flutter app sends authentication and financial data requests.
2. **Proxy Layer:** Nginx receives HTTPS traffic and forwards it to the backend.
3. **Core Layer:** Java Spring Boot backend handles business logic, validates OAuth2/JWT, and manages the PostgreSQL database.
4. **AI Layer:** Receipt images are forwarded to the Python OCR service; results are post-processed and returned to the backend for storage.

## 5. Getting Started

### Prerequisites
- [Docker](https://www.docker.com/get-started) & Docker Compose
- Java JDK 21 (for local backend development)
- Flutter SDK (for local mobile development)

### Quick Start
1. **Clone the repository:**
```bash
   git clone https://github.com/TriPhan15/donutpay-monorepo.git
   cd donutpay-monorepo
```
2. **Configure environment variables:**
   Create a `.env` file in the root directory with your Google OAuth 
   client ID, database credentials, and other required config.
3. **Deploy the stack:**
```bash
   docker-compose up -d --build
```
4. **Verify deployment:**
```bash
   curl -f http://localhost:8080/actuator/health
```
