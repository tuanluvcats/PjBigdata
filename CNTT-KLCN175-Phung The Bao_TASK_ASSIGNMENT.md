# Phân công Task & Git Workflow - CNTT-KLCN175

**Căn cứ:** [`CNTT-KLCN175-Phung The Bao_CODE_REVIEW.md`](CNTT-KLCN175-Phung%20The%20Bao_CODE_REVIEW.md) - danh sách chức năng còn thiếu/chưa tối ưu đối chiếu với đề cương.
**Thành phần:** Trí (chủ repo, duyệt PR) + Bạn A (Flutter Mobile) + Bạn B (Backend Java/Spring Boot).
**Lưu ý quan trọng đã cập nhật so với bản nháp ban đầu:**
- Biểu đồ thống kê (Pie/Bar chart, `fl_chart`) **đã có** ở `mobile-app/lib/screens/ai/ai_tab_page.dart` và `ledger_page.dart`, gọi WCM-SPADE. Không cần làm lại, chỉ cần bổ sung nhỏ (xem Task A3).
- Web Admin Dashboard (Thymeleaf + API thống kê) **đã có** (volume, user count, transaction count, top spenders, chart 6 tháng). Không cần làm lại.
- Việc thực sự đang thiếu là **Budget ở backend**, **sửa giao dịch**, **đổi mật khẩu production**, và **một số lỗ hổng phân quyền (userId từ client)**. Đây là các task ưu tiên P0 bên dưới.
- Đã đối chiếu lại với đúng nội dung file `.docx` đề cương (mục "Cài đặt tích hợp thuật toán vào chức năng ứng dụng", chiếm 1.0 điểm): đề cương yêu cầu tường minh 2 việc code-level là (1) chuẩn hóa 4 trường Seller/Address/Timestamp/Total Cost bằng Regex và (2) quy trình đối soát + cập nhật giao dịch tại Core Backend. Hai việc này **được nâng thành task chính thức B4** thay vì để ở dạng "làm thêm nếu rảnh" như bản trước, vì đây là phần chấm điểm trực tiếp trong đề cương.

## 1. Nguyên tắc chia việc (chống conflict)

- Chia theo tầng: Bạn A chỉ động vào `mobile-app/`, Bạn B chỉ động vào `core-backend/`. Không ai sửa file của người kia.
- Tính năng Budget và Sửa giao dịch cần **API contract** (endpoint, request/response JSON) thống nhất trước khi code song song. Bạn B chốt DTO trước, đăng lên nhóm chat/README task, Bạn A code UI theo đúng field đó (có thể dùng mock JSON để làm trước, không cần chờ backend xong 100%).
- Không ai được code thẳng và push lên `main`. Xem mục 5.

## 2. Task cho Bạn A (Flutter Mobile)

**Vùng code hoạt động:** chỉ trong `mobile-app/lib/screens/`, `mobile-app/lib/widgets/`, `mobile-app/lib/models/`, `mobile-app/lib/repositories/`, `mobile-app/lib/services/remote_api_helper.dart` (chỉ thêm hàm gọi API mới, không sửa hàm người khác đang dùng).

### Task A1 - Màn hình thiết lập ngân sách (Budget Screen) — P0
- Cho phép nhập số tiền hạn mức, chọn kỳ hạn Tuần/Tháng, chọn category (tùy chọn).
- Kết nối `BudgetModel` hiện có (`lib/models/budget_model.dart`) với API `/api/budgets` mới của Bạn B (CRUD) thay vì chỉ lưu SQLite local.
- Definition of Done: tạo/sửa/xóa budget qua API thật, dữ liệu hiển thị đồng bộ sau khi tạo lại app.

### Task A2 - Cảnh báo vượt ngưỡng ngân sách — P0/P1
- Logic hiển thị Toast/Dialog khi chi tiêu chạm 80% và 100% hạn mức (tuần và tháng), tính theo category nếu có.
- Nếu Bạn B có endpoint notification thì gọi để lấy cảnh báo từ server; nếu chưa kịp, tạm thời tính client-side dựa trên dữ liệu budget + tổng chi lấy về.
- Definition of Done: cảnh báo hiện đúng lúc vượt ngưỡng, không hiện lặp lại gây phiền (đánh dấu đã xem).

### Task A3 - Hoàn thiện báo cáo thói quen chi tiêu (nhỏ, vì phần lớn đã có) — P2
- Thêm bộ lọc khoảng thời gian tùy chọn cho màn hình biểu đồ đã có.
- Thêm nút refresh/gọi lại `/spade/analyze` khi đổi bộ lọc.
- Không cần dựng lại biểu đồ từ đầu, chỉ mở rộng UI hiện có.

### Task A4 - Màn hình sửa giao dịch (Edit Transaction) — P0
- Form sửa giao dịch (amount, category, note, date, payment method) gọi API `PUT /api/transactions/{id}` do Bạn B cung cấp.
- Definition of Done: sửa xong load lại danh sách, không tạo giao dịch trùng.

### Task A5 - Trạng thái đồng bộ giao dịch (bản gọn) — P1
- Đề cương yêu cầu tường minh "quản lý đồng bộ trạng thái và lưu trữ tập trung dữ liệu giao dịch lên Core Backend" (Chức năng 1, Yêu cầu 2). Không cần làm full offline-first, chỉ cần: thêm cờ trạng thái (đã lưu server / đang gửi / lỗi) cho giao dịch tạo trên mobile, hiển thị rõ cho user khi tạo giao dịch thất bại do mất mạng và cho phép gửi lại thủ công.
- Không cần queue phức tạp hay conflict resolution — nếu request lưu server lỗi thì báo lỗi ngay, không âm thầm lưu local rồi thôi.
- Definition of Done: tắt mạng khi tạo giao dịch → thấy trạng thái lỗi rõ ràng + nút "Gửi lại"; bật mạng lại, gửi lại thành công không bị tạo trùng.

## 3. Task cho Bạn B (Backend Java - Spring Boot)

**Vùng code hoạt động:** chỉ trong `core-backend/src/main/java/vn/edu/huit/finance/core/` (Controller, Service, Repository, Entity, DTO) và file cấu hình liên quan. Không sửa file Flutter.

### Task B1 - API đổi mật khẩu — P0
- Thêm `PUT /api/auth/change-password`: DTO (mật khẩu cũ, mật khẩu mới), lấy user từ JWT/SecurityContext (không nhận `userId` từ client), verify bằng `PasswordEncoder`, hash mật khẩu mới bằng BCrypt, ghi audit log.
- Definition of Done: test đổi mật khẩu đúng/sai mật khẩu cũ, test khi chưa đăng nhập bị từ chối.

### Task B2 - Budget backend (entity/API) — P0
- Tạo `Budget` entity, repository, service, controller: CRUD budget theo user, category, kỳ áp dụng (`periodType`: WEEK/MONTH, `startDate`, `endDate`).
- Ràng buộc không trùng budget cùng user/category/kỳ.
- Định nghĩa DTO trả về đúng field mà `RemoteApiHelper` (mobile) đang mong đợi khi gọi `/api/budgets` — đối chiếu với Task A1 trước khi code.
- Definition of Done: CRUD hoạt động qua Postman, có test ownership (user A không thấy budget của user B).

### Task B3 - API sửa giao dịch + vá lỗi phân quyền `userId` — P0
- Thêm `PUT /api/transactions/{id}`: kiểm tra ownership bằng user trong JWT, cập nhật atomically wallet balance khi đổi amount/type.
- Rà và sửa các route `/filter`, `/popular-vendors`, `/app-dashboard` đang nhận `userId` qua query param — chuyển sang lấy user từ SecurityContext, chỉ admin mới được truy vấn cross-user (kèm `@PreAuthorize`).
- Sửa `UserService.normalizeRole`/`toggleUserStatus`: đang so sánh chuỗi `"ADMIN"` trong khi role lưu dạng `"ROLE_ADMIN"` — chuẩn hóa về một cách so sánh duy nhất.
- Definition of Done: test user A gọi API bằng userId của user B phải bị từ chối/không trả dữ liệu; test toggle status hoạt động đúng với admin thật.

### Task B4 - Chuẩn hóa Regex 4 trường + quy trình đối soát hóa đơn — P0/P1 (đề cương chấm điểm trực tiếp mục này)
- Chuẩn hóa Regex: gom logic parse hiện đang rải rác (`ai_service.py` clean text, `TransactionService` parse ngày) thành một bước hậu xử lý rõ ràng ở Core Backend cho đúng 4 trường Seller, Address, Timestamp, Total Cost — trim khoảng trắng/ký tự thừa, parse ngày theo nhiều định dạng (`dd/MM/yyyy`, `yyyy-MM-dd`), parse số tiền theo locale Việt Nam (dấu chấm/phẩy), không tự set `DateTime.now()` khi parse thất bại mà phải trả lỗi rõ cho client.
- Quy trình đối soát: thêm trạng thái rõ ràng cho receipt (`PROCESSING`, `NEEDS_REVIEW`, `CONFIRMED`, `FAILED`) thay vì set `SUCCESS` ngay khi lưu; lưu riêng dữ liệu OCR gốc và dữ liệu user đã sửa; so sánh total OCR với amount cuối cùng do user xác nhận; kiểm tra ownership receipt/transaction bằng user trong JWT.
- Definition of Done: unit test cho các case ngày/tiền hợp lệ và lỗi; test receipt chuyển đúng trạng thái qua từng bước; user A không xem/sửa được receipt của user B.
- Bằng chứng hiện trạng: `ai-service/ai_service.py`, `core-backend/.../Service/TransactionService.java`, `core-backend/.../Controller/ReceiptController.java` (xem chi tiết mục 3.8, 3.9 trong file review).

### Task B5 (stretch, làm nếu còn thời gian) - Notification budget + logout token revoke — P2
- API notification khi vượt ngưỡng budget (để Task A2 dùng thay vì tính client-side).
- Revoke/blacklist refresh token khi logout hoặc đổi mật khẩu.

## 4. Thứ tự phối hợp đề xuất

1. Bạn B chốt DTO cho Budget (`/api/budgets`) và Transaction update (`/api/transactions/{id}`) trước, báo cho Bạn A field cụ thể (kể cả khi backend chưa code xong, có thể gửi JSON mẫu).
2. Bạn A bắt đầu Task A1/A4 song song với JSON mẫu, Bạn B code Task B1-B3 rồi tới B4 (Regex + đối soát — không phụ thuộc Bạn A, có thể làm song song từ đầu).
3. Khi Bạn B push xong nhánh backend, Bạn A trỏ `remote_api_helper.dart` sang API thật và test lại.
4. Task A2/A3/A5/B5 làm sau khi phần P0 hai bên đã xong và được Trí duyệt merge.

## 5. Quy trình Git bắt buộc

Repo hiện tại **chỉ có nhánh `main`** (chưa có nhánh `dev`). Vì vậy dùng `main` làm nhánh gốc thay vì `dev`. Tuyệt đối không code và push thẳng lên `main`.

```
git checkout main
git pull origin main
git checkout -b feature/ten-tinh-nang-cua-ban
# code trong đúng vùng code đã phân công ở trên
git add <file cụ thể>      # không dùng "git add -A" hoặc "git add ."
git commit -m "mô tả ngắn gọn"
git push origin feature/ten-tinh-nang-cua-ban
# mở Pull Request vào main, báo Trí review trước khi merge
```

**Quy ước tên nhánh:**
- Bạn A: `feature/mobile-budget-screen`, `feature/mobile-transaction-edit`, `feature/mobile-report-filter`...
- Bạn B: `feature/backend-change-password`, `feature/backend-budget-api`, `feature/backend-transaction-update`, `feature/backend-receipt-reconciliation`...

**Checklist trước khi mở PR:**
- Chỉ đổi file trong đúng vùng code được giao (không đụng file của người kia).
- Build/run thử được (Flutter: `flutter analyze` không lỗi mới; Backend: `mvn -q -DskipTests package` hoặc chạy app local).
- Mô tả PR nêu rõ task nào trong file này đã hoàn thành, kèm cách test thủ công.
