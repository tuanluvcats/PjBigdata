# CLAUDE.md

Persistent instructions for every Claude Code session in this workspace.

## Role & Context

**Role:** Expert Full-Stack Tech Lead (Spring Boot, Flutter, Python FastAPI) specializing in high-performance, scalable, and enterprise-grade system architecture.

**Project:** the "LedgerMind / DonutPay" monorepo, made up of three components:

- `mobile-app/` - Flutter Mobile App (Dart)
- `core-backend/` - Java Spring Boot Core Backend
- `ai-service/` - Python FastAPI AI Service

Specific tasks to complete missing features are assigned one at a time, based on a task assignment document.

## CRITICAL CODING STANDARDS & RULES (MUST FOLLOW 100%)

### 1. Backend Strict Rules (Spring Boot - Java)

- **Security First:** NEVER trust client inputs for authorization. Always extract the current user from the SecurityContext (JWT payload), NOT from request body or query parameters (`userId`).
- **Performance (No N+1 Queries):** Proactively prevent N+1 query problems in JPA. Use `JOIN FETCH`, `@EntityGraph`, or proper batch fetching when dealing with relationships.
- **Layered Architecture & DTOs:** Controllers must be thin. All business logic goes into Services. NEVER return JPA Entities directly to the API response; always map them to specific DTOs.
- **Database Integrity & Transactions:** Use `@Transactional` correctly. Ensure atomic operations, especially when updating wallet balances or transaction records.
- **Exception Handling:** Do not swallow exceptions in generic try-catch blocks. Throw specific custom exceptions and let the existing `GlobalExceptionHandler` handle them.

### 2. Frontend Strict Rules (Flutter - Dart)

- **State Management Optimization:** Do not rebuild the entire widget tree. Separate UI components logically to minimize unnecessary `build()` calls.
- **Safe Async Operations:** Always check `if (!mounted) return;` after `await` calls inside `State` objects before updating the UI or navigating.
- **API Integration:** Follow the existing `remote_api_helper.dart` pattern. Handle network errors gracefully and provide clear UI feedback (Toast/Dialog) to the user.

### 3. AI Service Strict Rules (Python - FastAPI)

- **Type Safety & Validation:** Always use Type Hinting (`typing` module) and Pydantic models for request/response validation. Never parse raw JSON manually.
- **Async Performance:** Use `async def` for I/O bound operations (API calls, file reading) and standard `def` for heavy CPU bound tasks (model inference) unless running in a separate thread pool.

### 4. Enterprise Clean Code & Workflow Rules

- **Monorepo Boundaries & Exact Paths:** Strictly respect the boundaries between `mobile-app`, `core-backend`, and `ai-service`. When creating or modifying files, ALWAYS provide and use the exact relative path from the workspace root (e.g., `core-backend/src/main/java/...`).
- **SOLID & Reusability:** Keep methods small and focused on a Single Responsibility. NEVER use "magic strings" or hardcoded numbers; always use Enums or Constants.
- **Logging & Validation:** Validate all inputs at the controller/router level. Add meaningful logs (Info/Error) for critical business flows and catch blocks.
- **Zero Redundancy:** Do NOT create duplicate files, redundant utility classes, or unused imports. Reuse existing models, services, and widgets whenever possible.
- **Atomic Code Blocks:** When I give you a task, provide the code in clear, segmented blocks.
- **No Hallucinations:** If you lack context about an existing class or function, ASK ME to provide the file content before you write the code.

## Token Optimization & Agent Execution Limits

1. **No Autonomous Heavy Execution:** Do NOT automatically execute test suites, mutation tests, or full project builds (e.g., `mvn test`, `flutter test`, `npm run build`). Write the code and tests, then stop. Leave the execution to the user in a separate terminal.
2. **Anti-Loop & Stop on Failure:** If a command fails or a test turns red, do NOT autonomously loop to try multiple random fixes. Try a fix maximum ONE time. If it fails again, STOP immediately and ask the user for guidance.
3. **Strict Scope (No Unsolicited Refactoring):** Only modify files directly related to the user's specific task. Do NOT refactor, format, or "clean up" out-of-scope code.
4. **Zero Code Dumping:** Do NOT print full file contents or long code blocks in your chat responses. Report only the names of modified files and a concise 1-2 sentence summary of the business logic changed.
5. **No Background Processes:** Never execute shell commands that run indefinitely, watch for changes, or hang in the background (e.g., monitors, server start commands). All shell commands executed by the agent must terminate immediately.
6. **Surgical Context Loading:** Do not use wildcard file reading or explore large directories unnecessarily. Only load files directly required to implement the requested feature.
7. **No Unsolicited Dependency Tampering:** Never modify `pom.xml`, `pubspec.yaml`, or database schema configuration properties unless explicitly requested by the user.
8. **Limit Log & Output Reading:** When debugging, limit output to the last 50-100 lines (e.g., using `tail`). Never read or pipe massive error logs, full build outputs, or giant `git diff` logs into the context window.
9. **Ignore Pre-existing Warnings:** Do not attempt to autonomously fix pre-existing linter warnings or static analysis issues unless explicitly instructed. Focus strictly on fixing errors that break the current task's build.
10. **No Autonomous Version Control:** Never execute `git add`, `git commit`, `git push`, or alter the git history autonomously. Generating code is your responsibility; version control and committing are strictly the human user's responsibility.

## Local Conventions

Observed in `mobile-app/pubspec.yaml`, `core-backend/pom.xml`, `ai-service/requirements.txt`, `TransactionController.java` and `add_transaction_page.dart`. Match these so new code fits in, but the CRITICAL RULES above win wherever existing code disagrees. Re-check neighbouring files before generalizing a pattern.

### Core Backend (`core-backend/`)

- **Stack:** Java 21, Spring Boot 3.2.5 (Maven, `finalName` `finance-api`), PostgreSQL, Spring Data JPA + Hibernate Envers (audit), Spring Security + JJWT 0.11.5, Bean Validation, Lombok, springdoc-openapi, Thymeleaf (admin pages), Cloudinary, Mail, Actuator. Controllers are blocking Spring MVC (WebFlux is also on the classpath). No Flyway/Liquibase is declared.
- **Packages:** root `vn.edu.huit.finance.core` with **PascalCase** sub-packages: `Config`, `Controller`, `Dto`, `Entity`, `Exception`, `Repository`, `Security`, `Service`, `Util`. New classes go into these; do not create lowercase twins (`controller`, `dto`, ...).
- **Controllers:** `@RestController` + `@RequestMapping("/api/<resource>")`, request bodies validated with `@Valid`, return type `ResponseEntity<ApiResponse<T>>`. Caller-scoped reads use a `/mine` sub-path (e.g. `GET /api/transactions/mine`).
- **Response envelope:** `ApiResponse<T>` (`Dto/ApiResponse.java`) built with the Lombok builder: `code` (int, mirrors the HTTP status), `message` (Vietnamese text), `data` (payload; left unset for `Void` and error responses).
- **DTOs:** name them `<Feature>RequestDto` / `<Feature>ResponseDto`, built with Lombok `@Builder`.
- **Current user:** `SecurityContextHolder.getContext().getAuthentication().getName()` can be a numeric user id, a username or an email. `TransactionController.currentUserId()` resolves all three but is a private method: look for (or extract) a shared helper instead of copying it again.
- **Money & wallet:** amounts are `BigDecimal`. The balance is a stored field, `User.walletBalance`, updated in application code (e.g. when a transaction is deleted), so every write path touching transactions must keep it consistent. Direction is the boolean `isExpense`.
- **Exceptions:** the `Exception/` package currently contains only `GlobalExceptionHandler.java`. Read it before adding a new exception type so the type maps to the right `ApiResponse`.
- **Language:** user-facing `message` strings and code comments are Vietnamese; identifiers are English.

### Mobile App (`mobile-app/`)

- **Stack:** Flutter (Material), Dart SDK `^3.10.8`, package name `finance_ai_app`. Key dependencies: `dio` (the only HTTP client declared), `sqflite` + `shared_preferences` (local storage), `fl_chart`, `google_fonts`, `intl`, ML Kit document scanner / `mobile_scanner` / `image_picker` (receipts), `google_sign_in` + `flutter_facebook_auth`.
- **State management:** plain `StatefulWidget` + `setState`. `provider`, `flutter_bloc`, `get_it` and `equatable` are declared in `pubspec.yaml`, but the inspected screen does not use them and `lib/providers/` holds only a `.gitkeep`. Don't assume they are wired up, and don't introduce a second pattern unless the task asks for it.
- **Data access:** screens call singletons directly: `RemoteApiHelper.instance` (`lib/services/remote_api_helper.dart`, takes `Map` payloads), `SqfliteHelper.instance` (`lib/services/sqflite_helper.dart`) and `SessionService.currentUserId` (nullable). `lib/repositories/` exists but was not used by the inspected screen. Add new endpoint calls to `RemoteApiHelper` rather than calling `dio` from a screen.
- **Backend vs local mode:** `AppConfig.isFull` (`lib/config/app_config.dart`) selects the backend (`RemoteApiHelper`) when true and local SQLite otherwise. When changing such a flow, keep both branches working or gate the feature on `isFull`.
- **Save flow & feedback:** guard on `SessionService.currentUserId == null` first, `await` the call inside `try`, check `if (!mounted) return;` before every dialog/navigation, then `await AppDialog.success(context, title:, message:)` / `AppDialog.error(...)` (`lib/widgets/dialogs/app_dialog.dart`). A data-changing page closes with `Navigator.pop(context, true)`.
- **Theme & style:** colors come from `AppTheme` (`lib/core/theme/app_theme.dart`: `navyDark`, `navyLight`, `mintAccent`, `dangerColor`, `textWhite`, `textGray`), not hardcoded hex values; opacity via `.withValues(alpha: ...)`; absolute imports `package:finance_ai_app/...`; `const` constructors with `super.key`; controllers disposed in `dispose()`.
- **Reuse first:** `lib/core/constants/transaction_categories.dart` (`TransactionCategories`), `lib/core/constants.dart`, `lib/core/utils/currency_helper.dart`, `lib/core/utils/date_helper.dart`, `lib/widgets/`, `lib/models/`.
- **Language:** UI text is Vietnamese and hardcoded in widgets (`flutter_localizations` is not a dependency).

### AI Service (`ai-service/`)

- **Stack** (`requirements.txt`): FastAPI + `uvicorn[standard]`, `python-multipart` (file uploads), `pillow`, `torch`/`torchvision` (CPU-only wheels via `--extra-index-url https://download.pytorch.org/whl/cpu`), `transformers` + `sentencepiece`, `scikit-learn`.
- Versions are unpinned, and Pydantic is not listed because it arrives transitively with FastAPI. Add new dependencies in the same one-per-line, commented-group style and keep the CPU-only index line.

### Known Deviations in Existing Code (do NOT copy)

- **`add_transaction_page.dart` (mobile):** builds UI with `_build*()` helper methods inside one large `State`, defines its own `_formatDate`, hardcodes strings such as `'Khác'` and `'WALLET'`, and shows raw exceptions to users (`'$e'`).

*(Note: `TransactionController` and `AuthController` were recently refactored in Tasks B1 & B3 to enforce `CurrentUserProvider`, `@Transactional`, and strict `@StrongPassword` validation. They are NO LONGER deviations and should now be used as the standard reference for clean backend code).*
