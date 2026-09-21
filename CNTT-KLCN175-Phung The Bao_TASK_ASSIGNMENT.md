# Phân công Task & Git Workflow - CNTT-KLCN175

**Căn cứ:** `CNTT-KLCN175-Phung The Bao_CODE_REVIEW.md` - đối chiếu chức năng đề cương.
**Thành phần:** Trí (chủ repo, duyệt PR, Tech Lead) + Bạn A (Flutter Mobile) + Bạn B (Backend Java/Spring Boot).
**Lưu ý quan trọng (Quyết định của Tech Lead):**
- Đồ án tập trung vào luồng lõi Quản lý tài chính và Tích hợp AI OCR (chiếm 1.0 điểm thực hành). Hệ thống hiện tại đã đạt trạng thái **Code Freeze** cho các logic này.
- Các tính năng nâng cao (UI Thông báo, sửa nâng cao, thu hồi chéo phiên) được gom vào Sprint Mở rộng (Phase 2) để thực hiện sau khi đã hoàn tất triển khai hạ tầng.

## Tổng kết tiến độ (Cập nhật 2026-09-21)

- **Backend (Core & AI):** Đã hoàn thiện xuất sắc 100% logic lõi (B1-B5, B7, B8). Hệ thống vượt qua **759 Unit Tests** xanh mượt. Các rủi ro về Race Condition, N+1 Query, và Idempotency (chống trùng lặp giao dịch) đã được vá kín.
- **Mobile (Flutter):** Đã hoàn thiện (A1-A5, A8). Vượt qua **300 Tests**. Giao diện mượt mà, xử lý tốt state management (`!mounted`), luồng Quét hóa đơn và Đồng bộ Offline hoạt động ổn định.
- **Đang triển khai (Chốt sổ Code Khóa luận):** Thực hiện nốt Task A6 (Đổi mật khẩu Mobile) và A11 (Đăng xuất gọi Server) để đáp ứng chuẩn bề mặt đề cương.
- **Kiểm chứng môi trường thật:** Chưa deploy lên VPS PostgreSQL thật, chuẩn bị thực hiện qua Docker.

---

## 1. Task cho Bạn A (Flutter Mobile)

**Vùng code hoạt động:** `mobile-app/`

### (ĐÃ HOÀN THÀNH) Task A1 - Màn hình thiết lập ngân sách (Budget Screen)
- **Kết quả:** Tạo/sửa/xóa budget Tuần/Tháng qua `/api/budgets`. Hỗ trợ chế độ offline bằng SQLite.

### (ĐÃ HOÀN THÀNH) Task A2 - Cảnh báo vượt ngưỡng ngân sách
- **Kết quả:** Dashboard và Ledger hiển thị tiến độ; popup cảnh báo mốc 80% và 100% lưu cờ SharedPreferences chống làm phiền.

### (ĐÃ HOÀN THÀNH) Task A3 - Hoàn thiện báo cáo thói quen chi tiêu
- **Kết quả:** Tab AI có bộ lọc Tuần/Tháng/Tùy chọn. Tích hợp nút "Làm mới" gọi lại API phân tích WCM-SPADE.

### (ĐÃ HOÀN THÀNH) Task A4 - Màn hình sửa giao dịch (Edit Transaction)
- **Kết quả:** Gọi `PUT /api/transactions/{id}` sửa amount, category, note. Ledger tự tải lại mượt mà.

### (ĐÃ HOÀN THÀNH) Task A5 & A8 - Trạng thái đồng bộ Offline & Gửi lại
- **Kết quả:** 
  - Thêm cờ `syncStatus` (pending, synced, error) và sinh `clientId` (UUID v4) lưu tại SQLite.
  - Hiển thị badge trạng thái trên UI.
  - Cung cấp nút "Gửi lại" đẩy API `POST /api/transactions/checkout` với `clientId`.
  - Vượt qua kiểm thử Timeout và chặn UI crash.

---

## 2. Task cho Bạn B (Backend Java - Spring Boot)

**Vùng code hoạt động:** `core-backend/`

### (ĐÃ HOÀN THÀNH) Task B1 - API đổi mật khẩu
- **Kết quả:** `PUT /api/auth/change-password` hash BCrypt, validation chặt chẽ, chạy an toàn trong `@Transactional`.

### (ĐÃ HOÀN THÀNH) Task B2 - Budget Backend
- **Kết quả:** CRUD ngân sách toàn diện, phân quyền ownership nghiêm ngặt qua SecurityContext.

### (ĐÃ HOÀN THÀNH) Task B3 - API sửa giao dịch & Vá lỗ hổng phân quyền
- **Kết quả:** Cập nhật số dư ví atomically bằng `PESSIMISTIC_WRITE`. Dọn sạch các query parameter `userId` dễ bị khai thác, chuẩn hóa `ROLE_ADMIN`.

### (ĐÃ HOÀN THÀNH) Task B4 & B8 - Chuẩn hóa OCR Regex & Đối soát Hóa đơn (Reconciliation)
- **Kết quả:** 
  - Hậu xử lý Regex chuẩn hóa 4 trường Seller, Address, Timestamp, Total Cost. 
  - Khi Admin duyệt (Approve), hệ thống dùng `@Transactional` trích xuất `totalCost`, đè lên `amount` của Transaction và tự động lock ví để trừ/cộng chênh lệch an toàn.

### (ĐÃ HOÀN THÀNH) Task B5 - Cảnh báo Budget & JWT Blacklist
- **Kết quả:** Thu hồi token bằng bảng `revoked_tokens` với SHA-256 chống Replay Attack. Tự động sinh `Notification` khi giao dịch chạm ngưỡng 80% hoặc 100% ngân sách.

### (ĐÃ HOÀN THÀNH) Task B7 - Chống tạo trùng lặp giao dịch (Idempotency)
- **Kết quả:** Nhận diện `clientId` kết hợp `userId`. Trả về giao dịch cũ nếu Mobile bấm "Gửi lại" khi rớt mạng, đảm bảo không rác database.

---

## 3. Các Task Đang Triển Khai (Chốt sổ Code Khóa luận)

Hai task cuối cùng để khớp 100% bề mặt chức năng trong đề cương:

- **[ ĐANG LÀM ] Task A6 (Mobile) - Nối UI Đổi mật khẩu:** Gọi `PUT /api/auth/change-password` trên app, xử lý response và ép user đăng nhập lại do token cũ bị backend thu hồi.
- **[ ĐANG LÀM ] Task A11 (Mobile) - Luồng Đăng xuất chuẩn:** Gọi `POST /api/auth/logout` để báo server revoke token trước khi clear session SQLite ở máy client.

---

## 4. Sprint Mở Rộng (Phase 2): Tối Ưu Trải Nghiệm & Bảo Mật

*Ghi chú: Đây là các tính năng KHÔNG bắt buộc để đạt điểm 10/10 khóa luận. Tuy nhiên, nếu thời gian cho phép, việc triển khai sẽ giúp hệ thống đạt chuẩn Enterprise thực tế.*

- [ ] **Task B6 & A7 - Mở rộng Form sửa giao dịch:** Bổ sung API và UI cho phép người dùng cập nhật thêm `Date` (ngày tháng) và `Payment Method` (phương thức thanh toán) của một giao dịch đã tạo.
- [ ] **Task B9 & A10 - Giao diện Trung tâm Thông báo (Notification Center):**
  - **Backend (B9):** Thêm cờ `isRead` và API `mark-as-read` cho Notification.
  - **Mobile (A10):** Thiết kế màn hình "Chuông thông báo" hiển thị danh sách các cảnh báo ngân sách.
- [ ] **Task B10 - Thu hồi Token chéo phiên (Advanced Security):** Cấu hình Backend để khi người dùng đổi mật khẩu ở thiết bị A, hệ thống sẽ tự động vô hiệu hóa Refresh Token và tất cả các phiên đăng nhập đang tồn tại ở thiết bị B (yêu cầu quản lý token theo version hoặc device fingerprint).
- [ ] **Task A12 - Luồng Quên mật khẩu bằng OTP trên Mobile:** Dựng luồng UI bao gồm: màn hình nhập email gọi `forgot-password`, màn hình xác thực mã OTP và đặt lại mật khẩu (Backend đã có sẵn API `POST /api/auth/reset-password`).

---

## 5. Kế hoạch tiếp theo (Triển khai Hạ tầng & DevOps)

Sau khi hoàn tất A6 và A11, dự án sẽ tiến hành đóng gói và triển khai lấy điểm hạ tầng:

1. **CI/CD Pipeline với GitHub Actions:**
   - Tạo file `.github/workflows/ci.yml`.
   - Setup tự động chạy `flutter analyze` và `flutter test` cho Mobile.
   - Setup tự động chạy `mvn clean test` cho Backend mỗi khi có lượt Push hoặc Pull Request vào nhánh `main`.
2. **Kiểm thử Hiệu năng (Load Testing):**
   - Viết kịch bản JMeter (`.jmx`) bắn 100-500 requests/s vào API tạo giao dịch và API WCM-SPADE.
   - Xuất biểu đồ HTML Report đưa vào báo cáo Word.
3. **Triển khai Production (Docker & VPS Deployment):**
   - Sử dụng file `docker-compose.yml` có sẵn.
   - SSH vào VPS thông qua khóa `.pem` để build Docker image cho Core Backend và AI Service.
   - Map domain/port, cấu hình Nginx (nếu cần) và khởi chạy hệ thống thực tế.