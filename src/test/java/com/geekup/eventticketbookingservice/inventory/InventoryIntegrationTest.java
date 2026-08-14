package com.geekup.eventticketbookingservice.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geekup.eventticketbookingservice.AbstractIntegrationTest;
import com.geekup.eventticketbookingservice.booking.*;
import com.geekup.eventticketbookingservice.booking.dto.BookingItemRequest;
import com.geekup.eventticketbookingservice.booking.dto.CreateBookingRequest;
import com.geekup.eventticketbookingservice.catalog.*;
import com.geekup.eventticketbookingservice.common.exception.ErrorCode;
import com.geekup.eventticketbookingservice.operation.OperationService;
import com.geekup.eventticketbookingservice.security.JwtService;
import com.geekup.eventticketbookingservice.user.Role;
import com.geekup.eventticketbookingservice.user.User;
import com.geekup.eventticketbookingservice.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Inventory Module Integration Tests")
public class InventoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private InventoryRedisService inventoryRedisService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private TicketInventoryRepository inventoryRepository;

    @Autowired
    private TicketCategoryRepository categoryRepository;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingItemRepository bookingItemRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingExpiryService bookingExpiryService;

    @Autowired
    private OperationService operationService;

    @Autowired
    private TicketCategoryService ticketCategoryService;

    @Autowired
    private JwtService jwtService;

    private User adminUser;
    private String adminToken;

    private User customerUser;
    private String customerToken;

    private Concert testConcert;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        adminUser = userRepository.findByEmail("admin@eventticket.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("admin@eventticket.com")
                        .fullName("System Admin")
                        .password("$2a$12$H/tnE.97GdULmDOTk4MRO.y6rRv/4f30bKA4IV2RESH2b9nX49wQu")
                        .role(Role.ADMIN)
                        .status("ACTIVE")
                        .build()));
        adminToken = jwtService.generateToken(adminUser);

        customerUser = userRepository.findByEmail("customer1@example.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("customer1@example.com")
                        .fullName("Customer One")
                        .password("$2a$12$H/tnE.97GdULmDOTk4MRO.y6rRv/4f30bKA4IV2RESH2b9nX49wQu")
                        .role(Role.CUSTOMER)
                        .status("ACTIVE")
                        .build()));
        customerToken = jwtService.generateToken(customerUser);

        testConcert = concertRepository.findById(1L).orElseGet(() ->
                concertRepository.save(Concert.builder()
                        .name("Test Concert")
                        .description("Test Concert Description")
                        .venue("Test Stadium")
                        .startAt(ZonedDateTime.now().plusDays(10))
                        .endAt(ZonedDateTime.now().plusDays(10).plusHours(3))
                        .saleStartAt(ZonedDateTime.now().minusDays(1))
                        .saleEndAt(ZonedDateTime.now().plusDays(9))
                        .status(ConcertStatus.PUBLISHED)
                        .build()));
    }

    private User createUniqueCustomer(String prefix) {
        String uniqueEmail = prefix + "_" + UUID.randomUUID().toString().substring(0, 8) + "@inventory-test.com";
        User user = User.builder()
                .email(uniqueEmail)
                .fullName("Test User " + prefix)
                .password("$2a$12$H/tnE.97GdULmDOTk4MRO.y6rRv/4f30bKA4IV2RESH2b9nX49wQu")
                .role(Role.CUSTOMER)
                .status("ACTIVE")
                .build();
        return userRepository.save(user);
    }

    private TicketCategory createCategoryWithInventory(String name, BigDecimal price, int maxPerBooking, int totalQty, int reservedQty, int soldQty) {
        TicketCategory category = categoryRepository.save(TicketCategory.builder()
                .concertId(testConcert.getId())
                .name(name + " " + UUID.randomUUID().toString().substring(0, 6))
                .price(price)
                .maxPerBooking(maxPerBooking)
                .status(TicketCategoryStatus.ACTIVE)
                .build());

        inventoryRepository.save(TicketInventory.builder()
                .ticketCategoryId(category.getId())
                .totalQuantity(totalQty)
                .reservedQuantity(reservedQty)
                .soldQuantity(soldQty)
                .build());

        int available = Math.max(0, totalQty - reservedQty - soldQty);
        inventoryRedisService.preWarm(category.getId(), available);

        return category;
    }

    // =========================================================================
    // 1. PRE-WARM & REAL-TIME AVAILABILITY TESTS
    // =========================================================================
    @Nested
    @DisplayName("Pre-Warm & Real-Time Availability Tests")
    class PreWarmAndAvailabilityTests {

        @Test
        @DisplayName("preWarm sets inventory in Redis and getAvailable returns exact quantity")
        void preWarm_SetsRedisInventoryCorrectly() {
            Long dummyCategoryId = 9991L;
            int initialStock = 250;

            inventoryRedisService.preWarm(dummyCategoryId, initialStock);

            Integer available = inventoryRedisService.getAvailable(dummyCategoryId);
            assertNotNull(available);
            assertEquals(initialStock, available);

            // Also check raw Redis key
            String rawValue = redisTemplate.opsForValue().get("inventory:" + dummyCategoryId);
            assertEquals(String.valueOf(initialStock), rawValue);
        }

        @Test
        @DisplayName("preWarm overwrites previous quantity with new value")
        void preWarm_Overwrite_UpdatesRedisValue() {
            Long dummyCategoryId = 9992L;

            inventoryRedisService.preWarm(dummyCategoryId, 100);
            assertEquals(100, inventoryRedisService.getAvailable(dummyCategoryId));

            inventoryRedisService.preWarm(dummyCategoryId, 350);
            assertEquals(350, inventoryRedisService.getAvailable(dummyCategoryId));
        }

        @Test
        @DisplayName("getAvailable returns null when category key does not exist in Redis")
        void getAvailable_NonExistentKey_ReturnsNull() {
            Long nonExistentCategoryId = 888888L;
            redisTemplate.delete("inventory:" + nonExistentCategoryId);

            Integer available = inventoryRedisService.getAvailable(nonExistentCategoryId);
            assertNull(available);
        }

        @Test
        @DisplayName("TicketCategoryService falls back to DB computation when Redis cache is absent")
        void ticketCategoryService_FallbackToDatabaseCalculation() {
            TicketCategory category = createCategoryWithInventory("Fallback Category", new BigDecimal("500000.00"), 4, 100, 15, 25);

            // Delete Redis key to simulate cache miss / cold start
            redisTemplate.delete("inventory:" + category.getId());
            assertNull(inventoryRedisService.getAvailable(category.getId()));

            // Query category list via TicketCategoryService
            var categories = ticketCategoryService.getCategoriesByConcertId(testConcert.getId());
            var matched = categories.stream()
                    .filter(c -> c.getId().equals(category.getId()))
                    .findFirst();

            assertTrue(matched.isPresent());
            // Expected available: 100 total - 15 reserved - 25 sold = 60
            assertEquals(60, matched.get().getAvailableQuantity());
        }
    }

    // =========================================================================
    // 2. REDIS ATOMIC DECREMENT & RELEASE TESTS
    // =========================================================================
    @Nested
    @DisplayName("Redis Atomic Operations (tryDecrement & release)")
    class RedisAtomicOperationsTests {

        @Test
        @DisplayName("tryDecrement succeeds when available inventory is sufficient")
        void tryDecrement_SufficientInventory_ReturnsTrueAndDecrements() {
            Long categoryId = 9993L;
            inventoryRedisService.preWarm(categoryId, 10);

            boolean firstDeduct = inventoryRedisService.tryDecrement(categoryId, 3);
            assertTrue(firstDeduct);
            assertEquals(7, inventoryRedisService.getAvailable(categoryId));

            boolean secondDeduct = inventoryRedisService.tryDecrement(categoryId, 4);
            assertTrue(secondDeduct);
            assertEquals(3, inventoryRedisService.getAvailable(categoryId));
        }

        @Test
        @DisplayName("tryDecrement to exactly zero succeeds, subsequent attempt fails")
        void tryDecrement_ExactInventory_DecrementsToZeroAndNextFails() {
            Long categoryId = 9994L;
            inventoryRedisService.preWarm(categoryId, 5);

            boolean deductAll = inventoryRedisService.tryDecrement(categoryId, 5);
            assertTrue(deductAll);
            assertEquals(0, inventoryRedisService.getAvailable(categoryId));

            // Next attempt should fail (sold out)
            boolean nextDeduct = inventoryRedisService.tryDecrement(categoryId, 1);
            assertFalse(nextDeduct);
            assertEquals(0, inventoryRedisService.getAvailable(categoryId)); // Remains 0 after rollback
        }

        @Test
        @DisplayName("tryDecrement with insufficient inventory rolls back and returns false")
        void tryDecrement_InsufficientInventory_RollsBack() {
            Long categoryId = 9995L;
            inventoryRedisService.preWarm(categoryId, 2);

            // Request 4 tickets when only 2 available
            boolean result = inventoryRedisService.tryDecrement(categoryId, 4);
            assertFalse(result);

            // Stock should be rolled back to 2
            assertEquals(2, inventoryRedisService.getAvailable(categoryId));
        }

        @Test
        @DisplayName("release increments Redis inventory accurately")
        void release_IncrementsRedisInventory() {
            Long categoryId = 9996L;
            inventoryRedisService.preWarm(categoryId, 5);

            inventoryRedisService.tryDecrement(categoryId, 3);
            assertEquals(2, inventoryRedisService.getAvailable(categoryId));

            inventoryRedisService.release(categoryId, 2);
            assertEquals(4, inventoryRedisService.getAvailable(categoryId));

            inventoryRedisService.release(categoryId, 1);
            assertEquals(5, inventoryRedisService.getAvailable(categoryId));
        }
    }

    // =========================================================================
    // 3. OPERATION INVENTORY API TESTS
    // =========================================================================
    @Nested
    @DisplayName("Operation Inventory API Tests (POST /api/operation/concerts/{id}/ticket-categories/{categoryId}/inventory)")
    class OperationInventoryApiTests {

        @Test
        @DisplayName("Admin sets total inventory - saves to DB and pre-warms Redis")
        void setInventory_AsAdmin_Success() throws Exception {
            TicketCategory category = categoryRepository.save(TicketCategory.builder()
                    .concertId(testConcert.getId())
                    .name("VIP Admin Set " + UUID.randomUUID().toString().substring(0, 5))
                    .price(new BigDecimal("1500000.00"))
                    .maxPerBooking(4)
                    .status(TicketCategoryStatus.ACTIVE)
                    .build());

            int newTotalQuantity = 600;

            mockMvc.perform(post("/api/operation/concerts/" + testConcert.getId() + "/ticket-categories/" + category.getId() + "/inventory")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("totalQuantity", String.valueOf(newTotalQuantity))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.ticketCategoryId").value(category.getId()))
                    .andExpect(jsonPath("$.data.totalQuantity").value(newTotalQuantity))
                    .andExpect(jsonPath("$.data.reservedQuantity").value(0))
                    .andExpect(jsonPath("$.data.soldQuantity").value(0));

            // Verify Database
            TicketInventory savedInventory = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(newTotalQuantity, savedInventory.getTotalQuantity());
            assertEquals(0, savedInventory.getReservedQuantity());
            assertEquals(0, savedInventory.getSoldQuantity());

            // Verify Redis
            assertEquals(newTotalQuantity, inventoryRedisService.getAvailable(category.getId()));
        }

        @Test
        @DisplayName("Admin updates inventory for category with existing reserved and sold tickets - calculates available accurately for Redis")
        void setInventory_UpdateExisting_CalculatesAvailableCorrectly() throws Exception {
            // Existing category with total=100, reserved=20, sold=30 -> available was 50
            TicketCategory category = createCategoryWithInventory("Update Inv Cat", new BigDecimal("750000.00"), 4, 100, 20, 30);

            int updatedTotal = 250;

            mockMvc.perform(post("/api/operation/concerts/" + testConcert.getId() + "/ticket-categories/" + category.getId() + "/inventory")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("totalQuantity", String.valueOf(updatedTotal))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalQuantity").value(updatedTotal))
                    .andExpect(jsonPath("$.data.reservedQuantity").value(20))
                    .andExpect(jsonPath("$.data.soldQuantity").value(30));

            // Verify Database
            TicketInventory updated = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(updatedTotal, updated.getTotalQuantity());
            assertEquals(20, updated.getReservedQuantity());
            assertEquals(30, updated.getSoldQuantity());

            // Verify Redis: available = 250 - 20 - 30 = 200
            assertEquals(200, inventoryRedisService.getAvailable(category.getId()));
        }

        @Test
        @DisplayName("Customer user setting inventory returns 403 Forbidden")
        void setInventory_AsCustomer_ReturnsForbidden() throws Exception {
            TicketCategory category = createCategoryWithInventory("Cust Forbidden Cat", new BigDecimal("500000.00"), 4, 50, 0, 0);

            mockMvc.perform(post("/api/operation/concerts/" + testConcert.getId() + "/ticket-categories/" + category.getId() + "/inventory")
                            .header("Authorization", "Bearer " + customerToken)
                            .param("totalQuantity", "200"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated request to set inventory returns 401 Unauthorized")
        void setInventory_Unauthenticated_ReturnsUnauthorized() throws Exception {
            mockMvc.perform(post("/api/operation/concerts/" + testConcert.getId() + "/ticket-categories/1/inventory")
                            .param("totalQuantity", "200"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // 4. INVENTORY LIFECYCLE ACROSS BOOKING PROCESS
    // =========================================================================
    @Nested
    @DisplayName("Inventory Lifecycle across Booking Workflow")
    class InventoryBookingLifecycleTests {

        @Test
        @DisplayName("Create booking -> reserves inventory in DB and fast-decrements Redis")
        void createBooking_ReservesInventoryInDbAndRedis() {
            User user = createUniqueCustomer("bk_reserve");
            TicketCategory category = createCategoryWithInventory("Reserve Lifecycle Cat", new BigDecimal("1000000.00"), 4, 50, 0, 0);

            BookingItemRequest itemReq = new BookingItemRequest();
            itemReq.setTicketCategoryId(category.getId());
            itemReq.setQuantity(3);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(itemReq));

            var bookingResponse = bookingService.createBooking(user.getId(), request, "idem-inv-res-" + UUID.randomUUID());
            assertNotNull(bookingResponse);
            assertEquals(BookingStatus.RECEIVED, bookingResponse.getStatus());

            // Verify Database: reservedQuantity = 3, soldQuantity = 0
            TicketInventory dbInventory = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(3, dbInventory.getReservedQuantity());
            assertEquals(0, dbInventory.getSoldQuantity());

            // Verify Redis: available = 50 - 3 = 47
            assertEquals(47, inventoryRedisService.getAvailable(category.getId()));
        }

        @Test
        @DisplayName("Confirm payment -> moves reservedQuantity to soldQuantity in DB without double decrementing Redis")
        void confirmPayment_MovesReservedToSold() {
            User user = createUniqueCustomer("bk_confirm");
            TicketCategory category = createCategoryWithInventory("Confirm Lifecycle Cat", new BigDecimal("1200000.00"), 4, 20, 0, 0);

            BookingItemRequest itemReq = new BookingItemRequest();
            itemReq.setTicketCategoryId(category.getId());
            itemReq.setQuantity(2);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(itemReq));

            var bookingResponse = bookingService.createBooking(user.getId(), request, "idem-inv-conf-" + UUID.randomUUID());
            Long bookingId = bookingResponse.getId();

            // Confirm payment
            var paidBooking = bookingService.confirmPayment(bookingId, user.getId());
            assertEquals(BookingStatus.PAID, paidBooking.getStatus());

            // Verify Database: reservedQuantity shifted to soldQuantity
            TicketInventory dbInventory = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(0, dbInventory.getReservedQuantity());
            assertEquals(2, dbInventory.getSoldQuantity());

            // Verify Redis: remains 18 (fast-deducted during booking creation)
            assertEquals(18, inventoryRedisService.getAvailable(category.getId()));
        }

        @Test
        @DisplayName("Booking expiry -> restores reservedQuantity in DB and releases Redis inventory")
        void bookingExpiry_ReleasesInventoryInDbAndRedis() {
            User user = createUniqueCustomer("bk_expire");
            TicketCategory category = createCategoryWithInventory("Expire Lifecycle Cat", new BigDecimal("800000.00"), 4, 30, 0, 0);

            BookingItemRequest itemReq = new BookingItemRequest();
            itemReq.setTicketCategoryId(category.getId());
            itemReq.setQuantity(4);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(itemReq));

            var bookingResponse = bookingService.createBooking(user.getId(), request, "idem-inv-exp-" + UUID.randomUUID());

            // Check pre-expiry state
            assertEquals(4, inventoryRepository.findById(category.getId()).orElseThrow().getReservedQuantity());
            assertEquals(26, inventoryRedisService.getAvailable(category.getId()));

            // Expire booking
            Booking booking = bookingRepository.findById(bookingResponse.getId()).orElseThrow();
            bookingExpiryService.expireBooking(booking);

            // Verify Database: reservedQuantity reset to 0
            TicketInventory dbInventory = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(0, dbInventory.getReservedQuantity());
            assertEquals(0, dbInventory.getSoldQuantity());

            // Verify Redis: counter restored back to 30
            assertEquals(30, inventoryRedisService.getAvailable(category.getId()));
        }

        @Test
        @DisplayName("Admin cancels RECEIVED booking -> releases reservedQuantity in DB and increments Redis")
        void adminCancel_ReceivedBooking_ReleasesReservedInventory() {
            User user = createUniqueCustomer("admin_cancel_rec");
            TicketCategory category = createCategoryWithInventory("Admin Cancel Rec Cat", new BigDecimal("600000.00"), 4, 40, 0, 0);

            BookingItemRequest itemReq = new BookingItemRequest();
            itemReq.setTicketCategoryId(category.getId());
            itemReq.setQuantity(2);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(itemReq));

            var bookingResponse = bookingService.createBooking(user.getId(), request, "idem-admin-can-rec-" + UUID.randomUUID());

            // Admin cancels booking
            operationService.cancelBookingAndReleaseInventory(bookingResponse.getId(), adminUser.getId());

            // Verify booking status
            Booking cancelledBooking = bookingRepository.findById(bookingResponse.getId()).orElseThrow();
            assertEquals(BookingStatus.CANCELLED, cancelledBooking.getStatus());

            // Verify Database: reservedQuantity restored to 0
            TicketInventory dbInventory = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(0, dbInventory.getReservedQuantity());
            assertEquals(0, dbInventory.getSoldQuantity());

            // Verify Redis: restored to 40
            assertEquals(40, inventoryRedisService.getAvailable(category.getId()));
        }

        @Test
        @DisplayName("Admin cancels PAID booking -> releases soldQuantity in DB and increments Redis")
        void adminCancel_PaidBooking_ReleasesSoldInventory() {
            User user = createUniqueCustomer("admin_cancel_paid");
            TicketCategory category = createCategoryWithInventory("Admin Cancel Paid Cat", new BigDecimal("900000.00"), 4, 25, 0, 0);

            BookingItemRequest itemReq = new BookingItemRequest();
            itemReq.setTicketCategoryId(category.getId());
            itemReq.setQuantity(3);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(itemReq));

            var bookingResponse = bookingService.createBooking(user.getId(), request, "idem-admin-can-paid-" + UUID.randomUUID());
            bookingService.confirmPayment(bookingResponse.getId(), user.getId());

            // Check pre-cancel state
            assertEquals(3, inventoryRepository.findById(category.getId()).orElseThrow().getSoldQuantity());
            assertEquals(22, inventoryRedisService.getAvailable(category.getId()));

            // Admin cancels paid booking
            operationService.cancelBookingAndReleaseInventory(bookingResponse.getId(), adminUser.getId());

            // Verify booking status
            Booking cancelledBooking = bookingRepository.findById(bookingResponse.getId()).orElseThrow();
            assertEquals(BookingStatus.CANCELLED, cancelledBooking.getStatus());

            // Verify Database: soldQuantity decremented back to 0
            TicketInventory dbInventory = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(0, dbInventory.getReservedQuantity());
            assertEquals(0, dbInventory.getSoldQuantity());

            // Verify Redis: counter restored back to 25
            assertEquals(25, inventoryRedisService.getAvailable(category.getId()));
        }
    }

    // =========================================================================
    // 5. HIGH-CONCURRENCY & OVERBOOKING PREVENTION TESTS
    // =========================================================================
    @Nested
    @DisplayName("Concurrency & Race Condition Prevention Tests")
    class ConcurrencyAndPessimisticLockTests {

        @Test
        @DisplayName("High concurrency: 20 simultaneous bookings for 6 available tickets - exactly 6 succeed and 14 fail, zero oversell")
        void concurrentBooking_PreventsOverbookingUnderHighLoad() throws InterruptedException {
            int availableTickets = 6;
            int totalAttempts = 20;

            TicketCategory category = createCategoryWithInventory("Flash Concurrency Cat", new BigDecimal("500000.00"), 1, availableTickets, 0, 0);

            ExecutorService executor = Executors.newFixedThreadPool(totalAttempts);
            CountDownLatch readyLatch = new CountDownLatch(totalAttempts);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(totalAttempts);

            AtomicInteger successfulBookings = new AtomicInteger(0);
            AtomicInteger failedBookings = new AtomicInteger(0);

            for (int i = 0; i < totalAttempts; i++) {
                final int index = i;
                User user = createUniqueCustomer("conc_inv_user_" + index);

                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();

                        BookingItemRequest item = new BookingItemRequest();
                        item.setTicketCategoryId(category.getId());
                        item.setQuantity(1);

                        CreateBookingRequest request = new CreateBookingRequest();
                        request.setItems(List.of(item));

                        bookingService.createBooking(user.getId(), request, "idem-conc-inv-" + index + "-" + UUID.randomUUID());
                        successfulBookings.incrementAndGet();
                    } catch (Exception ex) {
                        failedBookings.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // Wait for all worker threads to be ready
            readyLatch.await(5, TimeUnit.SECONDS);
            // Unleash all threads concurrently
            startLatch.countDown();
            boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(completed, "All concurrent booking requests must finish within timeout");
            assertEquals(availableTickets, successfulBookings.get(), "Exactly " + availableTickets + " bookings must succeed");
            assertEquals(totalAttempts - availableTickets, failedBookings.get(), "Remaining attempts must fail due to sold out");

            // Verify Database inventory
            TicketInventory finalDbInventory = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(availableTickets, finalDbInventory.getReservedQuantity());
            assertEquals(0, finalDbInventory.getSoldQuantity());
            assertEquals(availableTickets, finalDbInventory.getTotalQuantity());

            // Verify Redis counter is exactly 0 (no negative numbers)
            assertEquals(0, inventoryRedisService.getAvailable(category.getId()));
        }

        @Test
        @DisplayName("High concurrency: Multi-quantity requests preventing partial or broken oversell")
        void concurrentBooking_MultiQuantity_MaintainsIntegrity() throws InterruptedException {
            int totalTickets = 10;
            int totalThreads = 10; // each requests 2 tickets (total requested = 20 tickets for 10 available)

            TicketCategory category = createCategoryWithInventory("Multi Qty Concurrency Cat", new BigDecimal("700000.00"), 4, totalTickets, 0, 0);

            ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
            CountDownLatch readyLatch = new CountDownLatch(totalThreads);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(totalThreads);

            AtomicInteger successfulCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            for (int i = 0; i < totalThreads; i++) {
                final int index = i;
                User user = createUniqueCustomer("conc_multi_user_" + index);

                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();

                        BookingItemRequest item = new BookingItemRequest();
                        item.setTicketCategoryId(category.getId());
                        item.setQuantity(2);

                        CreateBookingRequest request = new CreateBookingRequest();
                        request.setItems(List.of(item));

                        bookingService.createBooking(user.getId(), request, "idem-multi-conc-" + index + "-" + UUID.randomUUID());
                        successfulCount.incrementAndGet();
                    } catch (Exception ex) {
                        failureCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();
            boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(completed);
            // Exactly 5 bookings (5 * 2 = 10 tickets) must succeed
            assertEquals(5, successfulCount.get());
            assertEquals(5, failureCount.get());

            // Verify Database: exactly 10 tickets reserved
            TicketInventory finalDb = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(10, finalDb.getReservedQuantity());

            // Verify Redis: exactly 0
            assertEquals(0, inventoryRedisService.getAvailable(category.getId()));
        }
    }
}
