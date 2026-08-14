# Coding Guideline — Quy Chuẩn Phát Triển

> Hướng dẫn quy chuẩn code, convention, và cách thêm API mới trong dự án Concert Ticket Booking Platform.
> Tài liệu dựa trên các pattern thực tế đang được áp dụng trong codebase.

---

## Mục Lục

- [1. Triết Lý Kiến Trúc](#1-triết-lý-kiến-trúc)
- [2. Cấu Trúc Thư Mục](#2-cấu-trúc-thư-mục)
- [3. Hướng Dẫn: Viết Một API Mới Từ A → Z](#3-hướng-dẫn-viết-một-api-mới-từ-a--z)
- [4. Quy Chuẩn Đặt Tên (Naming Conventions)](#4-quy-chuẩn-đặt-tên-naming-conventions)
- [5. Quy Chuẩn Entity](#5-quy-chuẩn-entity)
- [6. Quy Chuẩn Controller](#6-quy-chuẩn-controller)
- [7. Quy Chuẩn Service](#7-quy-chuẩn-service)
- [8. Quy Chuẩn Repository](#8-quy-chuẩn-repository)
- [9. Exception Handling](#9-exception-handling)
- [10. API Response Format](#10-api-response-format)
- [11. Database Migration (Flyway)](#11-database-migration-flyway)
- [12. Testing Conventions](#12-testing-conventions)

---

## 1. Triết Lý Kiến Trúc

Dự án áp dụng kiến trúc **Feature-based Modular Monolith**:
- Mỗi nghiệp vụ (feature/domain) là một **top-level package** — KHÔNG phải layered architecture (controller/, service/, repository/).
- Trong mỗi module: Entity, Controller, Service, Repository, Mapper, Enums nằm cùng package.
- DTOs được tổ chức trong sub-package `dto/`.
- Shared concerns (exception, response format, rate limiting, caching) nằm trong package `common/`.

```
✅ Đúng:  com.geekup.eventticketbookingservice.booking.BookingService
❌ Sai:   com.geekup.eventticketbookingservice.service.BookingService
```

---

## 2. Cấu Trúc Thư Mục

```
com.geekup.eventticketbookingservice/
├── {feature}/                    # Module nghiệp vụ
│   ├── {Feature}.java           # Entity (JPA)
│   ├── {Feature}Status.java     # Enum trạng thái
│   ├── {Feature}Controller.java # REST Controller
│   ├── {Feature}Service.java    # Business logic
│   ├── {Feature}Repository.java # Data access (JPA)
│   ├── {Feature}Mapper.java     # MapStruct mapper
│   └── dto/
│       ├── Create{Feature}Request.java
│       └── {Feature}Response.java
└── common/                      # Cross-cutting concerns
    ├── config/
    │   └── CacheConfig.java
    ├── dto/
    │   └── ApiResponse.java
    ├── exception/
    │   ├── AppException.java
    │   ├── ErrorCode.java
    │   └── GlobalExceptionHandler.java
    └── ratelimit/
        └── RateLimitFilter.java
```

---

## 3. Hướng Dẫn: Viết Một API Mới Từ A → Z

Ví dụ: Thêm tính năng **Feedback** (khách hàng đánh giá sau concert).

### Bước 1: Tạo Database Migration

Tạo file migration mới trong `src/main/resources/db/migration/`:

```sql
-- V5__create_feedback_table.sql

CREATE TABLE feedbacks (
    id          BIGSERIAL       PRIMARY KEY,
    user_id     BIGINT          NOT NULL,
    concert_id  BIGINT          NOT NULL,
    rating      SMALLINT        NOT NULL,
    comment     TEXT,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_feedbacks_user    FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_feedbacks_concert FOREIGN KEY (concert_id) REFERENCES concerts(id),
    CONSTRAINT uq_feedbacks_user_concert UNIQUE (user_id, concert_id),
    CONSTRAINT chk_feedbacks_rating CHECK (rating >= 1 AND rating <= 5)
);

CREATE INDEX idx_feedbacks_concert ON feedbacks(concert_id);
```

### Bước 2: Tạo Entity

```java
package com.geekup.eventticketbookingservice.feedback;

@Entity
@Table(name = "feedbacks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long concertId;

    @Column(nullable = false)
    private Short rating;

    private String comment;

    @CreationTimestamp
    @Column(updatable = false)
    private ZonedDateTime createdAt;
}
```

### Bước 3: Tạo Repository

```java
package com.geekup.eventticketbookingservice.feedback;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByConcertId(Long concertId);

    boolean existsByUserIdAndConcertId(Long userId, Long concertId);
}
```

### Bước 4: Tạo DTOs

```java
// dto/CreateFeedbackRequest.java
package com.geekup.eventticketbookingservice.feedback.dto;

@Data
public class CreateFeedbackRequest {
    @NotNull
    private Long concertId;

    @NotNull @Min(1) @Max(5)
    private Short rating;

    private String comment;
}

// dto/FeedbackResponse.java
@Data @Builder
public class FeedbackResponse {
    private Long id;
    private Long userId;
    private Long concertId;
    private Short rating;
    private String comment;
    private ZonedDateTime createdAt;
}
```

### Bước 5: Tạo Mapper (MapStruct)

```java
package com.geekup.eventticketbookingservice.feedback;

@Mapper(componentModel = "spring")
public interface FeedbackMapper {

    FeedbackResponse toResponse(Feedback feedback);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Feedback toEntity(CreateFeedbackRequest request);
}
```

### Bước 6: Tạo Service

```java
package com.geekup.eventticketbookingservice.feedback;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final FeedbackMapper feedbackMapper;
    private final ConcertRepository concertRepository;

    @Transactional
    public FeedbackResponse createFeedback(Long userId, CreateFeedbackRequest request) {
        // 1. Validate concert exists
        concertRepository.findById(request.getConcertId())
            .orElseThrow(() -> new AppException(ErrorCode.CONCERT_NOT_FOUND));

        // 2. Check duplicate feedback
        if (feedbackRepository.existsByUserIdAndConcertId(userId, request.getConcertId())) {
            throw new AppException(ErrorCode.FEEDBACK_ALREADY_EXISTS);
        }

        // 3. Create and save
        Feedback feedback = feedbackMapper.toEntity(request);
        feedback.setUserId(userId);
        feedback = feedbackRepository.save(feedback);

        return feedbackMapper.toResponse(feedback);
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> getFeedbacksByConcert(Long concertId) {
        return feedbackRepository.findByConcertId(concertId).stream()
            .map(feedbackMapper::toResponse)
            .toList();
    }
}
```

### Bước 7: Thêm ErrorCode (nếu cần)

Trong `common/exception/ErrorCode.java`, thêm constant mới:

```java
FEEDBACK_ALREADY_EXISTS("FEEDBACK_ALREADY_EXISTS", "Feedback already submitted", HttpStatus.CONFLICT),
```

### Bước 8: Tạo Controller

```java
package com.geekup.eventticketbookingservice.feedback;

@RestController
@RequestMapping("/api/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<ApiResponse<FeedbackResponse>> createFeedback(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateFeedbackRequest request) {
        FeedbackResponse response = feedbackService.createFeedback(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/concerts/{concertId}")
    public ResponseEntity<ApiResponse<List<FeedbackResponse>>> getConcertFeedbacks(
            @PathVariable Long concertId) {
        return ResponseEntity.ok(
                ApiResponse.success(feedbackService.getFeedbacksByConcert(concertId)));
    }
}
```

### Bước 9: Cập Nhật Security (nếu cần)

Nếu endpoint cần public access, thêm vào `SecurityConfig.securityFilterChain()`:

```java
.requestMatchers(HttpMethod.GET, "/api/feedbacks/**").permitAll()
```

Nếu endpoint dành cho authenticated users, không cần thay đổi gì (đã có `anyRequest().authenticated()`).

### Bước 10: Viết Test

```java
@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock private FeedbackRepository feedbackRepository;
    @Mock private FeedbackMapper feedbackMapper;
    @Mock private ConcertRepository concertRepository;
    @InjectMocks private FeedbackService feedbackService;

    @Test
    void createFeedback_success() {
        // Given
        given(concertRepository.findById(1L)).willReturn(Optional.of(new Concert()));
        given(feedbackRepository.existsByUserIdAndConcertId(1L, 1L)).willReturn(false);
        given(feedbackRepository.save(any())).willReturn(mockFeedback);
        given(feedbackMapper.toResponse(any())).willReturn(mockResponse);
        given(feedbackMapper.toEntity(any())).willReturn(new Feedback());

        // When
        FeedbackResponse result = feedbackService.createFeedback(1L, request);

        // Then
        assertNotNull(result);
        verify(feedbackRepository).save(any());
    }
}
```

---

## 4. Quy Chuẩn Đặt Tên (Naming Conventions)

### Classes

| Loại | Convention | Ví dụ |
|---|---|---|
| Entity | `{Feature}` | `Booking`, `Concert`, `Voucher` |
| Enum | `{Feature}Status` hoặc `{Feature}Type` | `BookingStatus`, `DiscountType` |
| Controller | `{Feature}Controller` | `BookingController`, `ConcertController` |
| Admin Controller | `Operation{Feature}Controller` | `OperationBookingController` |
| Service | `{Feature}Service` | `BookingService`, `VoucherService` |
| Repository | `{Feature}Repository` | `BookingRepository` |
| Mapper | `{Feature}Mapper` | `BookingMapper`, `ConcertMapper` |
| Request DTO | `Create{Feature}Request` hoặc `Update{Feature}Request` | `CreateBookingRequest` |
| Response DTO | `{Feature}Response` | `BookingResponse`, `ConcertResponse` |

### Variables & Methods

| Loại | Convention | Ví dụ |
|---|---|---|
| Variable | `camelCase` | `ticketCategoryId`, `discountAmount` |
| Method | `camelCase` | `createBooking()`, `findByCode()` |
| Constant | `UPPER_SNAKE_CASE` | `KEY_PREFIX`, `MAX_RETRIES` |
| DB column | `snake_case` | `user_id`, `created_at`, `discount_amount` |
| DB table | `snake_case` (plural) | `bookings`, `ticket_categories` |

### Packages

```
com.geekup.eventticketbookingservice.{module}          # Top-level module
com.geekup.eventticketbookingservice.{module}.dto       # DTOs
com.geekup.eventticketbookingservice.common.exception   # Shared exceptions
com.geekup.eventticketbookingservice.common.config      # Shared configs
```

---

## 5. Quy Chuẩn Entity

### Template

```java
@Entity
@Table(name = "table_name_plural")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EntityName {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fieldName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SomeStatus status;

    @CreationTimestamp
    @Column(updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    private ZonedDateTime updatedAt;
}
```

### Quy tắc

| Quy tắc | Chi tiết |
|---|---|
| **ID type** | Sử dụng `Long` với `GenerationType.IDENTITY` |
| **Temporal type** | Sử dụng `ZonedDateTime` (mapped to `TIMESTAMPTZ`) |
| **Enum persistence** | `@Enumerated(EnumType.STRING)` — lưu tên enum dạng text |
| **Audit fields** | Dùng `@CreationTimestamp` và `@UpdateTimestamp` từ Hibernate |
| **Lombok** | Bắt buộc: `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder` |
| **Money fields** | `BigDecimal` với `precision = 12, scale = 2` |
| **FK fields** | Lưu dạng `Long {entity}Id` (flat), KHÔNG dùng `@ManyToOne` relationship |

---

## 6. Quy Chuẩn Controller

### Template

```java
@RestController
@RequestMapping("/api/{feature}")
@RequiredArgsConstructor
public class FeatureController {

    private final FeatureService featureService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FeatureResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(featureService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FeatureResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(featureService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FeatureResponse>> create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateFeatureRequest request) {
        FeatureResponse response = featureService.create(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}
```

### Quy tắc

| Quy tắc | Chi tiết |
|---|---|
| **Injection** | Constructor injection via `@RequiredArgsConstructor` |
| **Response wrapper** | Luôn wrap trong `ApiResponse<T>` |
| **Return type** | `ResponseEntity<ApiResponse<T>>` |
| **Auth user** | Dùng `@AuthenticationPrincipal User user` |
| **Validation** | `@Valid @RequestBody` cho request DTOs |
| **Path variable** | `@PathVariable Long id` |
| **Status code** | `200` cho GET, `201` cho POST create |
| **Không xử lý exception** | Để `GlobalExceptionHandler` xử lý |

### URL Pattern

```
/api/{feature}              # CRUD chính (customer-facing)
/api/operation/{feature}    # Admin/operator endpoints
```

---

## 7. Quy Chuẩn Service

### Template

```java
@Service
@RequiredArgsConstructor
public class FeatureService {

    private final FeatureRepository featureRepository;
    private final FeatureMapper featureMapper;

    @Transactional
    public FeatureResponse create(Long userId, CreateFeatureRequest request) {
        // 1. Validate business rules
        // 2. Create entity
        // 3. Save to DB
        // 4. Return mapped response
    }

    @Transactional(readOnly = true)
    public FeatureResponse getById(Long id) {
        Feature feature = featureRepository.findById(id)
            .orElseThrow(() -> new AppException(ErrorCode.FEATURE_NOT_FOUND));
        return featureMapper.toResponse(feature);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "features", key = "'all'")
    public List<FeatureResponse> getAll() {
        return featureRepository.findAll().stream()
            .map(featureMapper::toResponse)
            .toList();
    }
}
```

### Quy tắc

| Quy tắc | Chi tiết |
|---|---|
| **Style** | Concrete class (KHÔNG dùng interface + impl pattern) |
| **Injection** | Constructor injection via `@RequiredArgsConstructor` |
| **Write operations** | `@Transactional` |
| **Read operations** | `@Transactional(readOnly = true)` |
| **Exception** | Throw `AppException(ErrorCode.XXX)` |
| **Mapping** | Dùng MapStruct mapper, KHÔNG map thủ công |
| **Caching** | `@Cacheable` cho read-heavy endpoints (xem `CacheConfig`) |

---

## 8. Quy Chuẩn Repository

### Template

```java
public interface FeatureRepository extends JpaRepository<Feature, Long> {

    // Derived query methods
    Optional<Feature> findByCode(String code);
    boolean existsByUserIdAndFeatureId(Long userId, Long featureId);
    List<Feature> findByStatus(FeatureStatus status);

    // Custom JPQL query
    @Query("SELECT f FROM Feature f WHERE f.status IN :statuses AND f.expiresAt < :now")
    List<Feature> findExpired(List<FeatureStatus> statuses, ZonedDateTime now);

    // Pessimistic locking query
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Feature f WHERE f.id = :id")
    Optional<Feature> findByIdForUpdate(Long id);
}
```

### Quy tắc

| Quy tắc | Chi tiết |
|---|---|
| **Base class** | Extends `JpaRepository<Entity, Long>` |
| **Simple queries** | Dùng derived query methods (`findBy...`, `existsBy...`) |
| **Complex queries** | Dùng `@Query` với JPQL |
| **Pessimistic lock** | `@Lock(LockModeType.PESSIMISTIC_WRITE)` + `@Query` |
| **Naming** | `findByIdForUpdate()` cho locking queries |

---

## 9. Exception Handling

### Kiến Trúc

```
AppException (extends RuntimeException)
    └── ErrorCode (enum) ──mapping──> HttpStatus
            │
            ▼
GlobalExceptionHandler (@RestControllerAdvice)
    └── ApiResponse.error(code, message)
```

### Thêm Exception Mới

**Bước 1**: Thêm constant vào `ErrorCode`:

```java
// common/exception/ErrorCode.java
public enum ErrorCode {
    // ... existing codes ...
    FEATURE_NOT_FOUND("FEATURE_NOT_FOUND", "Feature not found", HttpStatus.NOT_FOUND),
    FEATURE_LIMIT_REACHED("FEATURE_LIMIT_REACHED", "Feature limit reached", HttpStatus.CONFLICT),
}
```

**Bước 2**: Throw trong Service:

```java
// Dùng message mặc định từ ErrorCode
throw new AppException(ErrorCode.FEATURE_NOT_FOUND);

// Hoặc dùng custom message
throw new AppException(ErrorCode.FEATURE_LIMIT_REACHED, "Custom error message here");
```

**KHÔNG cần**: Tạo class exception riêng, thêm handler mới, hay modify `GlobalExceptionHandler`.

### Bảng Mapping ErrorCode → HTTP Status

| ErrorCode | HTTP Status | Khi nào dùng |
|---|---|---|
| `*_NOT_FOUND` | `404` | Resource không tồn tại |
| `*_ALREADY_EXISTS` | `409` | Duplicate resource |
| `*_INVALID` | `400` | Input/state không hợp lệ |
| `TICKET_SOLD_OUT` / `NOT_ENOUGH_TICKETS` | `409` | Hết vé |
| `VOUCHER_LIMIT_REACHED` / `VOUCHER_ALREADY_REDEEMED` | `409` | Voucher conflict |
| `UNAUTHORIZED` | `401` | Chưa xác thực |
| `FORBIDDEN` | `403` | Không đủ quyền |
| `RATE_LIMIT_EXCEEDED` | `429` | Vượt giới hạn request |
| `INTERNAL_SERVER_ERROR` | `500` | Lỗi hệ thống |

---

## 10. API Response Format

### Envelope Structure

Tất cả API responses đều được wrap trong `ApiResponse<T>`:

```java
// common/dto/ApiResponse.java
@Data @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ErrorResponse error;

    public record ErrorResponse(String code, String message) {}

    public static <T> ApiResponse<T> success(T data) { ... }
    public static <T> ApiResponse<T> error(String code, String message) { ... }
}
```

### Response Examples

**Success (200 OK):**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "Concert ABC",
    "status": "PUBLISHED"
  }
}
```

**Success with list:**
```json
{
  "success": true,
  "data": [
    { "id": 1, "name": "Concert A" },
    { "id": 2, "name": "Concert B" }
  ]
}
```

**Error (4xx/5xx):**
```json
{
  "success": false,
  "error": {
    "code": "CONCERT_NOT_FOUND",
    "message": "Concert not found"
  }
}
```

**Validation Error (400):**
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "rating: must be between 1 and 5; concertId: must not be null"
  }
}
```

### Quy tắc quan trọng

- `null` fields **KHÔNG** xuất hiện trong response (`@JsonInclude(NON_NULL)`)
- Success response luôn có `"success": true` + `"data"`
- Error response luôn có `"success": false` + `"error"` (không có `"data"`)
- Không sử dụng status text trong body — dùng HTTP status code

---

## 11. Database Migration (Flyway)

### Quy tắc đặt tên file

```
V{version}__{description}.sql
```

Ví dụ:
- `V1__init_schema_database.sql`
- `V2__update_user_roles.sql`
- `V3__insert_sample_data.sql`
- `V5__create_feedback_table.sql`

### Quy tắc viết migration

| Quy tắc | Chi tiết |
|---|---|
| **Table name** | `snake_case`, plural (`bookings`, `ticket_categories`) |
| **Column name** | `snake_case` (`user_id`, `created_at`) |
| **PK** | `BIGSERIAL PRIMARY KEY` |
| **FK naming** | `fk_{table}_{referenced}` |
| **UK naming** | `uq_{table}_{columns}` |
| **Index naming** | `idx_{table}_{columns}` |
| **Check naming** | `chk_{table}_{rule}` |
| **Enum columns** | `VARCHAR` với CHECK constraint (KHÔNG dùng PostgreSQL ENUM type) |
| **Money columns** | `NUMERIC(12, 2)` |
| **Timestamps** | `TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| **Computed columns** | `GENERATED ALWAYS AS (...) STORED` |

### Ví dụ CHECK constraint cho enum

```sql
-- ✅ Đúng: dùng CHECK constraint
ALTER TABLE feedbacks
ADD CONSTRAINT chk_feedbacks_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'));

-- ❌ Sai: dùng PostgreSQL ENUM type (khó thay đổi giá trị sau)
CREATE TYPE feedback_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED');
```

---

## 12. Testing Conventions

### Cấu trúc Test

```
src/test/java/com/geekup/eventticketbookingservice/
├── {module}/
│   ├── {Feature}ServiceTest.java          # Unit test (Mockito)
│   └── {Feature}IntegrationTest.java      # Integration test (Testcontainers)
```

### Unit Test Pattern (Mockito)

```java
@ExtendWith(MockitoExtension.class)
class FeatureServiceTest {

    @Mock private FeatureRepository featureRepository;
    @Mock private FeatureMapper featureMapper;
    @InjectMocks private FeatureService featureService;

    @Test
    void methodName_scenario_expectedResult() {
        // Given
        given(featureRepository.findById(1L)).willReturn(Optional.of(mockEntity));
        given(featureMapper.toResponse(any())).willReturn(mockResponse);

        // When
        FeatureResponse result = featureService.getById(1L);

        // Then
        assertNotNull(result);
        assertEquals(expected, result.getName());
        verify(featureRepository).findById(1L);
    }

    @Test
    void methodName_whenNotFound_throwsException() {
        // Given
        given(featureRepository.findById(1L)).willReturn(Optional.empty());

        // When & Then
        AppException ex = assertThrows(AppException.class,
            () -> featureService.getById(1L));
        assertEquals(ErrorCode.FEATURE_NOT_FOUND, ex.getErrorCode());
    }
}
```

### Integration Test Pattern (Testcontainers)

```java
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class FeatureIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private FeatureRepository featureRepository;

    @Test
    void fullLifecycleTest() {
        // Test with real database
    }
}
```

### Test Naming Convention

```
methodName_scenario_expectedResult
```

Ví dụ:
- `createBooking_withValidRequest_returnsBookingResponse`
- `createBooking_whenTicketSoldOut_throwsAppException`
- `validateVoucher_whenExpired_throwsVoucherInvalid`
- `tryDecrement_whenInsufficientStock_returnsFalse`

### Testing Tools

| Tool | Mục đích | Scope |
|---|---|---|
| **JUnit 5** | Test framework | Unit + Integration |
| **Mockito** | Mocking dependencies | Unit test |
| **Testcontainers** | Real PostgreSQL in Docker | Integration test |
| **Spring Boot Test** | Application context loading | Integration test |
| **JMeter** | Load & performance testing | Performance |
