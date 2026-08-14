# 🎫 Concert Ticket Booking Platform

Hệ thống đặt vé concert trực tuyến xử lý high-concurrency, được thiết kế để chịu tải Flash Sale với 300–500+ booking requests/phút mà không xảy ra overselling, duplicate booking hay lạm dụng voucher.

---

## 📋 Mục Lục

- [Tech Stack](#-tech-stack)
- [Kiến Trúc Dự Án](#-kiến-trúc-dự-án)
- [Hướng Dẫn Cài Đặt & Chạy Local](#-hướng-dẫn-cài-đặt--chạy-local)
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

## 🧪 Hướng Dẫn Chạy Test

### Unit Tests

```bash
# Chạy toàn bộ unit tests
./mvnw test

# Windows
mvnw.cmd test
```

### Integration Tests (yêu cầu Docker cho Testcontainers)

```bash
# Chạy unit + integration tests
./mvnw verify
```

> **Lưu ý:** Integration tests sử dụng **Testcontainers** để tự động khởi tạo PostgreSQL container. Docker Desktop phải đang chạy.

### Chạy Test Cụ Thể

```bash
# Chạy một test class cụ thể
./mvnw test -Dtest=BookingServiceTest

# Chạy tests theo pattern
./mvnw test -Dtest="*IntegrationTest"

# Chạy một method cụ thể
./mvnw test -Dtest="BookingServiceTest#testCreateBooking"
```

### Load Test (JMeter)

```bash
# Chạy JMeter load test qua Maven plugin
./mvnw verify -Djmeter.host=localhost -Djmeter.port=8080

# Tùy chỉnh parameters
./mvnw verify \
  -Djmeter.host=localhost \
  -Djmeter.port=8080 \
  -Djmeter.booking_rpm=500 \
  -Djmeter.booking_users=50 \
  -Djmeter.duration=300
```

Xem thêm: [JMeter Performance Testing Guide](docs/jmeter_performance_testing_guide.md)

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
