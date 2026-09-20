# Junior Software Engineer Onboarding Curriculum

This curriculum is ordered from beginner to advanced and is tailored to the LedgerMind / DonutPay monorepo.

## Lesson 1: Product, Repo, and System Architecture

1. Learning Objective
   - Understand what the product does, how the monorepo is organized, and how the mobile app, backend, AI service, and deployment pieces fit together.
2. Why this topic matters
   - A junior engineer needs the system map before changing any feature safely.
3. Files/Folders to study
   - [README.md](README.md)
   - [docker-compose.yml](docker-compose.yml)
   - [core-backend/](core-backend)
   - [mobile-app/](mobile-app)
   - [ai-service/](ai-service)
4. Important classes/files
   - [core-backend/src/main/java/vn/edu/huit/finance/core/CoreBackendApplication.java](core-backend/src/main/java/vn/edu/huit/finance/core/CoreBackendApplication.java)
   - [mobile-app/lib/main.dart](mobile-app/lib/main.dart)
   - [ai-service/ai_service.py](ai-service/ai_service.py)
   - [docker-compose.yml](docker-compose.yml)
5. Concepts to understand
   - Monorepo structure
   - Client/server split
   - Service boundaries
   - Request routing between mobile, backend, and AI service
   - Environment-based configuration
6. Request flow involved
   - User action in Flutter app -> backend REST API -> database and/or AI service -> response returned to the app.
7. Common beginner mistakes
   - Treating the repo as one application instead of three cooperating services.
   - Editing app code without checking which service owns the behavior.
   - Ignoring environment variables and local setup differences.
8. Practical exercises
   - Draw the end-to-end architecture on one page.
   - Identify which service owns login, receipts, payments, and OCR.
   - Trace one feature from UI to backend to storage.
9. Interview questions
   - What is the difference between a monorepo and a single-service repo?
   - Which part of this system is responsible for business logic?
   - How would you trace a request across all services?
10. Estimated study time
   - 4 to 6 hours.

## Lesson 2: Development Environment, Tooling, and Mobile App Basics

1. Learning Objective
   - Set up the Flutter app, understand its folder structure, and learn the app bootstrap path.
2. Why this topic matters
   - Most junior work starts in the mobile UI, so they must be able to run and navigate the app confidently.
3. Files/Folders to study
   - [mobile-app/pubspec.yaml](mobile-app/pubspec.yaml)
   - [mobile-app/lib/](mobile-app/lib)
   - [mobile-app/android/](mobile-app/android)
   - [mobile-app/ios/](mobile-app/ios)
   - [mobile-app/web/](mobile-app/web)
   - [mobile-app/deploy.sh](mobile-app/deploy.sh)
4. Important classes/files
   - [mobile-app/lib/main.dart](mobile-app/lib/main.dart)
   - [mobile-app/lib/config/app_config.dart](mobile-app/lib/config/app_config.dart)
   - [mobile-app/lib/core/theme/app_theme.dart](mobile-app/lib/core/theme/app_theme.dart)
   - [mobile-app/lib/screens/main_scaffold.dart](mobile-app/lib/screens/main_scaffold.dart)
5. Concepts to understand
   - Flutter app lifecycle
   - Widget tree
   - MaterialApp and theme switching
   - Platform folders for Android, iOS, web, desktop
   - Build-time configuration with dart-define
6. Request flow involved
   - App launch -> initialization -> session check -> theme load -> route to login or main shell.
7. Common beginner mistakes
   - Changing a screen without tracing where it is mounted.
   - Confusing build-time config with runtime config.
   - Forgetting that Flutter code may run on several platforms.
8. Practical exercises
   - Run the app and identify the first screen shown after startup.
   - Change the app title and theme color in a safe branch.
   - Trace where `AppConfig` affects runtime behavior.
9. Interview questions
   - What happens inside `main()` before the first screen appears?
   - Why does Flutter use platform folders?
   - What is the role of `pubspec.yaml`?
10. Estimated study time
   - 6 to 8 hours.

## Lesson 3: Frontend Architecture, State, and Local Data

1. Learning Objective
   - Understand how the Flutter app organizes state, repositories, services, and local persistence.
2. Why this topic matters
   - Frontend bugs often come from state, async timing, or storage misunderstandings rather than UI layout.
3. Files/Folders to study
   - [mobile-app/lib/core/](mobile-app/lib/core)
   - [mobile-app/lib/models/](mobile-app/lib/models)
   - [mobile-app/lib/providers/](mobile-app/lib/providers)
   - [mobile-app/lib/repositories/](mobile-app/lib/repositories)
   - [mobile-app/lib/services/](mobile-app/lib/services)
   - [mobile-app/lib/widgets/](mobile-app/lib/widgets)
   - [mobile-app/lib/screens/](mobile-app/lib/screens)
4. Important classes/files
   - [mobile-app/lib/services/session_service.dart](mobile-app/lib/services/session_service.dart)
   - [mobile-app/lib/services/remote_api_helper.dart](mobile-app/lib/services/remote_api_helper.dart)
   - [mobile-app/lib/services/db_helper.dart](mobile-app/lib/services/db_helper.dart)
   - [mobile-app/lib/services/sqflite_helper.dart](mobile-app/lib/services/sqflite_helper.dart)
   - [mobile-app/lib/repositories/auth_repository.dart](mobile-app/lib/repositories/auth_repository.dart)
   - [mobile-app/lib/repositories/app_repository.dart](mobile-app/lib/repositories/app_repository.dart)
5. Concepts to understand
   - Separation of concerns in Flutter
   - Repository pattern
   - Local storage with SharedPreferences and Sqflite
   - Async/await and widget rebuilds
   - Form handling and reusable UI components
6. Request flow involved
   - User interaction -> screen state -> repository/service -> local storage or API -> UI refresh.
7. Common beginner mistakes
   - Putting API calls directly inside widgets.
   - Mixing local cache logic with remote API logic.
   - Updating state after the widget is disposed.
8. Practical exercises
   - Locate where login tokens are stored and loaded.
   - Add one small local preference setting and persist it.
   - Trace one screen from widget tree to repository call.
9. Interview questions
   - Why use repositories in a Flutter app?
   - What is the difference between SharedPreferences and Sqflite?
   - How do you avoid setState bugs in async code?
10. Estimated study time
   - 8 to 10 hours.

## Lesson 4: Backend Foundations and REST APIs

1. Learning Objective
   - Learn how the Spring Boot backend is structured and how controllers, services, DTOs, and repositories work together.
2. Why this topic matters
   - The backend is the source of truth for finance data, business rules, and integrations.
3. Files/Folders to study
   - [core-backend/pom.xml](core-backend/pom.xml)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Controller/](core-backend/src/main/java/vn/edu/huit/finance/core/Controller)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Service/](core-backend/src/main/java/vn/edu/huit/finance/core/Service)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Dto/](core-backend/src/main/java/vn/edu/huit/finance/core/Dto)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Repository/](core-backend/src/main/java/vn/edu/huit/finance/core/Repository)
   - [core-backend/src/main/resources/application.yml](core-backend/src/main/resources/application.yml)
4. Important classes/files
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Controller/AuthController.java](core-backend/src/main/java/vn/edu/huit/finance/core/Controller/AuthController.java)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Controller/TransactionController.java](core-backend/src/main/java/vn/edu/huit/finance/core/Controller/TransactionController.java)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Controller/ReceiptController.java](core-backend/src/main/java/vn/edu/huit/finance/core/Controller/ReceiptController.java)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Service/TransactionService.java](core-backend/src/main/java/vn/edu/huit/finance/core/Service/TransactionService.java)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Dto/TransactionRequestDto.java](core-backend/src/main/java/vn/edu/huit/finance/core/Dto/TransactionRequestDto.java)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Entity/Transaction.java](core-backend/src/main/java/vn/edu/huit/finance/core/Entity/Transaction.java)
5. Concepts to understand
   - Spring Boot application structure
   - REST controllers
   - DTOs versus entities
   - Service layer business logic
   - JPA repositories and ORM mapping
   - Validation and error handling
6. Request flow involved
   - Flutter app -> HTTP controller -> service layer -> repository -> PostgreSQL -> response DTO -> Flutter app.
7. Common beginner mistakes
   - Returning entities directly from controllers.
   - Putting business logic in controllers.
   - Forgetting validation and null checks in request DTOs.
8. Practical exercises
   - Trace one transaction create/read/update flow end to end.
   - Add a new field to a DTO and propagate it correctly.
   - Write a simple controller test or service test.
9. Interview questions
   - Why should controllers stay thin?
   - What is the purpose of a DTO?
   - How does Spring Data JPA reduce boilerplate?
10. Estimated study time
   - 10 to 12 hours.

## Lesson 5: Authentication, Authorization, and Security

1. Learning Objective
   - Understand how login, JWT, security filters, and social authentication are implemented.
2. Why this topic matters
   - Finance software must protect user identity, tokens, and sensitive account data.
3. Files/Folders to study
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Security/](core-backend/src/main/java/vn/edu/huit/finance/core/Security)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Controller/AuthController.java](core-backend/src/main/java/vn/edu/huit/finance/core/Controller/AuthController.java)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Service/SocialAuthService.java](core-backend/src/main/java/vn/edu/huit/finance/core/Service/SocialAuthService.java)
   - [mobile-app/lib/services/social_auth_service.dart](mobile-app/lib/services/social_auth_service.dart)
   - [mobile-app/lib/services/session_service.dart](mobile-app/lib/services/session_service.dart)
4. Important classes/files
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Security/SecurityConfig.java](core-backend/src/main/java/vn/edu/huit/finance/core/Security/SecurityConfig.java)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Security/JwtAuthFilter.java](core-backend/src/main/java/vn/edu/huit/finance/core/Security/JwtAuthFilter.java)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Security/JwtUtil.java](core-backend/src/main/java/vn/edu/huit/finance/core/Security/JwtUtil.java)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Security/CustomUserDetailsService.java](core-backend/src/main/java/vn/edu/huit/finance/core/Security/CustomUserDetailsService.java)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Service/AuthService.java](core-backend/src/main/java/vn/edu/huit/finance/core/Service/AuthService.java)
5. Concepts to understand
   - Authentication versus authorization
   - JWT issuance and validation
   - Security filters and request interception
   - OAuth/social login concepts
   - Token storage on mobile
   - Secret management and environment variables
6. Request flow involved
   - Login request -> backend auth controller/service -> JWT creation -> mobile session storage -> authenticated API requests with token.
7. Common beginner mistakes
   - Logging secrets or tokens.
   - Storing tokens insecurely.
   - Confusing authentication flow with general API flow.
8. Practical exercises
   - Trace the login path and document where the JWT is created and read.
   - Identify where protected endpoints are enforced.
   - Review one security configuration and explain its effect.
9. Interview questions
   - What is a JWT and why is it used here?
   - Where should secrets live in production?
   - What is the role of a security filter?
10. Estimated study time
   - 8 to 10 hours.

## Lesson 6: Database Design, ORM, and Domain Modeling

1. Learning Objective
   - Understand the domain entities, database access layer, migrations/backup artifacts, and how finance data is stored.
2. Why this topic matters
   - Bad data modeling causes incorrect balances, duplicate records, and hard-to-debug production defects.
3. Files/Folders to study
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Entity/](core-backend/src/main/java/vn/edu/huit/finance/core/Entity)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Repository/](core-backend/src/main/java/vn/edu/huit/finance/core/Repository)
   - [core-backend/src/main/resources/application.yml](core-backend/src/main/resources/application.yml)
   - [core-backend/backups/](core-backend/backups)
4. Important classes/files
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Entity/User.java](core-backend/src/main/java/vn/edu/huit/finance/core/Entity/User.java)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Entity/Transaction.java](core-backend/src/main/java/vn/edu/huit/finance/core/Entity/Transaction.java)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Entity/Receipt.java](core-backend/src/main/java/vn/edu/huit/finance/core/Entity/Receipt.java)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Entity/SavingGoal.java](core-backend/src/main/java/vn/edu/huit/finance/core/Entity/SavingGoal.java)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Repository/TransactionRepository.java](core-backend/src/main/java/vn/edu/huit/finance/core/Repository/TransactionRepository.java)
5. Concepts to understand
   - Relational database basics
   - Entity relationships
   - JPA/Hibernate mapping
   - Auditing and revision history
   - Transactions and data consistency
   - Backup and restore concepts
6. Request flow involved
   - API request -> service layer -> repository -> entity mapping -> PostgreSQL -> persisted finance record.
7. Common beginner mistakes
   - Assuming entity fields match API fields one-to-one.
   - Ignoring time zones and date normalization.
   - Writing repository queries without understanding indexes or cardinality.
8. Practical exercises
   - Diagram the main entities and their relationships.
   - Inspect one backup file and identify what it protects.
   - Explain how a transaction record is created and later retrieved.
9. Interview questions
   - Why use an ORM here instead of raw SQL everywhere?
   - What problems do timestamps and time zones create?
   - When would you add a database index?
10. Estimated study time
   - 8 to 12 hours.

## Lesson 7: AI Service, OCR Pipeline, and Pattern Mining

1. Learning Objective
   - Learn how the FastAPI service extracts receipt data and computes spending patterns.
2. Why this topic matters
   - This is the automation layer that turns receipt images into usable finance data.
3. Files/Folders to study
   - [ai-service/requirements.txt](ai-service/requirements.txt)
   - [ai-service/Dockerfile](ai-service/Dockerfile)
   - [ai-service/ai_service.py](ai-service/ai_service.py)
   - [ai-service/wcm_spade.py](ai-service/wcm_spade.py)
   - [ai-service/budget_advisor.py](ai-service/budget_advisor.py)
   - [ai-service/Version3/donut_result/](ai-service/Version3/donut_result)
4. Important classes/files
   - [ai-service/ai_service.py](ai-service/ai_service.py)
   - [ai-service/wcm_spade.py](ai-service/wcm_spade.py)
   - [ai-service/budget_advisor.py](ai-service/budget_advisor.py)
5. Concepts to understand
   - FastAPI routing
   - File upload handling
   - OCR and document understanding
   - Transformer inference
   - Post-processing and text normalization
   - Pattern mining / recommendation logic
6. Request flow involved
   - Mobile app uploads receipt image -> backend or AI service endpoint -> OCR model runs -> parsed receipt JSON returned -> backend stores or uses result.
7. Common beginner mistakes
   - Confusing preprocessing with model inference.
   - Breaking tokenizer/model compatibility when changing model files.
   - Forgetting CPU/GPU runtime differences.
8. Practical exercises
   - Trace the `/donut/scan` endpoint from upload to JSON output.
   - Change the health endpoint or add a small diagnostic route.
   - Explain how a raw OCR string is turned into structured data.
9. Interview questions
   - Why keep AI inference in a separate service?
   - What is the difference between preprocessing and post-processing?
   - How would you handle slow OCR requests in production?
10. Estimated study time
   - 10 to 14 hours.

## Lesson 8: Docker, Deployment, and VPS Operations

1. Learning Objective
   - Understand containerization, orchestration, and how the system is deployed to a VPS.
2. Why this topic matters
   - A junior engineer must know how the code ships and how to reason about runtime environments.
3. Files/Folders to study
   - [docker-compose.yml](docker-compose.yml)
   - [core-backend/Dockerfile](core-backend/Dockerfile)
   - [ai-service/Dockerfile](ai-service/Dockerfile)
   - [mobile-app/deploy.sh](mobile-app/deploy.sh)
   - [README.md](README.md)
4. Important classes/files
   - [docker-compose.yml](docker-compose.yml)
   - [core-backend/Dockerfile](core-backend/Dockerfile)
   - [ai-service/Dockerfile](ai-service/Dockerfile)
   - [mobile-app/deploy.sh](mobile-app/deploy.sh)
5. Concepts to understand
   - Docker images and containers
   - Multi-stage builds
   - Compose networking
   - Health checks
   - Environment variables and secrets
   - VPS deployment basics
6. Request flow involved
   - Client requests reach the VPS -> reverse proxy or exposed service -> backend container -> AI service container or database.
7. Common beginner mistakes
   - Hardcoding local paths or local IP addresses into production config.
   - Ignoring container health checks.
   - Rebuilding images without checking environment drift.
8. Practical exercises
   - Explain the purpose of each Dockerfile stage.
   - Run the compose stack locally and map container-to-container communication.
   - Identify all external configuration values used at runtime.
9. Interview questions
   - Why use Docker Compose for this repo?
   - What is the benefit of a healthcheck in a containerized backend?
   - What usually breaks first when moving from local to VPS?
10. Estimated study time
   - 6 to 10 hours.

## Lesson 9: Logging, Monitoring, Performance, and Reliability

1. Learning Objective
   - Learn how to observe the system, spot bottlenecks, and reduce incident risk.
2. Why this topic matters
   - Production software fails silently unless you instrument and monitor it properly.
3. Files/Folders to study
   - [core-backend/Dockerfile](core-backend/Dockerfile)
   - [core-backend/src/main/resources/application.yml](core-backend/src/main/resources/application.yml)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Service/](core-backend/src/main/java/vn/edu/huit/finance/core/Service)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Util/](core-backend/src/main/java/vn/edu/huit/finance/core/Util)
   - [ai-service/ai_service.py](ai-service/ai_service.py)
   - [mobile-app/lib/services/remote_api_helper.dart](mobile-app/lib/services/remote_api_helper.dart)
4. Important classes/files
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Service/MonthlyJobService.java](core-backend/src/main/java/vn/edu/huit/finance/core/Service/MonthlyJobService.java)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Service/BackupService.java](core-backend/src/main/java/vn/edu/huit/finance/core/Service/BackupService.java)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Service/PaymentService.java](core-backend/src/main/java/vn/edu/huit/finance/core/Service/PaymentService.java)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Exception/GlobalExceptionHandler.java](core-backend/src/main/java/vn/edu/huit/finance/core/Exception/GlobalExceptionHandler.java)
5. Concepts to understand
   - Logging levels
   - Structured error handling
   - Health checks
   - Latency, throughput, and failure rates
   - Long-running jobs and scheduled tasks
   - Resource limits in containers
6. Request flow involved
   - Requests are processed -> errors are logged or converted to API responses -> container health and backend uptime are monitored.
7. Common beginner mistakes
   - Logging sensitive values.
   - Measuring only successful requests.
   - Ignoring slow paths like OCR or payment callbacks.
8. Practical exercises
   - Find every place a failure can become a user-facing response.
   - Identify the best health indicator for each service.
   - Estimate which API calls are most expensive.
9. Interview questions
   - What metrics would you monitor first in production?
   - How do you distinguish an app bug from an infrastructure issue?
   - Why do scheduled jobs need special attention in production?
10. Estimated study time
   - 6 to 8 hours.

## Lesson 10: Common Production Issues and Support Readiness

1. Learning Objective
   - Prepare the engineer to diagnose real incidents and resolve common production failures.
2. Why this topic matters
   - A junior engineer must learn to handle outages, data problems, deployment regressions, and integration failures responsibly.
3. Files/Folders to study
   - [README.md](README.md)
   - [core-backend/backups/](core-backend/backups)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Exception/GlobalExceptionHandler.java](core-backend/src/main/java/vn/edu/huit/finance/core/Exception/GlobalExceptionHandler.java)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Service/](core-backend/src/main/java/vn/edu/huit/finance/core/Service)
   - [mobile-app/lib/services/](mobile-app/lib/services)
   - [ai-service/ai_service.py](ai-service/ai_service.py)
4. Important classes/files
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Service/PaymentService.java](core-backend/src/main/java/vn/edu/huit/finance/core/Service/PaymentService.java)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Service/ReceiptService.java](core-backend/src/main/java/vn/edu/huit/finance/core/Service/ReceiptService.java)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Service/BackupService.java](core-backend/src/main/java/vn/edu/huit/finance/core/Service/BackupService.java)
   - [core-backend/src/main/java/vn/edu/huit/finance/core/Controller/MockGatewayController.java](core-backend/src/main/java/vn/edu/huit/finance/core/Controller/MockGatewayController.java)
5. Concepts to understand
   - Error triage
   - Rollback versus forward fix
   - Payment and webhook failures
   - OCR/model availability issues
   - Data drift and stale cache problems
   - Backup and restore readiness
6. Request flow involved
   - User request or callback fails -> logs, error handler, and backup/recovery tooling are used to isolate the issue -> safe fix or rollback is applied.
7. Common beginner mistakes
   - Changing production config without checking downstream effects.
   - Assuming payment or OCR errors are always backend bugs.
   - Skipping data validation when investigating corrupt records.
8. Practical exercises
   - Create a runbook for one common incident, such as login failure, OCR timeout, or payment callback failure.
   - List the first five checks you would do during a production outage.
   - Explain how you would validate a backup restore before an emergency.
9. Interview questions
   - What is your first step when a production API starts returning 500s?
   - How would you investigate an intermittent payment issue?
   - What should be in a junior engineer’s incident checklist?
10. Estimated study time
   - 6 to 8 hours.

## Suggested Progression

1. Start with Lessons 1 to 3 to understand the system and the mobile app.
2. Move to Lessons 4 to 6 to become productive in backend and data changes.
3. Study Lesson 7 to understand the AI workflow.
4. Finish with Lessons 8 to 10 to build deployment and production readiness.
