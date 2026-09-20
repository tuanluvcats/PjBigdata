# Review chức năng/code - CNTT-KLCN175

**Đối tượng:** `CNTT-KLCN175-Phung The Bao.docx` so với code hiện tại trong `core-backend`, `mobile-app`, `ai-service`  
**Phạm vi:** Chỉ kiểm tra chức năng và luồng code. Không đánh giá thiết kế hệ thống, kiến trúc microservices, chất lượng UI/UX, dữ liệu huấn luyện hoặc nghiên cứu mô hình AI.  
**Ngày review:** 2026-09-15

## 1. Kết luận nhanh

Project đã có nền tảng cho đăng nhập JWT, quản lý giao dịch cơ bản, Web Admin, biểu đồ mobile và luồng quét hóa đơn bằng DONUT. Tuy nhiên để khớp đầy đủ yêu cầu chức năng trong đề cương, các khoảng thiếu quan trọng là:

1. Đổi mật khẩu trên môi trường thật chưa hoạt động vì backend thiếu API `/api/auth/change-password`.
2. Quản lý ngân sách chưa được lưu tập trung: mobile có SQLite model nhưng backend chưa có Budget entity/repository/controller.
3. Chưa có ngân sách tuần và hệ thống cảnh báo vượt hạn mức theo tuần/danh mục qua notification.
4. Giao dịch chưa có chức năng sửa; đồng bộ offline/local với backend chưa được triển khai.
5. Một số API đang nhận `userId` từ client, có thể đọc dữ liệu của user khác nếu không chặn ở service/controller.
6. Luồng receipt mới là OCR -> người dùng xem -> lưu giao dịch; chưa có đối soát trạng thái đầy đủ và kiểm tra ownership receipt.
7. Mobile gọi một số endpoint chưa thấy backend triển khai: `/api/budgets`, `/api/notifications`, `/api/wallets`, `/api/auth/change-password`, và `/api/transactions` dạng query.

## 2. Bảng đối chiếu tổng quát

| Nhóm chức năng             | Trạng thái       | Nhận xét                                                                                                                                                                |
| -------------------------- | ---------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Đăng nhập                  | Đã có            | Backend cấp access/refresh JWT; mobile tự refresh token.                                                                                                                |
| Đăng xuất                  | Có một phần      | Mobile chỉ xóa session local; JWT cũ chưa bị revoke ở backend. Admin có logout form.                                                                                    |
| Đổi mật khẩu               | Thiếu production | Mobile có gọi API nhưng backend không có endpoint tương ứng; bản demo chỉ sửa SQLite local.                                                                             |
| Phân quyền                 | Có một phần      | Có `ROLE_USER`, `ROLE_ADMIN`, JWT và `@PreAuthorize`; guard trong `toggleUserStatus` lại so sánh `ADMIN` với role thực tế `ROLE_ADMIN`, cần sửa và rà lại mọi endpoint. |
| Giao dịch thêm/đọc/xóa     | Có một phần      | Backend có create/read/delete; chưa có update giao dịch.                                                                                                                |
| Giao dịch phân loại        | Có một phần      | Có category name/category ID khi tạo; mobile chỉ đọc danh mục, chưa có CRUD cho người dùng.                                                                             |
| Đồng bộ dữ liệu tập trung  | Có một phần      | Tạo giao dịch online có; chưa có offline-to-server sync, retry, conflict resolution hoặc deduplication.                                                                 |
| Ngân sách tháng            | Có một phần      | Có SQLite budget và kiểm tra tổng chi ở mobile; chưa có backend persistence/API.                                                                                        |
| Ngân sách tuần             | Thiếu            | Chưa có model, API hoặc dữ liệu lưu cho hạn mức theo tuần.                                                                                                              |
| Cảnh báo vượt hạn mức      | Có một phần      | Có cảnh báo local khi tổng chi tháng vượt budget; chưa có cảnh báo theo danh mục/tuần và notification backend.                                                          |
| Admin quản lý user         | Đã có            | Có tạo, sửa, xóa, khóa/mở khóa tài khoản.                                                                                                                               |
| Admin quản lý category     | Đã có            | Có CRUD qua route `/admin/ajax/categories`. Mobile public API hiện chỉ GET.                                                                                             |
| Admin log hoạt động        | Có một phần      | Có ActionLog, login log và trang audit; chưa bảo đảm mọi thao tác đều ghi log, một số lỗi bị nuốt.                                                                      |
| Admin thống kê tổng quan   | Đã có            | Có volume, user count, transaction count, top spenders, chart 6 tháng và receipt status.                                                                                |
| Biểu đồ mobile             | Đã có            | Có biểu đồ tuần/tháng bằng `fl_chart`.                                                                                                                                  |
| Báo cáo thói quen chi tiêu | Có một phần      | Có trend, top category, weekly comparison, WCM-SPADE và budget advice; chưa có báo cáo lưu/xuất riêng.                                                                  |
| Camera/thư viện ảnh        | Đã có            | Mobile có camera/document scanner và gallery picker.                                                                                                                    |
| Ảnh -> AI -> kết quả       | Đã có            | Mobile gọi `/donut/scan`; FastAPI nhận ảnh và trả JSON Donut.                                                                                                           |
| Review kết quả OCR         | Có một phần      | Có sửa seller/date/category; chưa cho sửa đầy đủ line items/tax và chưa có validation field chặt.                                                                       |
| Đối soát và lưu receipt    | Có một phần      | Có lưu transaction + receipt sau khi user xác nhận; receipt bị đánh dấu `SUCCESS` ngay, chưa có workflow đối soát rõ ràng.                                              |
| Regex chuẩn hóa 4 trường   | Có một phần      | Có làm sạch text, tách total và parse date; chưa có contract normalization thống nhất cho seller/address/timestamp/total.                                               |

## 3. Các chức năng còn thiếu hoặc cần hoàn thiện

### P0 - Cần xử lý trước khi nghiệm thu

#### 3.1. Đổi mật khẩu trên backend

**Hiện trạng:** Mobile gọi `PUT /api/auth/change-password`, nhưng `AuthController` không có route này. Trong chế độ full, màn hình tài khoản thông báo chưa hỗ trợ server; chỉ bản demo mới đổi mật khẩu trong SQLite.

**Cần bổ sung:**

- DTO gồm mật khẩu hiện tại, mật khẩu mới và xác nhận nếu cần.
- Xác định user từ JWT, không nhận `userId` tùy ý từ client.
- Kiểm tra mật khẩu cũ bằng `PasswordEncoder`.
- Hash mật khẩu mới bằng BCrypt.
- Ghi audit log và buộc refresh session/token nếu policy yêu cầu.
- Bổ sung test cho mật khẩu cũ sai, user chưa đăng nhập và đổi thành công.

**Bằng chứng:** `core-backend/.../Controller/AuthController.java`, `mobile-app/lib/services/remote_api_helper.dart`, `mobile-app/lib/screens/profile/account_credentials_screen.dart`.

#### 3.2. Quản lý budget tập trung trên Core Backend

**Hiện trạng:** Flutter có `BudgetModel`, bảng SQLite và `getBudgetsByUserIdAndMonth`, nhưng backend không có Budget entity, repository hay controller. `RemoteApiHelper` gọi `/api/budgets` nhưng không thấy route server tương ứng.

**Cần bổ sung:**

- Budget entity/repository/service/controller.
- CRUD và truy vấn budget theo user, category, kỳ áp dụng.
- Ràng buộc không trùng budget cùng user/category/kỳ.
- Backend là nguồn dữ liệu chính; mobile chỉ cache/hiển thị.
- Xử lý update/delete và refresh dữ liệu sau khi tạo.

**Bằng chứng:** `mobile-app/lib/models/budget_model.dart`, `mobile-app/lib/services/sqflite_helper.dart`, `mobile-app/lib/services/remote_api_helper.dart`; không có lớp Budget tương ứng trong `core-backend/src/main/java`.

#### 3.3. Sửa giao dịch

**Hiện trạng:** Backend có `POST /api/transactions/checkout`, `GET /mine` và `DELETE /{id}` nhưng không có `PUT/PATCH` để sửa giao dịch. Yêu cầu đề cương yêu cầu thêm, sửa, xóa và phân loại giao dịch.

**Cần bổ sung:**

- API update transaction.
- Kiểm tra ownership bằng user trong JWT.
- Cập nhật atomically wallet balance khi đổi amount/type.
- Cho phép đổi category, note, date, payment method và receipt metadata hợp lệ.
- Mobile thêm form edit và đồng bộ sau khi lưu.

**Bằng chứng:** `core-backend/.../Controller/TransactionController.java`, `core-backend/.../Service/TransactionService.java`.

#### 3.4. Sửa lỗi phân quyền do tin `userId` từ request

**Hiện trạng:** Các route `/filter`, `/popular-vendors`, `/app-dashboard` nhận `userId` bằng query parameter, trong khi một số route khác đã lấy user từ SecurityContext. Client có thể thay userId để thử đọc dữ liệu của user khác.

**Cần xử lý:**

- Bỏ `userId` khỏi input của endpoint dành cho user hiện tại.
- Lấy user từ JWT/SecurityContext trong service.
- Chỉ cho phép admin dùng API thống kê cross-user riêng biệt và có `@PreAuthorize`.
- Viết test bảo đảm user A không đọc được dữ liệu của user B.

**Bằng chứng:** `core-backend/.../Controller/TransactionController.java`.

Ngoài lỗi nhận `userId`, `UserService.normalizeRole` lưu role theo dạng `ROLE_ADMIN` nhưng `toggleUserStatus` kiểm tra chuỗi `ADMIN`. Vì vậy điều kiện bảo vệ không khớp role thực tế và cần được chuẩn hóa về một cách so sánh duy nhất.

**Bằng chứng:** `core-backend/src/main/java/vn/edu/huit/finance/core/Service/UserService.java`.

### P1 - Cần hoàn thiện để đáp ứng đủ nghiệp vụ

#### 3.5. Ngân sách tuần và cảnh báo vượt ngưỡng

**Hiện trạng:** Mobile chỉ có budget tháng local và so sánh tổng chi tháng. Việc chia dữ liệu thành 4 khoảng ngày trong biểu đồ không phải ngân sách tuần độc lập.

**Cần bổ sung:**

- `periodType` hoặc model riêng cho WEEK/MONTH.
- Kỳ bắt đầu/kết thúc rõ ràng, không suy ra bằng số ngày cố định.
- Tính chi tiêu theo category và kỳ.
- Cảnh báo ở các mốc như 80%, 100% và khi vượt hạn mức.
- API notification/in-app notification; tránh chỉ hiển thị một banner sau khi mở dashboard.
- Đánh dấu đã đọc và lịch sử cảnh báo nếu yêu cầu nghiệp vụ cần truy vết.

**Bằng chứng:** `mobile-app/lib/screens/dashboard/dashboard_page.dart`, `mobile-app/lib/services/remote_api_helper.dart`; chưa có backend `/api/budgets` hoặc `/api/notifications`.

#### 3.6. Đồng bộ offline/local với backend

**Hiện trạng:** App có hai nhánh `AppConfig.isFull`: online dùng REST, demo dùng SQLite. Chưa có hàng đợi pending sync, trạng thái đồng bộ, retry hoặc xử lý xung đột. Vì vậy dữ liệu nhập khi offline không tự lên Core Backend.

**Cần bổ sung:**

- Local record có `syncStatus`, `clientId`, `updatedAt`.
- Queue các thao tác create/update/delete.
- API idempotency hoặc client ID để chống tạo trùng khi retry.
- Job sync khi có mạng, retry có giới hạn và hiển thị lỗi cho user.
- Quy tắc conflict rõ ràng giữa local và server.

**Bằng chứng:** `mobile-app/lib/repositories/app_repository.dart`, `mobile-app/lib/services/sqflite_helper.dart`, `mobile-app/lib/services/remote_api_helper.dart`.

#### 3.7. Logout và vòng đời token

**Hiện trạng:** Logout mobile gọi `SessionService.clearSession`, nhưng backend không có revoke/blacklist refresh token. Access token còn dùng được đến hạn.

**Cần cân nhắc triển khai:**

- Lưu/rotate refresh token phía server hoặc blacklist token đã revoke.
- API logout nhận refresh token/current session.
- Xóa local token sau khi server xử lý, kể cả khi request thất bại.
- Thu hồi tất cả session khi đổi mật khẩu nếu yêu cầu bảo mật.

#### 3.8. Hoàn chỉnh receipt reconciliation

**Hiện trạng:** Flow hiện tại là chọn/chụp ảnh -> gọi AI -> sửa seller/date/category -> gửi transaction checkout. Backend tạo Receipt và đặt `SUCCESS` ngay trong lúc lưu transaction. Đây là xác nhận của user, nhưng chưa có state machine đối soát riêng.

**Cần bổ sung:**

- Trạng thái rõ ràng: `PROCESSING`, `NEEDS_REVIEW`, `CONFIRMED`, `FAILED`, `CANCELED`.
- Lưu dữ liệu OCR raw và dữ liệu đã user chỉnh sửa riêng.
- So sánh total OCR với amount cuối cùng và ghi nhận chênh lệch.
- Kiểm tra receipt/transaction ownership bằng authenticated user.
- Hoàn thiện feedback endpoint để không nhận `userId` mặc định hoặc userId tùy ý.
- Mobile gọi feedback/retry khi OCR sai hoặc thất bại.

**Bằng chứng:** `mobile-app/lib/screens/receipt_scanner/review_form_page.dart`, `core-backend/.../Service/TransactionService.java`, `core-backend/.../Controller/ReceiptController.java`.

#### 3.9. Chuẩn hóa và validate kết quả OCR

**Hiện trạng:** AI có `clean_vietnamese_text` và chuẩn hóa số tiền; backend có regex parse một số định dạng ngày giờ. Các bước nằm rải rác và không tạo một response contract chặt cho đủ Seller, Address, Timestamp, Total Cost.

**Cần bổ sung:**

- DTO/schema cố định cho 4 trường, kèm confidence hoặc trạng thái thiếu dữ liệu nếu cần.
- Chuẩn hóa seller/address: trim whitespace, ký tự thừa và null/blank.
- Chuẩn hóa timestamp theo một timezone/format thống nhất.
- Parse total theo locale Việt Nam, kiểm tra số âm và giới hạn hợp lý.
- Không tự dùng `DateTime.now()` khi parse thất bại mà không báo cho user.
- Unit test cho ngày `dd/MM/yyyy`, `yyyy-MM-dd`, dấu chấm/phẩy tiền và OCR thiếu trường.

**Bằng chứng:** `ai-service/ai_service.py`, `core-backend/.../Service/TransactionService.java`, `mobile-app/lib/screens/receipt_scanner/review_form_page.dart`.

### P2 - Nên bổ sung để hoàn thiện chức năng

#### 3.10. Báo cáo thói quen chi tiêu

Hiện đã có insight cards, biểu đồ tuần, trend tháng, top category, WCM-SPADE và budget advice. Phần còn thiếu so với một “báo cáo” hoàn chỉnh:

- Bộ lọc thời gian tùy chọn.
- Lịch sử báo cáo theo kỳ.
- Tổng hợp thu/chi theo category và tỷ lệ phần trăm ổn định.
- Xuất/chia sẻ báo cáo nếu đây là yêu cầu trình bày nghiệp vụ.
- Backend endpoint thống kê để kết quả không phụ thuộc toàn bộ dữ liệu tải về mobile.

#### 3.11. Admin activity log

Admin đã có trang audit log và summary. Nên rà lại để bảo đảm mọi thao tác tạo/sửa/xóa giao dịch, category, user, receipt và login đều có log; không nuốt exception khi ghi log; bổ sung filter theo thời gian/action/user và paging nếu số log lớn.

#### 3.12. CRUD category ở mobile

Đề cương yêu cầu quản trị category mặc định, phần Admin đã có CRUD. Nếu phạm vi chỉ là admin thì đã đáp ứng. Nếu user cũng được phép tạo category cá nhân, cần thêm API ownership và màn hình CRUD mobile; hiện `CategoryApiController` chỉ có `GET /api/categories`.

## 4. Những phần đã đáp ứng, không cần làm lại

- Đăng nhập email/password, social login và refresh JWT.
- Đăng ký tài khoản và đặt lại mật khẩu qua OTP.
- Admin dashboard có thống kê tổng quan.
- Admin quản lý user và category.
- Tạo giao dịch thủ công và xóa giao dịch có kiểm tra ownership.
- Camera/gallery/document scanner cho hóa đơn.
- Gọi AI service `/donut/scan` và hiển thị form review cơ bản.
- Lưu raw OCR, image URL, transaction và receipt ở flow quét hóa đơn.
- Biểu đồ mobile và một số phân tích thói quen chi tiêu.
- Regex làm sạch text, parse total và parse ngày giờ ở các lớp hiện có.

## 5. Thứ tự triển khai đề xuất

1. Bổ sung change-password backend và sửa toàn bộ endpoint transaction để lấy user từ JWT.
2. Xây dựng Budget backend, kết nối lại Flutter và thêm weekly budget.
3. Thêm update transaction và cơ chế sync offline có idempotency.
4. Hoàn thiện notification/cảnh báo budget.
5. Chuẩn hóa receipt reconciliation, ownership và OCR response schema.
6. Bổ sung test integration cho auth, transaction ownership, budget và receipt flow.
7. Hoàn thiện report history/export và các bộ lọc admin nếu còn thời gian.

## 6. Ghi chú phương pháp

Đây là static code review đối chiếu source hiện tại với yêu cầu chức năng trong file đề cương. Chưa chạy full stack hoặc kiểm thử API end-to-end; các kết luận về endpoint “chưa có” dựa trên việc rà toàn bộ controller/backend hiện có và đối chiếu với các URL mobile đang gọi.
