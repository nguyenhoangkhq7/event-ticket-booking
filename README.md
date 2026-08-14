# 🎫 Concert Ticket Booking Platform

Hệ thống đặt vé concert trực tuyến xử lý high-concurrency, được thiết kế để chịu tải Flash Sale với 300–500+ booking requests/phút mà không xảy ra overselling, duplicate booking hay lạm dụng voucher.

---

## 📋 Mục Lục

- [Tech Stack](#-tech-stack)
- [Kiến Trúc Dự Án](#-kiến-trúc-dự-án)
- [Hướng Dẫn Cài Đặt & Chạy Local](#-hướng-dẫn-cài-đặt--chạy-local)
- [Swagger / OpenAPI Documentation](#-swagger--openapi-documentation)
- [Postman Collection](#-postman-collection)
- [Hướng Dẫn Chạy Test](#-hướng-dẫn-chạy-test)
- [Tài Liệu Chi Tiết](#-tài-liệu-chi-tiết)

---

## 🛠 Tech Stack

| Công nghệ | Phiên bản | Vai trò |
|---|---|---|
| **Java** | 21 | Ngôn ngữ chính |
| **Spring Boot** | 4.1.0 | Application framework |
| **Spring Security** | — | Authentication & Authorization (JWT) |
| **Spring Data JPA** | — | ORM layer (Hibernate + HikariCP) |
| **PostgreSQL** | 17 | Relational database (source of truth) |
| **Redis** | 7 | Atomic inventory counter, cache layer |
| **Flyway** | — | Database migration & versioning |
| **Caffeine** | — | Local in-memory cache (concerts, categories) |
| **Bucket4j** | 8.10.1 | In-memory rate limiting (token bucket) |
| **MapStruct** | 1.5.5 | Object mapping (DTO ↔ Entity) |
| **Lombok** | — | Boilerplate code generation |
| **JJWT** | 0.12.3 | JWT token generation & validation |
| **SpringDoc OpenAPI** | 2.8.5 | OpenAPI 3.0 & Swagger UI documentation |
| **JUnit 5** | — | Unit testing framework |
| **Mockito** | — | Mocking framework |
| **Testcontainers** | 1.19.7 | Integration testing (PostgreSQL container) |
| **JMeter** | 3.8.0 (Maven plugin) | Load & performance testing |
| **Docker** | Multi-stage build | Containerization (eclipse-temurin:21) |

---

## 📦 Kiến Trúc Dự Án

Hệ thống được tổ chức theo kiến trúc **Modular Monolith** — các module nghiệp vụ được tách biệt bằng package trong cùng một ứng dụng Spring Boot:

```
com.geekup.eventticketbookingservice/
├── auth/               # Xác thực: đăng ký, đăng nhập
├── booking/            # Vòng đời đặt vé: tạo, thanh toán, hết hạn
├── catalog/            # Danh mục: concert (hỗ trợ Pageable), loại vé, tồn kho
├── common/             # Cross-cutting: ApiResponse, ErrorCode, Exception, RateLimiting, Cache, OpenApi, Auditing
├── inventory/          # Redis-based inventory counter (chống overselling)
├── operation/          # API quản trị: quản lý concert, booking, voucher
├── security/           # JWT filter, SecurityConfig
├── user/               # User entity, Role enum (CUSTOMER, ADMIN)
└── voucher/            # Voucher: validation, redemption, chống lạm dụng
```

### Tính năng cốt lõi & Chống gian lận Flash Sale

| Cơ chế | Mô tả |
|---|---|
| **Two-Tier Inventory Control** | Redis `DECRBY` pre-filter → PostgreSQL `SELECT FOR UPDATE` |
| **Idempotency Key** | HTTP header + DB unique constraint `(user_id, idempotency_key)` |
| **Rate Limiting** | 5 booking/phút/user, 10 login/phút/IP (Bucket4j Token Bucket) |
| **Booking Auto-Expiration** | TTL 15 phút, scheduler giải phóng vé mỗi 60 giây |
| **Voucher Pessimistic Lock** | `SELECT FOR UPDATE` + DB unique index per user |
| **JPA Auditing** | `@CreatedDate` & `@LastModifiedDate` tự động tracking thời gian trên entities |
| **Concert Pagination** | `GET /api/concerts` hỗ trợ Spring Data `Pageable` (`page`, `size`, `sort`) |
| **Interactive API Docs** | Swagger UI (`/swagger-ui.html`) tích hợp sẵn JWT Authorization |

---

## 🚀 Hướng Dẫn Cài Đặt & Chạy Local

### Yêu cầu hệ thống

- **Docker** & **Docker Compose** (bắt buộc)
- **Java 21** (nếu chạy ngoài Docker)

### ⚙️ Cấu Hình Môi Trường (`.env`)

Tạo file `.env` từ file mẫu `.env.example` đặt tại thư mục gốc của project (nếu chưa có):

```bash
# Linux / macOS
cp .env.example .env

# Windows (Command Prompt / PowerShell)
copy .env.example .env
```

Nội dung file `.env` (đã cấu hình sẵn các giá trị mặc định cho local):

```env
# Database configurations
POSTGRES_USER=ticket_user
POSTGRES_PASSWORD=ticket_password
POSTGRES_DB=event_ticket_db

# JWT Configurations
JWT_SECRET_KEY=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
JWT_EXPIRATION=3600000 # 1 hour (3600000ms)
```

---

### Cách A: Chạy Toàn Bộ Qua Docker (Khuyến nghị)

```bash
# 1. Build lại image mới nhất và khởi động toàn bộ hệ thống (PostgreSQL + Redis + Backend)
docker compose up -d --build

# 2. Kiểm tra trạng thái container
docker compose ps

# 3. Test API
curl http://localhost:8080/api/concerts
```

> **Ghi chú**: Cờ `--build` đảm bảo Docker luôn build lại backend image từ source code mới nhất thay vì dùng image cũ trong cache nếu trước đó đã từng build.

Hệ thống sẽ tự động:
- Khởi tạo PostgreSQL database và Redis
- Chạy Flyway migration để tạo schema
- Seed dữ liệu mẫu (users, concerts, vouchers)

---

### Cách B: Chạy Local cho Development

```bash
# 1. Khởi động infrastructure (PostgreSQL + Redis)
docker compose up -d postgres redis

# 2. Build và chạy ứng dụng
./mvnw spring-boot:run
```

Trên Windows:
```cmd
mvnw.cmd spring-boot:run
```

> Khi chạy local (Cách B), đảm bảo các biến môi trường trong `.env` được set trong shell hoặc IDE run configuration. Spring Boot sẽ đọc các biến `SPRING_DATASOURCE_*`, `SPRING_REDIS_*`, `JWT_SECRET_KEY` từ environment.

---

### Tài Khoản Seed (Mật khẩu: `password123`)

| Email | Role | Mô tả |
|---|---|---|
| `admin@eventticket.com` | `ADMIN` | Tài khoản quản trị |
| `customer1@example.com` | `CUSTOMER` | Khách hàng 1 |
| `customer2@example.com` | `CUSTOMER` | Khách hàng 2 |

### 🏷️ Mã Voucher Seed Mặc Định (Dùng test Swagger / Postman)

| Mã Voucher | Loại giảm giá | Giá trị | Tổng số lượt phát | Giới hạn / User | Trạng thái |
|---|---|---|---|---|---|
| `WELCOME50` | Giảm cố định (Fixed) | 50.000 VNĐ | 500 lượt | 1 lần / user | `ACTIVE` |
| `SUMMER2026` | Giảm phần trăm (Percentage) | 10% | 100 lượt | 1 lần / user | `ACTIVE` |
| `VIPFLASH20` | Giảm phần trăm (Percentage) | 20% | 50 lượt | 1 lần / user | `ACTIVE` |

### Cấu Hình Mặc Định

| Service | Host | Port |
|---|---|---|
| Backend API | localhost | 8080 |
| PostgreSQL | localhost | 5432 |
| Redis | localhost | 6379 |

---

## 📖 Swagger / OpenAPI Documentation

Hệ thống cung cấp giao diện tương tác và tài liệu API theo chuẩn OpenAPI 3.0:

- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON Spec**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### Hướng dẫn Test API qua Swagger UI:
1. Đăng nhập lấy Bearer token qua endpoint `POST /api/auth/login` (dùng tài khoản seed phía trên).
2. Nhấn nút **Authorize 🔓** ở góc trên bên phải màn hình Swagger UI và nhập token.
3. Test trực tiếp các endpoint đặt vé, voucher, danh mục concert.

### Pagination trên Catalog API:
- `GET /api/concerts` hỗ trợ phân trang chuẩn Spring Data:
  ```http
  GET /api/concerts?page=0&size=20&sort=startAt,asc
  ```
  Response trả về cấu trúc `ApiResponse<Page<ConcertResponse>>` gồm mảng dữ liệu `data.content` cùng các trường phân trang `totalElements`, `totalPages`, `size`, `number`.

---

## 📮 Postman Collection

Dự án cung cấp sẵn file Postman Collection đầy đủ các luồng Customer và Admin:

- **File collection**: [`postman/event-ticket-booking.postman_collection.json`](postman/event-ticket-booking.postman_collection.json)
- **Base URL mặc định**: `http://localhost:8080/api`

### Các bước test nhanh bằng Postman:
1. Mở Postman → Chọn **Import** → Kéo thả file `postman/event-ticket-booking.postman_collection.json`.
2. Chạy request `auth > login-customer` (hoặc `login-admin`):
   - Token sẽ được script tự động gán vào collection variable `{{access_token}}`.
3. Thực hiện các request tiếp theo trong collection theo thứ tự:
   - **Customer Flow**: `browse-concert` → `view-ticket-category` → `booking > create-booking` (tự động lưu `bookingId` & sinh `Idempotency-Key`) → `confirm-payment`.
   - **Operation Flow**: `create-concert` → `publish-concert` → `add-ticket-category` → `set-inventory` → `create-voucher`.

---

## 🧪 Hướng Dẫn Chạy Test

Dự án phân chia rõ ràng thành 3 cấp độ kiểm thử: **Unit Test**, **Integration Test** và **Performance / Load Test**.

---

### 1. Unit Tests (Kiểm thử đơn vị)
Kiểm tra logic nghiệp vụ độc lập bằng Mockito & JUnit 5 (không cần DB, không cần Docker, chạy siêu tốc):

```bash
# Chạy toàn bộ unit tests
./mvnw test

# Windows CMD / PowerShell
mvnw.cmd test

# Chạy một test class cụ thể
./mvnw test -Dtest=BookingServiceTest

# Chạy một method cụ thể
./mvnw test -Dtest="BookingServiceTest#testCreateBooking"
```

---

### 2. Integration Tests (Kiểm thử tích hợp với Testcontainers)
Kiểm thử tích hợp luồng dữ liệu thực tế với PostgreSQL.

> 🐳 **Yêu cầu:** Docker Desktop phải đang chạy. **Testcontainers** sẽ tự động khởi tạo container PostgreSQL độc lập và tự dọn dẹp sau khi test xong.

```bash
# Chạy toàn bộ Integration Tests
./mvnw test -Dtest="*IntegrationTest"

# Chạy một Integration Test cụ thể
./mvnw test -Dtest=ConcertIntegrationTest
```

---

### 3. Load & Performance Tests (Kiểm thử hiệu năng với JMeter)

> ⚠️ **LƯU Ý CỰC KỲ QUAN TRỌNG VỀ LỆNH `./mvnw verify`:**
> 
> Plugin `jmeter-maven-plugin` được gắn mặc định vào phase `verify` của Maven. Khi bạn chạy `./mvnw verify`, hệ thống sẽ **tự động kích hoạt bài test tải JMeter** (mặc định kéo dài 5 phút / 300s).
> 
> Nếu chỉ muốn chạy Unit/Integration Test thông thường, **KHÔNG NÊN** chạy `./mvnw verify` trần mà hãy dùng `./mvnw test` hoặc `./mvnw test -Dtest="*IntegrationTest"`.

#### 📋 Các bước chuẩn bị bắt buộc trước khi chạy Load Test (Pre-requisites):

1. **Khởi động Backend & Hạ tầng (chọn 1 trong 2 cách):**
   * **Cách A (Nếu chạy toàn bộ qua Docker):**
     ```bash
     docker compose up -d --build
     ```
     > 💡 *Lưu ý: Nếu bạn đã chạy lệnh này rồi thì cả PostgreSQL, Redis và Backend đã chạy sẵn ở cổng `8080`, **KHÔNG** cần chạy thêm `./mvnw spring-boot:run` để tránh lỗi xung đột cổng.*
   * **Cách B (Nếu chạy Local development với IDE / Maven):**
     ```bash
     # 1. Chỉ bật database và cache
     docker compose up -d postgres redis

     # 2. Chạy backend ngoài máy host
     ./mvnw spring-boot:run
     ```

2. **Import 50,000 Users & Voucher Test Tải vào Database:**
   *(Script SQL này sẽ nạp 50,000 users và các mã voucher tải cao `PERF10` giảm 10%, `PERF50K` giảm 50k với 100,000 lượt dùng dành riêng cho JMeter)*:
   ```bash
   # Linux / macOS / Git Bash
   docker exec -i event-ticket-postgres psql -U ticket_user -d event_ticket_db < jmeter/data/seed_50k_users.sql

   # Windows PowerShell
   Get-Content jmeter/data/seed_50k_users.sql | docker exec -i event-ticket-postgres psql -U ticket_user -d event_ticket_db

   # Windows CMD
   docker exec -i event-ticket-postgres psql -U ticket_user -d event_ticket_db < jmeter\data\seed_50k_users.sql
   ```

3. **Nạp tồn kho vé lên Redis (Pre-warming):**
   * **Khi chạy bài Test Đo Năng Suất / SLA (500 RPM trong 5 phút):**
     Nạp số lượng lớn để chu trình test chạy liên tục 5 phút mà không bị dừng do hết vé:
     ```bash
     docker exec -it event-ticket-redis redis-cli SET inventory:1 500000
     docker exec -it event-ticket-redis redis-cli SET inventory:2 500000
     docker exec -it event-ticket-redis redis-cli SET inventory:3 500000
     docker exec -it event-ticket-redis redis-cli SET inventory:4 500000
     ```
   * **Khi chạy bài Test Chống Bán Âm Vé (Zero Overselling Test):**
     Đặt tồn kho chỉ còn **50 vé** để thử thách 200 users cùng tranh mua trong 1ms:
     ```bash
     docker exec -it event-ticket-redis redis-cli SET inventory:1 50
     docker exec -i event-ticket-postgres psql -U ticket_user -d event_ticket_db -c "UPDATE ticket_inventory SET total_quantity = 50, reserved_quantity = 0, sold_quantity = 0 WHERE ticket_category_id = 1;"
     ```

#### 🚀 Thực thi Load Test:

* **Bài Test A — Đo Năng Suất & SLA (300 – 500 RPM liên tục):**
  * *Cách 1 (⭐ Khuyến nghị trên Windows / Local - Tự động mở HTML Report):*
    ```powershell
    .\jmeter\scripts\run_load_test.ps1 -BookingRpm 500 -Duration 300
    ```
  * *Cách 2 (Chuẩn hóa cho CI/CD Pipeline):*
    ```bash
    ./mvnw verify -Dsurefire.skip=true -Djmeter.booking_rpm=500 -Djmeter.duration=300
    ```
  * *Cách 3 (Docker - Không cần cài Java/JMeter trên máy):*
    ```bash
    docker compose -f docker-compose.jmeter.yml up
    ```

* **Bài Test B — Flash Sale Spike Test (200 users cùng tranh mua 50 vé trong 1ms):**
  ```bash
  jmeter -n -t jmeter/plans/flash_sale_spike_test.jmx -l jmeter/reports/spike_results.jtl -e -o jmeter/reports/spike_report -Jusers=200 -Jcategory_id=1 -Jcsv_file=jmeter/data/users_tokens.csv
  ```
  *(Kết quả: Đúng 50 đơn `200 OK`, 150 đơn `400 SOLD OUT`, DB & Redis không bao giờ bán âm vé).*

📖 Xem hướng dẫn chi tiết, phân tích SLA & kịch bản Spike Test tại: [JMeter Performance Testing Guide](docs/jmeter_performance_testing_guide.md)

---

### 4. Kiểm Thử Các Kịch Bản Trọng Yếu (Critical Concurrency & Security Tests)

Dự án cung cấp sẵn các bộ Automated Test Cases chuyên biệt cho từng bài toán chống gian lận & tranh chấp cao điểm:

| Kịch bản kiểm thử | Mô tả & Kỳ vọng | Lệnh chạy test |
|---|---|---|
| 🔄 **Chống trùng đơn (Idempotency Key)** | Khi mạng lag, user spam click gửi nhiều request cùng `Idempotency-Key` ➔ Chỉ tạo 1 đơn, không trừ vé 2 lần. | `./mvnw test -Dtest=BookingIntegrationTest` |
| 🎟️ **Chống bán âm vé (Zero Overselling)** | 20 threads tranh mua 6 vé cuối cùng ➔ Đúng 6 đơn thành công, 14 đơn từ chối. | `./mvnw test -Dtest=InventoryIntegrationTest` |
| 🏷️ **Chống lạm dụng Voucher** | 1 user cố dùng 1 mã 2 lần (hoặc 100 users tranh mã cuối cùng) ➔ Khóa bi quan chặn đứng `409 Conflict`. | `./mvnw test -Dtest=VoucherIntegrationTest` |
| ⏳ **Thu hồi vé hết hạn (15 phút)** | Đơn quá 15 phút chưa trả tiền ➔ Scheduler tự động hủy đơn và hoàn trả vé lại cho kho. | `./mvnw test -Dtest=BookingExpiryServiceTest` |
| 🛡️ **Giới hạn tốc độ (Rate Limiting)** | Gửi quá 5 booking/phút hoặc quá 10 login/phút ➔ Chặn ngay `429 Too Many Requests`. | `./mvnw test -Dtest=RateLimitFilterTest` |
| 💥 **Stress Test tìm điểm gãy (2500+ RPM)** | Đẩy tải cực hạn để xác định năng lực tối đa của 1 node backend. | `.\jmeter\scripts\run_load_test.ps1 -BookingRpm 2500` |

---

## 📚 Tài Liệu Chi Tiết

| Tài liệu | Mô tả |
|---|---|
| [System & Database Design](docs/system_and_database_design.md) | Kiến trúc hệ thống, database schema, phân tích Flash Sale |
| [Assumptions & Limitations](docs/assumptions_and_limitations.md) | Giả định thiết kế và giới hạn hệ thống |
| [Coding Guideline](docs/coding_guideline.md) | Quy chuẩn code, hướng dẫn viết API mới |
| [JMeter Performance Testing Guide](docs/jmeter_performance_testing_guide.md) | Hướng dẫn load testing với JMeter |
| [Executive Performance Report](docs/executive_performance_report.md) | Báo cáo hiệu năng tổng hợp |

> 💡 **Mẹo xem sơ đồ Mermaid**: Các tài liệu thiết kế trên chứa các sơ đồ kiến trúc và flowchart vẽ bằng **Mermaid**:
> - **Trên GitHub / GitLab**: Sơ đồ Mermaid tự động được render trực tiếp khi xem file `.md`.
> - **Trong VS Code**: Khuyến nghị cài extension `Markdown Preview Mermaid Support` (`bierner.markdown-mermaid`) và nhấn `Ctrl + Shift + V` (`Cmd + Shift + V` trên macOS) để xem preview trực quan.
> - **Trong IntelliJ IDEA**: Plugin Markdown mặc định đã hỗ trợ hiển thị Mermaid sẵn có.
