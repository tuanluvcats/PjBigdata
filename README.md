#  LedgerMind - Intelligent Personal Finance Management

##  1. Project Pitch
**LedgerMind** is a comprehensive, automated personal finance management system designed to eliminate the friction of manual expense tracking. By seamlessly integrating a robust Java backend, an intuitive Flutter mobile application, and Python-driven Data Mining capabilities, LedgerMind empowers users to digitize their financial data, gain actionable insights, and master their cash flow with AI-assisted automation.

##  2. Tech Stack & Infrastructure

This project adopts a **Monorepo** architecture, housing multiple independent services engineered for high performance and scalability.

###  Core Backend
- **Framework:** Spring Boot 3.x (Spring Web, Spring Security, Spring Data JPA)
- **Language:** Java 21 (JDK 21)
- **Authentication:** OAuth2 (Google & Facebook Meta Login Integration)
- **Database:** Relational Database with Hibernate ORM
- **Optimization:** JVM memory tuning (`-XX:MaxRAMPercentage=75.0`), custom Spring profiles (`prod`).

###  AI & Data Mining Service 
- **Purpose:** Automated receipt processing and financial data categorization.
- **Ecosystem:** Python (Integrated Data Mining models).

###  Mobile App (Frontend)
- **Framework:** Flutter (Dart).
- **Features:** Cross-platform fluid UI, secure token storage, real-time API communication via HTTPS.

###  DevOps & Deployment
- **Containerization:** Docker & Docker Compose.
- **Image Optimization:** Multi-stage builds (reducing core image footprint), utilizing `eclipse-temurin:21-jre-jammy` & `alpine`.
- **Process Management:** `tini` as PID 1 for graceful shutdowns and zombie process reaping.
- **Security & Ops:** Non-root user execution (`spring:spring`), Actuator Healthchecks, timezone synchronization (`Asia/Ho_Chi_Minh`).
- **Infrastructure:** Azure VPS, Nginx Reverse Proxy (SSL/TLS configuration).

##  3. Key Features
- **Enterprise-Grade Security:** Secure third-party authentication flow using Google and Facebook OAuth2, fully validated at the Java Backend layer.
- **AI-Powered Automation:** Leverages Data Mining to intelligently process and categorize expenses, drastically reducing manual data entry time.
- **Production-Ready Operations:** Containerized with security best practices, automated health checks, and optimized resource allocation to prevent OOM errors.
- **Unified Domain Routing:** Seamless traffic management via Nginx over HTTPS (`https://ledgermind.dev`).

##  4. System Architecture

**Data Flow:**
1. **Client Layer:** Flutter App requests authentication or submits financial data.
2. **Proxy Layer:** Nginx intercepts the HTTPS request and routes it to the appropriate service.
3. **Core Layer:** Java Backend processes business logic, validates OAuth2 tokens, and interacts with the database.
4. **AI Layer:** Complex data mining tasks are securely delegated to the Python service for processing and returned to the core system.

##  5. Getting Started

### Prerequisites
To run this project locally, ensure you have the following installed:
- [Docker](https://www.docker.com/get-started) & Docker Compose
- Java JDK 21 (For local core-backend development)
- Flutter SDK (For local mobile-app development)

### Quick Start

1. **Clone the repository:**
   ```bash
   git clone https://github.com/TriPhan15/donutpay-monorepo.git
   cd donutpay-monorepo

2. **Configure Environment Variables:**

     Create a .env file in the root directory.
     Add your necessary configuration keys (Google Web Client ID, Facebook App ID, Database credentials).
   
4. **Deploy the Infrastructure:**

      Spin up the entire architecture (Backend, AI Service, Database) using Docker Compose:
    ```bash
    docker-compose up -d --build

5. **Verify Deployment:**

      Check the health status of the backend:
    ```bash
    curl -f http://localhost:8080/actuator/health
