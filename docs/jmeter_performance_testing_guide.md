# Hướng Dẫn Toàn Diện Kiểm Thử Hiệu Năng Với Apache JMeter
## Event Ticket Booking Service

Tài liệu này cung cấp toàn bộ quy trình thiết lập dữ liệu, phân tích mô hình lưu lượng thực tế, thực thi kiểm thử tải (Load Test), kiểm thử sốc tải (Spike Test) và kiểm thử giới hạn gãy (Breakpoint Test) cho hệ thống **Event Ticket Booking Service**.

---

## 1. Phân Tích Mô Hình Tải & Chiến Lược Quy Mô (Load Profile & Strategy)

### 1.1. Tại sao yêu cầu 300–500 RPM nhưng cần test ở các mức lớn hơn?

Hệ thống bán vé sự kiện (Concert / Flash-sale) có đặc tính lưu lượng **rất khác** so với website thương mại điện tử thông thường:

```
Lưu lượng (Requests/giây)
   ▲
   │                █ █ ◄── Đột biến lúc mở bán (Spike: 1,000 - 3,000 RPM / 25 - 50 RPS)
   │               ██ ██
   │              ███ ███
   │  ───────────████ ████─────────── ◄── Mức trung bình thiết kế (300 - 500 RPM / ~8.3 RPS)
   │  Browsing..                     
   └──────────────────────────────────► Thời gian
     10:00:00 AM (Cổng vé mở)
```

1. **Hiệu ứng dồn tải (Flash Crowd)**:
   - Khi có **50,000 users** canh giờ mở bán, 90% lượng booking sẽ dồn vào **vài giây đầu tiên** khi cổng thanh toán mở ra.
   - Lưu lượng tức thời có thể vọt lên **1,000 – 3,000 req/phút (~25 – 50 req/giây)** trước khi hạ nhiệt.
2. **Khảo sát "Điểm Gãy" (Breaking Point & Capacity Planning)**:
   - Cần xác định chính xác năng lực xử lý tối đa của 1 node backend (CPU, RAM, DB Connections, Redis Latency) để thiết lập ngưỡng kích hoạt Auto-scaling phù hợp.
3. **Kiểm thử tính đúng đắn & Chống bán âm vé (Zero Overselling)**:
   - Khi hàng trăm user cùng tranh mua những tấm vé VIP cuối cùng, tầng **Redis Pre-decrement** và **PostgreSQL Row Lock (`SELECT FOR UPDATE`)** phải đảm bảo không bao giờ bán vượt quá số lượng tồn kho (Zero Overselling) và không bị Deadlock.

---

### 1.2. Chiến lược kiểm thử 4 cấp độ (4-Tier Performance Testing)

| Cấp độ | Tên bài test | Lưu lượng mục tiêu | Mục đích chính | SLA kỳ vọng |
| :---: | :--- | :--- | :--- | :--- |
| **Tier 1** | **Baseline Load Test** | **300 – 500 RPM** (~5.0 – 8.33 RPS) | Đảm bảo hệ thống đạt đúng cam kết SLA thiết kế ban đầu. | Error = 0%, p95 < 20ms, CPU < 30% |
| **Tier 2** | **Safety Margin Test** | **1,000 – 1,500 RPM** (~16.7 – 25 RPS) | Kiểm tra hệ số an toàn (2x–3x) khi sự kiện thu hút đông người hơn dự kiến. | Error = 0%, p95 < 50ms, không nghẽn Connection Pool |
| **Tier 3** | **Flash Sale Spike Test** | **200 – 500 Threads cùng bắn trong 1 ms** | Kiểm tra race condition, tính toàn vẹn của kho vé & voucher khi tranh chấp cao điểm. | Không bị Overselling, các request đến sau nhận `400 Sold Out` an toàn |
| **Tier 4** | **Breakpoint / Stress Test** | **3,000 – 6,000+ RPM** (~50 – 100+ RPS) | Đẩy tải tăng dần đến khi hệ thống đạt giới hạn bão hòa để tìm điểm nghẽn cổ chai. | Xác định giới hạn tối đa của 1 node backend |

---

### 1.3. Cấu hình Môi trường Thực nghiệm & Khả năng Thích ứng Phần cứng (Hardware Profile & Scalability)

Toàn bộ chỉ số benchmark tiêu chuẩn trong dự án được ghi nhận trên cấu hình máy thực nghiệm sau:

| Thành phần | Thông số môi trường Benchmark |
| :--- | :--- |
| **CPU** | 11th Gen Intel(R) Core(TM) i5-11400H @ 2.70GHz (6 Cores / 12 Threads) |
| **RAM** | 16.0 GB DDR4 |
| **Hệ điều hành** | Windows 11 64-bit |
| **Hạ tầng Test** | Localhost (Spring Boot + PostgreSQL 16 + Redis 7 Docker + JMeter Engine) |

#### 💡 Hướng dẫn tinh chỉnh tham số khi chạy trên các cấu hình máy khác nhau:

| Phân khúc máy | Cấu hình tham khảo | Khuyến nghị tham số JMeter | Kỳ vọng SLA |
| :--- | :--- | :--- | :--- |
| **Máy cấu hình thấp (Entry)** | 2 Cores CPU, 8GB RAM | `RPM=300, Users=20, Duration=120s` | Error: 0%, p95 < 80ms |
| **Máy tiêu chuẩn (Standard - Baseline)** | 6 Cores CPU, 16GB RAM | `RPM=500, Users=50, Duration=300s` | Error: 0%, p95 < 20ms |
| **Máy chủ Staging / Cloud (High)** | 8+ Cores CPU, 32GB RAM | `RPM=1500–3000, Users=200, Duration=600s` | Error: 0%, p95 < 15ms |

> 📌 **Nguyên tắc cốt lõi**:
> - Dù chạy trên máy yếu hay mạnh, **tính toàn vẹn dữ liệu (0% Overselling, không trùng đơn, voucher không bị dùng quá số lần)** luôn được đảm bảo tuyệt đối 100%.
> - Trên máy yếu hơn, bạn có thể truyền `-Djmeter.booking_rpm=300 -Djmeter.booking_users=20` để bài test chạy nhẹ nhàng, tránh bị nghẽn do chính JMeter cạnh tranh tài nguyên CPU với Backend.

---

## 2. Cấu Trúc Thư Mục JMeter

```
jmeter/
├── data/
│   ├── seed_50k_users.sql         # Script SQL chèn 50,000 users vào PostgreSQL
│   ├── generate_test_data.py       # Tool Python sinh 50,000 JWT tokens (< 1 giây)
│   ├── GenerateTestData.java       # Tool Java độc lập sinh JWT tokens
│   └── users_tokens.csv           # File CSV Data Set cho JMeter (50,000 dòng user_id, email, token - 11.5 MB)
├── plans/
│   ├── event_ticket_booking_load_test.jmx  # Kịch bản chính (Browsing + Peak Booking điều phối RPM)
│   └── flash_sale_spike_test.jmx           # Kịch bản Spike/Rendezvous kiểm tra Race Condition & Cháy vé
├── scripts/
│   ├── run_load_test.bat          # Script chạy tự động trên Windows CMD
│   ├── run_load_test.ps1          # Script chạy tự động trên Windows PowerShell (kèm auto-setup)
│   ├── setup_jmeter.ps1           # Script tự động tải Apache JMeter Portable 5.6.3
│   └── run_load_test.sh           # Script chạy tự động trên Linux/macOS
├── docker-compose.jmeter.yml      # Chạy JMeter độc lập qua Docker
└── reports/                       # Thư mục lưu HTML Dashboard Reports sau khi chạy
```

---

## 3. Chuẩn Bị Môi Trường & Dữ Liệu Kiểm Thử (3 Bước)

### Bước 1: Khởi động Server Backend & Database
```bash
# Khởi động PostgreSQL và Redis:
docker-compose up -d postgres redis

# Chạy ứng dụng Backend (nếu chạy local):
./mvnw spring-boot:run
```

### Bước 2: Chèn 50,000 Users vào Database
```bash
# Nạp trực tiếp vào container PostgreSQL:
docker exec -i event-ticket-postgres psql -U ticket_user -d event_ticket_db < jmeter/data/seed_50k_users.sql
```
*Script sử dụng `generate_series(1, 50000)` trong PostgreSQL, hoàn tất trong ~1-2 giây.*

### Bước 3: Nạp Tồn Kho Vé lên Redis (Pre-warming)
```bash
docker exec -it event-ticket-redis redis-cli SET inventory:1 500000
docker exec -it event-ticket-redis redis-cli SET inventory:2 500000
docker exec -it event-ticket-redis redis-cli SET inventory:3 500000
docker exec -it event-ticket-redis redis-cli SET inventory:4 500000
```

---

## 4. Hướng Dẫn Chạy Test Cho Từng Cấp Độ

### Cấp độ 1: Baseline Load Test (300 – 500 RPM)

```cmd
# Chạy 500 RPM trong 5 phút (qua Maven Wrapper):
.\mvnw verify "-Dsurefire.skip=true" "-Djmeter.booking_rpm=500" "-Djmeter.duration=300"
```
```powershell
# Hoặc chạy qua PowerShell Runner (Tự mở báo cáo HTML sau khi hoàn tất):
.\jmeter\scripts\run_load_test.ps1 -BookingRpm 500 -Duration 300 -BrowseUsers 100 -BookingUsers 50
```

---

### Cấp độ 2: Safety Margin Test (1,000 – 1,500 RPM)

```cmd
# Thử nghiệm mức tải gấp 2x - 3x:
.\mvnw verify "-Dsurefire.skip=true" "-Djmeter.booking_rpm=1000" "-Djmeter.booking_users=100" "-Djmeter.duration=180"
```

---

### Cấp độ 3: Flash Sale Spike & Chống Bán Âm Vé (Spike Test)

Sử dụng kịch bản [`jmeter/plans/flash_sale_spike_test.jmx`](file:///d:/projects/event-ticket-booking/event-ticket-booking-service/jmeter/plans/flash_sale_spike_test.jmx) với **Synchronizing Timer (Rendezvous Point)** gom 200 threads cùng bắn vào 1 Category trong cùng 1 mili-giây:

```bash
jmeter -n -t jmeter/plans/flash_sale_spike_test.jmx \
  -l jmeter/reports/spike_results.jtl \
  -e -o jmeter/reports/spike_report \
  -Jusers=200 \
  -Jcategory_id=1 \
  -Jcsv_file=jmeter/data/users_tokens.csv
```

---

### Cấp độ 4: Breakpoint / Stress Test (2,500 – 5,000+ RPM)

```cmd
# Đẩy tải lên mức cực đại 2,500 - 5,000 RPM để tìm điểm gãy:
.\mvnw verify "-Dsurefire.skip=true" "-Djmeter.booking_rpm=2500" "-Djmeter.booking_users=200" "-Djmeter.duration=180"
```

---

### Chạy qua Docker Compose (Môi trường CI/CD hoặc máy không cài Java/JMeter)
```bash
docker-compose -f docker-compose.jmeter.yml up
```
*Báo cáo xuất tại:* `jmeter/reports/docker_report/index.html`

---

## 5. Kinh Nghiệm Thực Tế & Xử Lý Lỗi Thường Gặp

### 5.1. Lỗi `409 Conflict` (`VOUCHER_ALREADY_REDEEMED`)
- **Nguyên nhân**: Khi JMeter chạy với số lượng request lớn hơn số User trong pool hoặc lặp lại cùng một User token, nghiệp vụ tại `VoucherService.validateAndLock` từ chối nếu user đó đã áp dụng voucher này trước đó.
- **Giải pháp**:
  - Tách `CSVDataSet` riêng cho `Thread Group 2 (Booking)` với chế độ `shareMode.group`, mỗi lượt booking tiêu thụ tuần tự 1 user độc nhất.
  - Sử dụng pool 50,000 user để đáp ứng hàng chục nghìn lượt đặt vé không trùng lặp.

### 5.2. Lỗi `Cannot write to ... as folder is not empty`
- **Nguyên nhân**: JMeter yêu cầu thư mục xuất báo cáo HTML phải trống.
- **Giải pháp**: Đã tích hợp plugin `maven-clean-plugin` tự động dọn dẹp `target/jmeter/reports` và `results` ở phase `initialize` trước mỗi lần chạy test mới.

### 5.3. Điều phối Throughput trong JMeter
- Trong `ConstantThroughputTimer`, thuộc tính `calcMode` phải được cấu hình dạng số nguyên `<intProp name="calcMode">1</intProp>` (1 = all active threads) để JMeter áp dụng đúng thuật toán hãm tốc độ theo RPM.

---

## 6. Phân Tích Báo Cáo & Cẩm Nang Tinh Chỉnh Hệ Thống (Tuning Guide)

### 6.1. Các chỉ số cốt lõi trong HTML Dashboard

```
+---------------------------------------------------------------------------------+
| APDEX: 1.000 (Tuyệt vời)                                                        |
| Throughput: 8.1 requests/giây (~486 RPM)                                       |
| Response Time: Mean = 7ms | p90 = 12ms | p95 = 18ms | p99 = 35ms (Max: 48ms)   |
| Error Rate: 0.00% (0 failures)                                                  |
+---------------------------------------------------------------------------------+
```

- **APDEX (Application Performance Index)**: Điểm số trải nghiệm người dùng (mục tiêu > 0.95).
- **Latency p95 & p99**: Phát hiện sớm tình trạng nghẽn DB Connection Pool hoặc GC Pause.
- **Error Rate**: Đo lường tỷ lệ lỗi HTTP 500 hoặc Timeout.

---

### 6.2. Cẩm nang tinh chỉnh kiến trúc khi tải tăng cao

| Thành phần | Vấn đề khi tải cao | Cấu hình tinh chỉnh tối ưu |
| :--- | :--- | :--- |
| **HikariCP Connection Pool** | Cạn kiệt Connection khi đồng thời ghi DB nhiều | Tăng `maximum-pool-size: 50 - 100` trong `application.yaml`, đồng thời tăng `max_connections: 200+` trong PostgreSQL. |
| **Tomcat Thread Pool** | Request xếp hàng chờ lâu do thiếu worker thread | Cấu hình `server.tomcat.threads.max: 500` và `accept-count: 300`. |
| **Redis Inventory Counter** | Tranh chấp lock nếu bỏ qua Redis | Luôn bật `InventoryRedisService.tryDecrement` (atomic decrement) để chặn 99% tải trước khi chạm tới DB row lock (`SELECT FOR UPDATE`). |
| **Rate Limiter (Bucket4j)** | Giới hạn 5 req/phút/user và 10 req/phút/IP | Sử dụng pool 50,000 user độc lập và header `X-Forwarded-For` ngẫu nhiên trong JMeter. |
| **JVM Garbage Collection** | Stop-the-world GC gây tăng vọt p99 Latency | Chạy JVM với cấu hình G1GC:<br>`java -Xms2g -Xmx2g -XX:+UseG1GC -jar app.jar` |
