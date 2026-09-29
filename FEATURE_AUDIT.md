# FEATURE AUDIT - CNTT-KLCN175

Ngày rà soát: 2026-09-29  
Chuẩn đối chiếu: `ycda.txt` và source hiện có trong `core-backend/`, `mobile-app/`, `ai-service/`.

## 1. Kết luận tổng quan

Dự án **chưa thể xác nhận hoàn thành 100%**. Các nghiệp vụ lõi đã có code thực tế: xác thực JWT, giao dịch, budget tuần/tháng, cảnh báo, đồng bộ offline có idempotency, admin dashboard, mobile chart và luồng DONUT receipt. Tuy nhiên vẫn còn các mục chưa đủ bằng chứng trong repository hoặc còn dang dở: bộ dữ liệu/quy trình huấn luyện được version hóa, mở rộng tokenizer, cache cho thống kê, JMeter/Postman artefact, tài liệu phân tích thiết kế, UI notification center, thu hồi token chéo phiên và một số chức năng Phase 2.

Trạng thái trong bảng phản ánh **implementation evidence**, không chỉ dựa trên mô tả trong README/task assignment. “Đã hoàn thành” nghĩa là đã thấy module/route/flow tương ứng; không đồng nghĩa đã nghiệm thu end-to-end trên VPS thật.

## 2. Bảng đối chiếu requirements

| Tên tính năng theo requirements | Trạng thái | File/module bằng chứng |
|---|---|---|
| Phân tích yêu cầu nghiệp vụ và thiết kế kiến trúc Microservices | Đang làm | `docker-compose.yml`, `README.md`; chưa có bộ tài liệu/sơ đồ kiến trúc độc lập |
| Thiết kế cơ sở dữ liệu giao dịch tài chính | Hoàn thành | `core-backend/src/main/java/vn/edu/huit/finance/core/Entity/`, `Repository/`, `src/main/resources/application.yml` |
| UI/UX và ứng dụng mobile Flutter | Hoàn thành | `mobile-app/lib/`, `mobile-app/pubspec.yaml`, `mobile-app/test/` |
| Web Admin Dashboard | Hoàn thành | `core-backend/src/main/java/vn/edu/huit/finance/core/Controller/AdminController.java`, `src/main/resources/templates/` |
| Thu thập, xử lý và chuẩn hóa dataset hóa đơn tiếng Việt | Đang làm | `ai-service/Version3/donut_result/` có model/tokenizer; không có dataset, manifest hoặc pipeline tiền xử lý trong repo |
| Nghiên cứu kiến trúc DONUT End-to-End OCR-free | Hoàn thành (runtime) | `ai-service/ai_service.py`, `ai-service/Version3/donut_result/`; phần tài liệu nghiên cứu chưa được version hóa riêng |
| Nghiên cứu/mở rộng Vietnamese Tokenizer | Đang làm | `ai-service/ai_service.py` nạp `AutoTokenizer` từ model output; chưa có script/quy trình mở rộng và đánh giá tokenizer |
| Huấn luyện thành công DONUT cho hóa đơn tiếng Việt | Đang làm | `ai-service/Version3/donut_result/` chứa weights/config; không có training script, checkpoint log, metric hoặc notebook để kiểm chứng tái lập |
| Trích xuất 4 trường Seller, Address, Timestamp, Total Cost | Hoàn thành | `ai-service/ai_service.py`, `core-backend/.../Service/OcrService.java`, `Dto/ExtractedReceiptDto.java`, `Service/OcrServiceTest.java` |
| Hậu xử lý Regex và chuẩn hóa kết quả OCR | Hoàn thành | `ai-service/ai_service.py` (`clean_vietnamese_text`, parse total), `core-backend/.../Service/OcrService.java` |
| Đăng ký, đăng nhập và refresh token | Hoàn thành | `core-backend/.../Controller/AuthController.java`, `Service/AuthService.java`, `Security/`; `mobile-app/lib/services/remote_api_helper.dart` |
| Đăng xuất và thu hồi access/refresh token hiện tại | Hoàn thành | `AuthController.java`, `TokenBlacklistService.java`, `mobile-app/lib/services/remote_api_helper.dart` |
| Đổi mật khẩu trong môi trường backend | Hoàn thành | `AuthController.java`, `Dto/ChangePasswordRequestDto.java`, `Service/AuthService.java` |
| Phân quyền người dùng Spring Security/JWT | Hoàn thành | `core-backend/.../Security/`, `Config/SecurityConfig.java`, `AdminTransactionController.java` |
| Thêm giao dịch thu/chi | Hoàn thành | `TransactionController.java`, `Service/TransactionService.java`, `mobile-app/lib/screens/transactions/`, `services/remote_api_helper.dart` |
| Sửa giao dịch | Hoàn thành (phạm vi lõi) | `TransactionController.java` (`PUT /{id}`), `TransactionUpdateRequestDto.java`, mobile transaction edit screen |
| Xóa và hoàn tiền giao dịch | Hoàn thành | `TransactionController.java`, `TransactionService.java` |
| Phân loại giao dịch/danh mục mặc định | Hoàn thành | `CategoryApiController.java`, `AdminController.java`, category entities/services, `TransactionService.java` |
| Đồng bộ trạng thái và lưu trữ tập trung | Hoàn thành (phạm vi lõi) | `mobile-app/lib/models/sync_status.dart`, `services/sqflite_helper.dart`, `repositories/app_repository.dart`, `TransactionService.java` (`clientId`) |
| Chống tạo trùng khi retry offline | Hoàn thành | `TransactionRepository.java`, `TransactionService.java`, `Transaction.clientId`, mobile resend flow |
| Tạo/sửa/xóa budget theo tuần/tháng | Hoàn thành | `BudgetController.java`, `BudgetService.java`, `Entity/Budget.java`, mobile budget screens/models |
| Theo dõi chi tiêu và cảnh báo vượt ngưỡng | Hoàn thành (backend + local UI) | `BudgetAlertService.java`, `Notification.java`, `mobile-app/lib/widgets/budget/`, `dashboard_page.dart` |
| Web Admin quản lý tài khoản | Hoàn thành | `AdminController.java`, `UserController.java`, templates admin |
| Web Admin quản trị category mặc định | Hoàn thành | `AdminController.java` category routes, category service/repository |
| Giám sát activity/audit log | Đang làm | `AuditLog.java`, `AuditLogService.java`, `AdminController.java`; chưa chứng minh mọi mutation đều log và còn catch rỗng trong một luồng giao dịch |
| Thống kê tổng quan hệ thống trên Admin | Hoàn thành | `AdminController.java`, `AdminTransactionController.java`, dashboard templates |
| Biểu đồ thu/chi trên Mobile | Hoàn thành | `mobile-app/lib/screens/dashboard/`, `screens/statistics/`, `fl_chart` trong `pubspec.yaml` |
| Báo cáo/thói quen tiêu dùng | Hoàn thành (phạm vi hiện có) | `ai-service/wcm_spade.py`, `budget_advisor.py`, `/spade/*`, mobile AI/report widgets |
| Bộ lọc kỳ báo cáo và làm mới phân tích | Hoàn thành | mobile AI/report screens, `RemoteApiHelper` `/spade/analyze` và `/spade/recommend` |
| Chụp ảnh hóa đơn bằng camera/document scanner | Hoàn thành | `mobile-app/lib/screens/receipt_scanner/`, `pubspec.yaml` (`mobile_scanner`, document scanner) |
| Chọn ảnh từ thư viện | Hoàn thành | receipt scanner screens, `image_picker` integration |
| Mobile gọi AI Service độc lập | Hoàn thành | `RemoteApiHelper.scanReceipt()`, `ai-service/ai_service.py` (`POST /donut/scan`) |
| Review/chỉnh sửa kết quả OCR trước khi lưu | Hoàn thành (cơ bản) | `mobile-app/lib/screens/receipt_scanner/review_form_page.dart` |
| Đối soát và cập nhật giao dịch từ receipt | Hoàn thành (cơ bản) | `ReceiptProcessingService.java`, `TransactionService.java`, `ReceiptController.java`, `ReceiptStatus.java` |
| Chuẩn hóa workflow receipt đầy đủ và lưu raw/edited tách biệt | Đang làm | `ReceiptProcessingService.java` có status/feedback/ownership; chưa thấy đầy đủ state machine raw-vs-edited và retry UI |
| REST API kết nối Mobile - Core - AI | Hoàn thành | `core-backend/.../Controller/`, `mobile-app/lib/services/remote_api_helper.dart`, `ai-service/ai_service.py` |
| PostgreSQL làm hệ quản trị dữ liệu | Hoàn thành (cấu hình) | `core-backend/pom.xml`, `application.yml`, `docker-compose.yml` |
| Cache tối ưu truy vấn thống kê | Chưa làm | Không thấy Redis/Caffeine/Spring Cache dependency hoặc `@Cacheable` trong source |
| Swagger/OpenAPI | Hoàn thành (dependency/config surface) | `core-backend/pom.xml` có `springdoc-openapi-starter-webmvc-ui`; chưa có tài liệu export/screenshot kiểm chứng |
| Kiểm thử unit/integration | Hoàn thành (code/test suite) | `core-backend/src/test/`, `mobile-app/test/`, `ai-service/test_main.py`, `.github/workflows/ci.yml` |
| Kiểm thử API bằng Postman | Chưa làm | Không thấy collection/environment Postman trong repository |
| Kiểm thử tải bằng JMeter | Chưa làm | Không thấy `.jmx` hoặc load-test report trong repository |
| Đóng gói Docker/Docker Compose | Hoàn thành | `docker-compose.yml`, `core-backend/Dockerfile`, `ai-service/Dockerfile` |
| CI/CD | Hoàn thành (cấu hình) | `.github/workflows/ci.yml` |
| Triển khai thực tế VPS | Đang làm | CI có SSH deploy và README mô tả VPS, nhưng chưa có bằng chứng runtime/healthcheck production trong repository; README ghi Azure còn workflow ghi AWS |
| Tài liệu Use Case, class diagram, data/design diagrams | Chưa làm trong repository | Không tìm thấy file sơ đồ/đặc tả tương ứng; `ycda.txt` mới là yêu cầu |
| Báo cáo Word, slide và tài liệu nghiệm thu | Chưa làm trong repository | Không có bộ tài liệu báo cáo/slide tương ứng được version hóa |

## 3. Task/review còn tồn đọng

### 3.1. Từ `CNTT-KLCN175-Phung The Bao_CODE_REVIEW.md`

Các thiếu hụt P0/P1 của review cũ đã được task assignment tuyên bố đã xử lý và source hiện tại có bằng chứng tương ứng: change-password, Budget backend, update transaction, ownership/JWT, budget alert, blacklist, idempotency và OCR normalization/reconciliation. Các điểm sau vẫn còn cần đóng hoặc cần nghiệm thu lại:

- Activity log chưa được chứng minh bao phủ toàn bộ create/update/delete của user, category, transaction, receipt; cần bỏ các catch rỗng và bổ sung kiểm thử audit.
- Receipt reconciliation mới có trạng thái và feedback cơ bản; cần hoàn thiện state machine, lưu raw/edited tách biệt, retry/failure UI và kiểm chứng ownership toàn flow.
- OCR normalization đã có nhưng cần bổ sung contract/schema kiểm thử xuyên AI -> Mobile -> Backend cho cả bốn trường và các trường hợp thiếu dữ liệu.
- Báo cáo thói quen tiêu dùng chưa có bằng chứng về lịch sử báo cáo, export/share và endpoint thống kê riêng phía backend.
- Chưa có artefact Postman/JMeter để xác nhận mục kiểm thử và hiệu năng trong đề cương.
- Chưa có bằng chứng production cho VPS; cần thống nhất thông tin Azure/AWS và lưu kết quả deploy/healthcheck.

### 3.2. Từ `CNTT-KLCN175-Phung The Bao_TASK_ASSIGNMENT.md`

Các mục Phase 2 được đánh dấu chưa hoàn thành trong chính task assignment:

- **B6 & A7:** checklist cũ ghi chưa làm, nhưng backend hiện đã có `transactionDate` và `paymentMethod` trong `TransactionUpdateRequestDto` và `TransactionService.updateTransaction()`. Cần cập nhật checklist và nghiệm thu UI mobile thực tế để đóng task hoàn toàn.
- **B9 & A10:** notification center: backend `isRead`/mark-as-read và màn hình chuông thông báo. Hiện backend có GET danh sách nhưng `NotificationController` chưa có route mark-as-read.
- **B10:** thu hồi token chéo phiên sau khi đổi mật khẩu.
- **A12:** UI quên mật khẩu OTP trên mobile; backend reset API đã tồn tại.

Task assignment cũng còn ghi A6/A11 là “đang làm”, nhưng source hiện đã có `PUT /api/auth/change-password`, `POST /api/auth/logout` và các lời gọi tương ứng trong `RemoteApiHelper`. Đây là điểm cần cập nhật tài liệu và kiểm thử end-to-end, không còn là thiếu route/backend.

Kế hoạch hạ tầng trong task assignment cũng còn các deliverable chưa thấy trong repo:

- JMeter kịch bản 100-500 requests/s và HTML report.
- Postman collection/API evidence.
- Kết quả deploy VPS thực tế, domain/Nginx và healthcheck production.

### 3.3. Các yêu cầu môi trường/nghiên cứu cần bổ sung bằng chứng

- Dataset hóa đơn tiếng Việt, nguồn dữ liệu, preprocessing script và thống kê train/validation/test.
- Script/notebook huấn luyện DONUT, log GPU/Kaggle, metric và checkpoint provenance.
- Quy trình mở rộng tokenizer tiếng Việt và đánh giá trước/sau.
- Cơ chế cache cho các truy vấn thống kê như yêu cầu `ycda.txt`.
- Use Case, đặc tả, class diagram, mô hình dữ liệu/kiến trúc và tài liệu báo cáo/slide.

## 4. Kết luận nghiệm thu

Không nên xóa hai file review/task assignment ở thời điểm này. Chúng vẫn còn giá trị làm checklist cho các task Phase 2 và các deliverable nghiên cứu, kiểm thử, tài liệu, cache và production chưa có bằng chứng đầy đủ. Có thể xem phần nghiệp vụ lõi đã ở trạng thái gần code freeze, nhưng chưa đủ cơ sở để xác nhận dự án đạt 100% yêu cầu gốc.