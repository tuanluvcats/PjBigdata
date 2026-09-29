# 🚀 TỔNG HỢP KẾ HOẠCH ĐỒ ÁN: BIG DATA & NOSQL
*Tích hợp vào Hệ thống Quản lý Tài chính Cá nhân (Polyglot Persistence Architecture)*

---

## 🌟 TỔNG QUAN KIẾN TRÚC HỆ THỐNG (SYSTEM ARCHITECTURE)
Dự án áp dụng kiến trúc **Polyglot Persistence** (Đa cơ sở dữ liệu) chuẩn Enterprise (giống MoMo, ZaloPay), kết hợp luồng phân tích Dữ liệu lớn (Big Data).

*   **PostgreSQL (Core Business):** Quản lý định danh User, Số dư ví, và 4 trường cốt lõi của hóa đơn (Tên cửa hàng, Địa chỉ, Ngày giờ, Tổng tiền). Đảm bảo tính toàn vẹn dữ liệu (ACID).
*   **MongoDB - NoSQL (AI Raw Data & Logs):** Hứng toàn bộ dữ liệu thô (chuỗi JSON khổng lồ chứa raw text, tọa độ bounding box, confidence score) từ mô hình AI DONUT. Cô lập rủi ro phình to dữ liệu, bảo vệ hiệu suất cho luồng thanh toán chính.
*   **Apache Spark & HDFS - Big Data (Analytics):** Đọc dữ liệu giao dịch kết hợp dữ liệu mở để chạy thuật toán Machine Learning (Isolation Forest/Random Forest), trả về kết quả cảnh báo gian lận/chi tiêu bất thường theo thời gian thực.

---

## 🎯 PHẦN 1: ĐỒ ÁN MÔN NHẬP MÔN BIG DATA
**Hướng đi:** Tích hợp tính năng Cảnh báo chi tiêu/gian lận cho app Flutter.

### 1.1. Công thức "Nhanh & Điểm Cao"
*   **Công nghệ lõi:** Sử dụng HDFS để lưu trữ và Apache Spark (Spark MLlib) để xử lý tính toán nặng. Không dùng backend Java thông thường để phân tích.
*   **Luồng xử lý (Pipeline):** Trình bày rõ kiến trúc Data Pipeline (từ tập dữ liệu -> HDFS -> Spark xử lý mất cân bằng dữ liệu -> Model ML -> API).
*   **Trực quan hóa:** Dùng trực tiếp App Flutter làm frontend hiển thị cảnh báo đẩy (Push Notification) hoặc Dashboard giao dịch nghi vấn.
*   **Tiêu chuẩn báo cáo:** Tuân thủ đúng 10 mục bắt buộc của cấu trúc báo cáo Word (từ trang bìa đến phụ lục), nộp đủ source code và slide.

### 1.2. Thông tin điền Form Đăng Ký Đề Tài
*   **Tên đề tài dự kiến:** Phát hiện giao dịch tài chính bất thường (Tích hợp tính năng cảnh báo rủi ro cho hệ thống quản lý tài chính cá nhân).
*   **Mô tả ngắn về đề tài:** Đề tài tập trung giải quyết bài toán phát hiện các giao dịch đáng ngờ, gian lận hoặc chi tiêu vượt ngưỡng bất thường. Nhóm sẽ sử dụng bộ dữ liệu giao dịch thẻ tín dụng/ngân hàng đã ẩn danh. Dữ liệu được lưu trữ trên HDFS, sau đó sử dụng Apache Spark để tiền xử lý và giải quyết bài toán mất cân bằng dữ liệu. Các thuật toán Machine Learning như Isolation Forest hoặc Random Forest sẽ được áp dụng để phân loại giao dịch. Kết quả mong muốn là một API kết nối với hệ thống quản lý tài chính cá nhân để trực tiếp cảnh báo rủi ro cho người dùng theo thời gian thực.
*   **Dataset / Nguồn dữ liệu dự kiến:** Dữ liệu công khai từ Kaggle (Credit Card Fraud Detection dataset) kết hợp cấu trúc dữ liệu mô phỏng từ hệ thống hóa đơn thực tế.
    *   **Link Dataset:** [https://www.kaggle.com/datasets/mlg-ulb/creditcardfraud](https://www.kaggle.com/datasets/mlg-ulb/creditcardfraud)
*   **Yếu tố Big Data của đề tài là gì?**
    *   **Volume & Variety:** Phân tích khối lượng dữ liệu giao dịch tài chính lớn (hàng trăm nghìn records trở lên) với nhiều chiều đặc trưng phức tạp.
    *   **Xử lý phân tán:** Giải quyết bài toán tính toán nặng và dữ liệu mất cân bằng (imbalanced data) bằng kỹ thuật xử lý phân tán của Apache Spark.
    *   **Lưu trữ:** Lưu trữ tập dữ liệu lớn trên nền tảng phân tán HDFS.
*   **Kết quả / Sản phẩm dự kiến của nhóm:**
    *   Pipeline xử lý dữ liệu hoàn chỉnh từ HDFS sang Spark.
    *   Mô hình phát hiện giao dịch bất thường đạt chuẩn, đánh giá qua Precision, Recall, F1-score.
    *   Giao diện trực quan hóa các giao dịch nghi vấn tích hợp trên app Flutter.

---

## 🎯 PHẦN 2: ĐỒ ÁN MÔN DỮ LIỆU NOSQL
**Hướng đi:** Thiết lập MongoDB Document-based để quản lý dữ liệu bóc tách từ hóa đơn (Model AI DONUT).

### 2.1. Thông tin Đề tài
*   **Tên đề tài chốt (Ngắn gọn):** Ứng dụng MongoDB quản lý dữ liệu trích xuất hóa đơn AI.
*   **Tên đề tài (Bản đầy đủ nếu cần):** Thiết kế CSDL Document-based lưu trữ dữ liệu hóa đơn cho hệ thống quản lý tài chính.
*   **Công nghệ sử dụng:** MongoDB, Spring Data MongoDB (Java Boot).

### 2.2. Chiến thuật "Hack Điểm" (Theo barem môn NoSQL)
*   **Tích hợp sâu ứng dụng (Cộng 2 điểm mở rộng):** Không cần mất thời gian tìm hiểu GUI Tool lạ (như CouchDB, ArangoDB). Giữ nguyên MongoDB, nhưng tập trung "Show-off" việc kết nối mượt mà hệ thống CSDL vào Backend Spring Boot và Flutter.
*   **Nghiệp vụ thực tế:** Demo thao tác CRUD trực tiếp qua App, Import/Export/Backup dữ liệu đầy đủ.
*   **Lợi thế dữ liệu sẵn có:** Sử dụng chính tập dữ liệu ảnh hóa đơn thực tế đã thu thập được để làm data demo, không cần phải mock (giả lập) dữ liệu ảo.

### 2.3. Luồng xử lý Code (Code Pipeline)
1.  Flutter gửi ảnh hóa đơn `->` API Spring Boot.
2.  Spring Boot đẩy qua model DONUT bóc tách.
3.  Nhận về JSON kết quả. Spring Boot thực hiện 2 tác vụ:
    *   **(Lưu luồng lõi):** Lưu 4 trường cơ bản (Tên, Địa chỉ, Ngày, Tiền) vào PostgreSQL.
    *   **(Lưu luồng AI):** Bất đồng bộ (Asynchronous) lưu toàn bộ JSON raw (text, bounding box, confidence score) vào collection `ocr_logs` của MongoDB.
4.  **Thiết lập khóa ngoại ảo:** Dùng `ocr_log_id` (Lưu bên Postgres) trỏ tới `_id` của Document bên MongoDB để dễ dàng đối chiếu khi truy vấn.

---

## 💡 PHẦN 3: KỊCH BẢN BẢO VỆ & PHỎNG VẤN (DEFENSE STRATEGY)
*(Sử dụng khi giảng viên hỏi "Tại sao dùng cả 2 CSDL?" hoặc khi đi phỏng vấn thực tập Backend)*

**❓ Câu hỏi:** *"Hệ thống đã có PostgreSQL để lưu dữ liệu, tại sao lại dùng thêm MongoDB làm gì cho phức tạp? Có phải em đang nhồi nhét công nghệ không?"*

**✅ Kịch bản trả lời chuẩn Senior:**

> "Dạ không, đây là sự phân tách nghiệp vụ bắt buộc (Separation of Concerns). Ứng dụng của nhóm em áp dụng kiến trúc Polyglot Persistence tương tự các hệ thống ví điện tử lớn hiện nay."

*   **Bảo vệ Core Business:** Các thông tin như Tổng tiền, Số dư ví đòi hỏi tính toàn vẹn dữ liệu (ACID) tuyệt đối. Em dùng PostgreSQL để quản lý luồng thanh toán này.
*   **Xử lý "Dữ liệu linh hoạt" (Trade-off):** Đầu ra của mô hình AI DONUT là một cục JSON khổng lồ chứa raw text, tọa độ bounding box, và độ tin cậy. Nếu insert liên tục cục JSON này vào bảng Transaction của PostgreSQL, nó sẽ làm phình to ổ cứng, tốn tài nguyên RDBMS và làm chậm các câu query tính tiền.
*   **Future-proofing (Sẵn sàng mở rộng):** Dùng MongoDB (Schema-less) như một Data Lake thu nhỏ giúp em hứng trọn vẹn cục dữ liệu thô này để phục vụ Audit (truy vết khi AI nhận diện sai). Đồng thời, đây chính là kho dữ liệu chuẩn bị sẵn để đổ vào cụm Apache Spark phân tích chuyên sâu cho module Big Data mà không làm ảnh hưởng đến luồng giao dịch cốt lõi của người dùng.

---
*📌 **Lưu ý:** Bạn hãy lưu file này lại, gửi các phần thông tin đăng ký cho Giảng viên hướng dẫn môn học để chốt đề tài sớm nhé!*