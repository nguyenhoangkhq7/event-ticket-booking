# Giả Định & Giới Hạn Hệ Thống (Assumptions & Limitations)

> Tài liệu liệt kê các giả định thiết kế (assumptions) được đưa ra trong quá trình phát triển
> và các giới hạn hiện tại (limitations) của hệ thống Concert Ticket Booking Platform.

---

## Mục Lục

- [1. Giả Định (Assumptions)](#1-giả-định-assumptions)
- [2. Giới Hạn (Limitations)](#2-giới-hạn-limitations)

---

## 1. Giả Định (Assumptions)

### 1.1. Booking Status Lifecycle

Hệ thống định nghĩa vòng đời đơn hàng qua enum `BookingStatus` trong [`BookingStatus.java`](../src/main/java/com/geekup/eventticketbookingservice/booking/BookingStatus.java):

| Trạng thái | Ý nghĩa | Chuyển đổi tiếp theo |
|---|---|---|
| `RECEIVED` | Booking vừa được tạo, chờ thanh toán | → `PAID`, → `EXPIRED`, → `CANCELLED` |
| `PENDING_PAYMENT` | Đang xử lý thanh toán | → `PAID`, → `EXPIRED`, → `CANCELLED` |
| `PAID` | Đã thanh toán thành công | → `CANCELLED` (chỉ bởi Admin) |
| `EXPIRED` | Hết hạn thanh toán (sau 15 phút) | Terminal state |
| `CANCELLED` | Đã hủy (bởi Admin) | Terminal state |
| `FAILED` | Xử lý thất bại | Terminal state |

**Giả định**: Trạng thái khởi tạo luôn là `RECEIVED`. Hệ thống không hỗ trợ trạng thái `REFUNDED` hay `PARTIALLY_CANCELLED`.

### 1.2. User Roles

Chỉ có **2 role** được định nghĩa trong enum [`Role.java`](../src/main/java/com/geekup/eventticketbookingservice/user/Role.java):

| Role | Mô tả | Cách tạo |
|---|---|---|
| `CUSTOMER` | Người dùng cuối, đặt vé | Tự đăng ký qua `/api/auth/register` |
| `ADMIN` | Quản trị viên, quản lý hệ thống | Seed data hoặc insert trực tiếp vào DB |

**Giả định**: 
- Mọi đăng ký đều được gán role `CUSTOMER` mặc định (hardcoded trong `AuthService.register()`).
- Không có endpoint để tạo Admin. Tài khoản Admin chỉ được tạo qua database seed (migration V3) hoặc thao tác trực tiếp.
- DB CHECK constraint: `role IN ('CUSTOMER', 'ADMIN')` — không cho phép role khác.

### 1.3. User Status

Entity `User` có field `status` với 2 giá trị khả dụng (DB CHECK constraint):

| Status | Hành vi |
|---|---|
| `ACTIVE` | User có thể đăng nhập và sử dụng hệ thống (`User.isEnabled()` trả `true`) |
| `SUSPENDED` | User bị khóa (`User.isAccountNonLocked()` trả `false`) |

**Giả định**: Không có endpoint để suspend/activate user. Quản lý user status phải thông qua thao tác DB trực tiếp.

### 1.4. Single-Instance Architecture

**Giả định**: Hệ thống chạy trên **một instance duy nhất**:
- Không có API Gateway, Service Mesh, hay Load Balancer phía trước
- Không có message queue (Kafka, RabbitMQ) cho async processing
- `@Scheduled` task chỉ chạy trên instance hiện tại
- Rate limiting (`ConcurrentHashMap`) không chia sẻ giữa instances

### 1.5. Concert Status Lifecycle

Enum [`ConcertStatus.java`](../src/main/java/com/geekup/eventticketbookingservice/catalog/ConcertStatus.java):

| Status | Mô tả |
|---|---|
| `DRAFT` | Vừa tạo, chưa công khai |
| `PUBLISHED` | Đã mở bán, hiển thị cho khách hàng |
| `CANCELLED` | Đã hủy |
| `ENDED` | Đã kết thúc |

**Giả định**: 
- Chỉ concerts có status `PUBLISHED` mới hiển thị qua public API (`ConcertService.getPublishedConcerts()` filter theo status).
- Concert chỉ hỗ trợ transition `DRAFT → PUBLISHED` qua API. Các transition khác (→ CANCELLED, → ENDED) chưa có endpoint.

### 1.6. Voucher Status & Discount Types

**Voucher Status** ([`VoucherStatus.java`](../src/main/java/com/geekup/eventticketbookingservice/voucher/VoucherStatus.java)):

| Status | Mô tả | Transition |
|---|---|---|
| `ACTIVE` | Có thể sử dụng | → `USED_UP` (khi đạt max), → `DISABLED` (bởi admin) |
| `USED_UP` | Đã hết lượt dùng | → `ACTIVE` (khi booking bị hủy/hết hạn, hoàn lại lượt) |
| `EXPIRED` | Hết hạn thời gian | Terminal |
| `DISABLED` | Bị admin vô hiệu hóa | → `ACTIVE` (khi admin enable lại) |

**Discount Types** ([`DiscountType.java`](../src/main/java/com/geekup/eventticketbookingservice/voucher/DiscountType.java)):

| Type | Logic tính | Ràng buộc |
|---|---|---|
| `PERCENTAGE` | `subtotal × discountValue / 100` | `discountValue ≤ 100` (DB CHECK) |
| `FIXED` | `min(discountValue, subtotal)` | Không vượt quá giá trị đơn hàng |

**Giả định**: Không hỗ trợ discount type khác (Buy-X-Get-Y, tiered discount, etc.). Voucher không giới hạn theo concert cụ thể — áp dụng được cho bất kỳ booking nào.

### 1.7. Booking Expiration TTL

**Giả định**: Thời gian hết hạn booking được **hardcoded 15 phút** trong `BookingService.createBooking()`:
```java
.expiresAt(ZonedDateTime.now().plusMinutes(15))
```
Giá trị này không configurable qua application properties hay environment variable.

### 1.8. Scheduler Interval

**Giả định**: `BookingExpiryScheduler` chạy mỗi **60 giây** (`@Scheduled(fixedDelay = 60_000)`). Điều này có nghĩa:
- Một booking có thể tồn tại trong trạng thái `RECEIVED`/`PENDING_PAYMENT` tối đa ~16 phút (15 phút TTL + tối đa 60 giây chờ scheduler cycle tiếp theo).
- Scheduler sử dụng `fixedDelay` (không phải `fixedRate`), tức là đợi 60s **sau khi batch trước hoàn thành**.

### 1.9. Authentication & Token

**Giả định**:
- Stateless JWT — server không lưu trữ token. Không có cơ chế revoke/blacklist token.
- Không có Refresh Token. User phải đăng nhập lại khi token hết hạn.
- Token expiration: 24 giờ (86400000ms, configurable qua `JWT_EXPIRATION` env var).
- JWT signing: HMAC-SHA256 với secret key từ environment variable.

### 1.10. Currency & Pricing

**Giả định**: 
- Hệ thống sử dụng **đơn vị tiền tệ duy nhất** (VND, dựa trên seed data: giá vé từ 600,000 đến 4,500,000).
- Không có field `currency` trong bất kỳ bảng nào.
- Tất cả giá tiền dùng `NUMERIC(12,2)` / `BigDecimal` — hỗ trợ giá trị tối đa 9,999,999,999.99.

### 1.11. Idempotency Key

**Giả định**:
- Client tự generate `Idempotency-Key` và gửi qua HTTP header.
- Key được scope theo user: unique constraint trên `(user_id, idempotency_key)`.
- Không có TTL cho idempotency key — key tồn tại vĩnh viễn trong DB.
- Hệ thống tin tưởng client sẽ generate key unique (thường là UUID).

### 1.12. Booking Code Format

**Giả định**: Mã booking được generate tự động: `"BK-" + UUID.randomUUID().substring(0, 8).toUpperCase()`. Ví dụ: `BK-A1B2C3D4`. Không sequential, không chứa thông tin concert hay thời gian.

### 1.13. Redis là Cache, Không Phải Persistent Store

**Giả định**:
- PostgreSQL là **source of truth** cho inventory.
- Redis inventory counter được pre-warm từ DB lúc startup hoặc khi admin set inventory.
- Nếu Redis mất data (restart, crash), hệ thống tự động fallback về DB cho inventory validation.
- Redis được cấu hình `maxmemory-policy noeviction` + `appendonly yes` — dữ liệu được persist nhưng không đảm bảo consistency tuyệt đối.

---

## 2. Giới Hạn (Limitations)

### 2.1. Không Tích Hợp Payment Gateway

`BookingController.confirmPayment()` chỉ đơn giản chuyển status từ `RECEIVED`/`PENDING_PAYMENT` → `PAID`. **Không có**:
- Tích hợp cổng thanh toán thực (VNPay, Momo, Stripe, etc.)
- Webhook từ payment provider để xác nhận giao dịch
- Payment verification hay anti-fraud check

**Lý do**: Đây là phần simulate cho scope bài test. Trong production, endpoint này sẽ là webhook receiver từ payment gateway.

### 2.2. Không Có Endpoint Update/Delete Concert

`OperationConcertController` chỉ hỗ trợ:
- ✅ `POST /api/operation/concerts` — Tạo concert mới
- ✅ `PATCH /api/operation/concerts/{id}/publish` — Publish concert

**Không có**:
- ❌ PUT/PATCH để update thông tin concert (tên, venue, ngày giờ)
- ❌ DELETE để xóa concert
- ❌ Transition sang `CANCELLED` hoặc `ENDED`

### 2.3. Quản Lý Voucher

Voucher **CÓ** được quản lý qua API admin:
- ✅ `POST /api/operation/vouchers` — Tạo voucher campaign
- ✅ `PATCH /api/operation/vouchers/{id}/disable` — Vô hiệu hóa
- ✅ `PATCH /api/operation/vouchers/{id}/enable` — Kích hoạt lại

**Không có**:
- ❌ PUT/PATCH để update thông tin voucher (giá trị, hạn dùng, etc.)
- ❌ GET endpoint để liệt kê voucher trên admin dashboard
- ❌ DELETE để xóa voucher

Ngoài ra, 3 voucher seed sẵn (SUMMER2026, WELCOME50, VIPFLASH20) phục vụ cho testing.

### 2.4. Không Hỗ Trợ Chọn Ghế (No Seat Selection)

Hệ thống sử dụng mô hình **quantity-based ticket categories** (VIP, CAT 1, Standard, GA Standing, etc.). **Không có**:
- Bản đồ ghế ngồi (seat map)
- Chọn ghế cụ thể
- Hold ghế tạm thời

Mỗi `TicketCategory` có `totalCapacity` và hệ thống chỉ quản lý tổng số lượng vé available.

### 2.5. Không Có Email/Notification

Không có hệ thống thông báo:
- ❌ Email xác nhận booking
- ❌ Notification khi booking sắp hết hạn
- ❌ Push notification khi status thay đổi
- ❌ Email receipt sau thanh toán

User phải tự kiểm tra status booking qua API `GET /api/bookings`.

### 2.6. Pagination (Đã giải quyết một phần trên Catalog API)

- ✅ **Đã triển khai**: `GET /api/concerts` đã hỗ trợ Spring Data `Pageable` (`@PageableDefault(page = 0, size = 20, sort = "startAt", direction = ASC)`), trả về `ApiResponse<Page<ConcertResponse>>` với đầy đủ metadata (`content`, `totalElements`, `totalPages`, `size`, `number`). Caching theo từng page/size/sort key.
- ⚠️ **Còn lại**: Một số endpoint khác vẫn trả về danh sách đầy đủ:
  - `GET /api/bookings` — trả tất cả bookings của user
  - `GET /api/operation/bookings` — trả tất cả bookings trong hệ thống

**Kế hoạch**: Áp dụng `Pageable` đồng nhất cho các endpoint danh sách còn lại trong các bản cập nhật tiếp theo.

### 2.7. Không Có Search/Filter

Không có tính năng tìm kiếm hoặc lọc trên public API:
- ❌ Search concert theo tên
- ❌ Filter theo ngày, venue, price range
- ❌ Sort theo thời gian, giá, popularity
- ❌ Full-text search

### 2.8. Không Có Real-time Updates (WebSocket)

Thông tin tồn kho vé chỉ queryable qua REST polling (`GET /api/concerts/{id}/ticket-categories`). **Không có**:
- WebSocket push cho stock updates
- Server-Sent Events (SSE) cho booking status changes
- Live countdown cho booking expiration

### 2.9. Rate Limiting Chỉ Hoạt Động Single-Instance

`RateLimitFilter` sử dụng `ConcurrentHashMap<String, Bucket>` lưu trong JVM memory:
- Nếu deploy N instances, mỗi instance có counter riêng
- User có thể thực hiện `5 × N` booking requests/phút bằng cách request vào các instance khác nhau
- **Production fix**: Sử dụng Redis-backed rate limiter (Bucket4j-Redis hoặc Spring Cloud Gateway rate limiter)

### 2.10. Audit Logging (Đã tích hợp Spring Data JPA Auditing)

- ✅ **Đã triển khai**: Các entity quan trọng (`Booking`, `Voucher`, `Concert`, `User`, `TicketCategory`, `TicketInventory`, `VoucherRedemption`) đã được tích hợp Spring Data JPA Auditing (`@EnableJpaAuditing`, `BaseAuditEntity`, `@CreatedDate`, `@LastModifiedDate`, `AuditingEntityListener`), tự động ghi nhận chính xác thời điểm tạo (`created_at`) và sửa đổi (`updated_at`) trên DB.
- ⚠️ **Giới hạn còn lại**: Chưa có bảng log hành vi chuyên biệt (Event Sourcing / Spring Data Envers / Audit Table) để lưu actor user ID cho mọi thao tác cập nhật trạng thái admin.

### 2.11. Không Có Soft Delete

Entities không hỗ trợ soft delete:
- `booking_items` và `voucher_redemptions` sử dụng `ON DELETE CASCADE`
- Không có flag `deleted` hay `deleted_at` trên bất kỳ entity nào

### 2.12. Không Có Customer Self-Cancel

**Không có** endpoint cho khách hàng tự hủy booking. Chỉ Admin có thể hủy qua:
- `POST /api/operation/bookings/{id}/cancel`

Booking chỉ được hủy gián tiếp khi hết hạn 15 phút (auto-expire).

### 2.13. Redis Key Không Có TTL

Redis inventory keys (`inventory:<categoryId>`) không được set TTL. Chúng tồn tại vĩnh viễn cho đến khi:
- Redis bị restart (có `appendonly` nên sẽ recover)
- Key bị xóa thủ công

Trong production, nên có cơ chế sync lại Redis từ DB định kỳ.

### 2.14. Không Có API Versioning

Tất cả endpoints sử dụng prefix `/api/` mà không có version:
- Hiện tại: `/api/bookings`, `/api/concerts`
- Best practice: `/api/v1/bookings`, `/api/v1/concerts`

### 2.15. Swagger / OpenAPI 3.0 UI (Đã Tích Hợp)

- ✅ **Đã triển khai**: Hệ thống đã tích hợp thư viện `springdoc-openapi-starter-webmvc-ui` (OpenAPI 3.0), cung cấp giao diện Swagger UI trực quan và tài liệu API JSON tự động:
  - **Swagger UI URL**: `http://localhost:8080/swagger-ui.html` (hoặc `/swagger-ui/index.html`)
  - **OpenAPI Schema URL**: `http://localhost:8080/v3/api-docs`
  - **Security Scheme**: Hỗ trợ Bearer JWT Token (`bearerAuth`) trực tiếp trên giao diện Swagger UI để test các endpoint bảo mật.

### 2.16. CORS & CSRF

- **CORS**: Disabled (`AbstractHttpConfigurer::disable`). Production cần whitelist specific origins.
- **CSRF**: Disabled. Phù hợp cho stateless JWT API, nhưng cần lưu ý nếu thêm session-based features.

### 2.17. Không Có File Upload / Image Management

Entity `Concert` **không có field `imageUrl`** (khác biệt giữa DB schema V1 ban đầu và code hiện tại). Hệ thống không quản lý hình ảnh cho concerts.

### 2.18. Booking Code Collision Potential

Booking code sử dụng 8 ký tự từ UUID (`BK-XXXXXXXX`), tương đương ~4 tỷ combinations. Với volume thấp hiện tại thì an toàn, nhưng không có mechanism retry nếu collision xảy ra (dù xác suất cực thấp).
