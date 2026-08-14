package com.geekup.eventticketbookingservice.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geekup.eventticketbookingservice.AbstractIntegrationTest;
import com.geekup.eventticketbookingservice.booking.dto.BookingItemRequest;
import com.geekup.eventticketbookingservice.booking.dto.CreateBookingRequest;
import com.geekup.eventticketbookingservice.catalog.*;
import com.geekup.eventticketbookingservice.common.exception.ErrorCode;
import com.geekup.eventticketbookingservice.security.JwtService;
import com.geekup.eventticketbookingservice.user.Role;
import com.geekup.eventticketbookingservice.user.User;
import com.geekup.eventticketbookingservice.user.UserRepository;
import com.geekup.eventticketbookingservice.voucher.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Booking Module Integration Tests")
public class BookingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private TicketCategoryRepository categoryRepository;

    @Autowired
    private TicketInventoryRepository inventoryRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingItemRepository bookingItemRepository;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private VoucherRedemptionRepository voucherRedemptionRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingExpiryService bookingExpiryService;

    @Autowired
    private com.geekup.eventticketbookingservice.inventory.InventoryRedisService inventoryRedisService;

    private User primaryCustomer;
    private String primaryToken;

    private User secondaryCustomer;
    private String secondaryToken;

    @BeforeEach
    void setUpUsers() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        primaryCustomer = userRepository.findByEmail("customer1@example.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("customer1@example.com")
                        .fullName("Customer One")
                        .password("$2a$12$H/tnE.97GdULmDOTk4MRO.y6rRv/4f30bKA4IV2RESH2b9nX49wQu")
                        .role(Role.CUSTOMER)
                        .status("ACTIVE")
                        .build()));
        primaryToken = jwtService.generateToken(primaryCustomer);

        secondaryCustomer = userRepository.findByEmail("customer2@example.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("customer2@example.com")
                        .fullName("Customer Two")
                        .password("$2a$12$H/tnE.97GdULmDOTk4MRO.y6rRv/4f30bKA4IV2RESH2b9nX49wQu")
                        .role(Role.CUSTOMER)
                        .status("ACTIVE")
                        .build()));
        secondaryToken = jwtService.generateToken(secondaryCustomer);

        // Prewarm inventory for all categories in Redis
        for (TicketInventory inv : inventoryRepository.findAll()) {
            int available = inv.getTotalQuantity() - inv.getReservedQuantity() - inv.getSoldQuantity();
            inventoryRedisService.preWarm(inv.getTicketCategoryId(), Math.max(0, available));
        }
    }

    private User createUniqueCustomer(String prefix) {
        String uniqueEmail = prefix + "_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        User user = User.builder()
                .email(uniqueEmail)
                .fullName("Test User " + prefix)
                .password("$2a$12$H/tnE.97GdULmDOTk4MRO.y6rRv/4f30bKA4IV2RESH2b9nX49wQu")
                .role(Role.CUSTOMER)
                .status("ACTIVE")
                .build();
        return userRepository.save(user);
    }

    private TicketCategory createTestCategoryWithInventory(Long concertId, String name, BigDecimal price, int maxPerBooking, int totalQty, int reservedQty, int soldQty) {
        TicketCategory category = categoryRepository.save(TicketCategory.builder()
                .concertId(concertId)
                .name(name)
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

        int available = totalQty - reservedQty - soldQty;
        inventoryRedisService.preWarm(category.getId(), Math.max(0, available));

        return category;
    }

    // =========================================================================
    // 1. CREATE BOOKING TESTS
    // =========================================================================
    @Nested
    @DisplayName("Create Booking Tests (POST /api/bookings)")
    class CreateBookingTests {

        @Test
        @DisplayName("Create booking successfully - updates DB and reserves inventory")
        void createBooking_Success() throws Exception {
            User user = createUniqueCustomer("booking_success");
            String token = jwtService.generateToken(user);

            // Category 1 is 'VIP SVIP' (price 3500000.00, max_per_booking 4)
            TicketCategory category = categoryRepository.findById(1L).orElseThrow();
            TicketInventory initialInventory = inventoryRepository.findById(category.getId()).orElseThrow();
            int initialReserved = initialInventory.getReservedQuantity();

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(2);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));

            String idempotencyKey = "idem-" + UUID.randomUUID();

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").isNumber())
                    .andExpect(jsonPath("$.data.bookingCode").value(startsWith("BK-")))
                    .andExpect(jsonPath("$.data.userId").value(user.getId()))
                    .andExpect(jsonPath("$.data.status").value("RECEIVED"))
                    .andExpect(jsonPath("$.data.subtotal").value(7000000.00))
                    .andExpect(jsonPath("$.data.discountAmount").value(0.00))
                    .andExpect(jsonPath("$.data.totalAmount").value(7000000.00))
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andExpect(jsonPath("$.data.items[0].ticketCategoryId").value(category.getId()))
                    .andExpect(jsonPath("$.data.items[0].quantity").value(2));

            // Verify database state
            TicketInventory updatedInventory = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(initialReserved + 2, updatedInventory.getReservedQuantity());

            var savedBooking = bookingRepository.findByUserIdAndIdempotencyKey(user.getId(), idempotencyKey);
            assertTrue(savedBooking.isPresent());
            assertEquals(BookingStatus.RECEIVED, savedBooking.get().getStatus());

            var items = bookingItemRepository.findByBookingId(savedBooking.get().getId());
            assertEquals(1, items.size());
            assertEquals(2, items.get(0).getQuantity());
        }

        @Test
        @DisplayName("Idempotency check - repeating same request with same key returns identical booking and does not double deduct inventory")
        void createBooking_Idempotency_ReturnsSameBooking() throws Exception {
            User user = createUniqueCustomer("idempotency");
            String token = jwtService.generateToken(user);

            TicketCategory category = categoryRepository.findById(1L).orElseThrow();
            TicketInventory initialInventory = inventoryRepository.findById(category.getId()).orElseThrow();
            int initialReserved = initialInventory.getReservedQuantity();

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(2);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));

            String idempotencyKey = "idem-duplicate-" + UUID.randomUUID();

            // First call
            MvcResult firstResult = mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andReturn();

            String firstResponseJson = firstResult.getResponse().getContentAsString();

            // Second call with same idempotency key
            MvcResult secondResult = mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andReturn();

            // Compare data response
            var firstData = objectMapper.readTree(firstResult.getResponse().getContentAsString()).get("data");
            var secondData = objectMapper.readTree(secondResult.getResponse().getContentAsString()).get("data");

            assertEquals(firstData.get("id").asLong(), secondData.get("id").asLong());
            assertEquals(firstData.get("bookingCode").asText(), secondData.get("bookingCode").asText());
            assertEquals(firstData.get("status").asText(), secondData.get("status").asText());
            assertEquals(firstData.get("userId").asLong(), secondData.get("userId").asLong());
            assertEquals(firstData.get("totalAmount").asDouble(), secondData.get("totalAmount").asDouble(), 0.001);
            assertEquals(firstData.get("items").size(), secondData.get("items").size());

            // Verify inventory reserved only once
            TicketInventory finalInventory = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(initialReserved + 2, finalInventory.getReservedQuantity());

            // Verify only 1 booking created in DB
            List<Booking> userBookings = bookingRepository.findByUserId(user.getId());
            assertEquals(1, userBookings.size());
        }

        @Test
        @DisplayName("Create booking with Percentage Voucher - applies discount and creates redemption record")
        void createBooking_WithPercentageVoucher_Success() throws Exception {
            User user = createUniqueCustomer("voucher_pct");
            String token = jwtService.generateToken(user);

            // SUMMER2026: 10% discount
            Voucher voucher = voucherRepository.findByCode("SUMMER2026").orElseThrow();
            int initialRedeemedCount = voucher.getRedeemedCount();

            // Category 3: CAT 2 (price 1200000.00)
            TicketCategory category = categoryRepository.findById(3L).orElseThrow();

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(2); // Subtotal = 2,400,000

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));
            request.setVoucherCode("SUMMER2026");

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "idem-voucher-pct-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.subtotal").value(2400000.00))
                    .andExpect(jsonPath("$.data.discountAmount").value(240000.00)) // 10% of 2,400,000
                    .andExpect(jsonPath("$.data.totalAmount").value(2160000.00));

            // Verify voucher redeemed count incremented
            Voucher updatedVoucher = voucherRepository.findById(voucher.getId()).orElseThrow();
            assertEquals(initialRedeemedCount + 1, updatedVoucher.getRedeemedCount());

            // Verify voucher redemption record exists
            assertTrue(voucherRedemptionRepository.existsByVoucherIdAndUserId(voucher.getId(), user.getId()));
        }

        @Test
        @DisplayName("Create booking with Fixed Voucher - applies fixed discount correctly")
        void createBooking_WithFixedVoucher_Success() throws Exception {
            User user = createUniqueCustomer("voucher_fixed");
            String token = jwtService.generateToken(user);

            // WELCOME50: 50,000 fixed discount
            TicketCategory category = categoryRepository.findById(4L).orElseThrow(); // Standard: 600,000

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(1); // Subtotal = 600,000

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));
            request.setVoucherCode("WELCOME50");

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "idem-voucher-fixed-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.subtotal").value(600000.00))
                    .andExpect(jsonPath("$.data.discountAmount").value(50000.00))
                    .andExpect(jsonPath("$.data.totalAmount").value(550000.00));
        }

        @Test
        @DisplayName("Reusing same voucher by same user returns 409 Conflict (VOUCHER_ALREADY_REDEEMED)")
        void createBooking_ReuseSameVoucher_ThrowsConflict() throws Exception {
            User user = createUniqueCustomer("voucher_reuse");
            String token = jwtService.generateToken(user);

            TicketCategory category = categoryRepository.findById(4L).orElseThrow();

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(1);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));
            request.setVoucherCode("WELCOME50");

            // 1st booking - success
            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "idem-reuse-1-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // 2nd booking with different idempotency key but same voucher -> conflict
            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "idem-reuse-2-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.VOUCHER_ALREADY_REDEEMED.getCode()));
        }
    }

    // =========================================================================
    // 2. VALIDATION & ERROR SCENARIOS
    // =========================================================================
    @Nested
    @DisplayName("Validation & Error Scenarios")
    class ValidationErrorTests {

        @Test
        @DisplayName("Booking with non-existent ticket category returns 404 Not Found")
        void createBooking_NonExistentCategory_ThrowsNotFound() throws Exception {
            User user = createUniqueCustomer("cat_not_found");
            String token = jwtService.generateToken(user);

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(99999L);
            item.setQuantity(1);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "idem-err-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.TICKET_CATEGORY_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("Booking quantity exceeding maxPerBooking returns 409 Conflict")
        void createBooking_ExceedsMaxPerBooking_ThrowsConflict() throws Exception {
            User user = createUniqueCustomer("exceed_max");
            String token = jwtService.generateToken(user);

            // Category 5 (VIP Standing) has maxPerBooking = 2
            TicketCategory category = categoryRepository.findById(5L).orElseThrow();

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(category.getMaxPerBooking() + 1); // quantity = 3 > 2

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "idem-err-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_ENOUGH_TICKETS.getCode()));
        }

        @Test
        @DisplayName("Booking when tickets are sold out returns 409 Conflict (TICKET_SOLD_OUT)")
        void createBooking_SoldOut_ThrowsConflict() throws Exception {
            User user = createUniqueCustomer("sold_out");
            String token = jwtService.generateToken(user);

            // Create a test category with 0 available tickets (total=10, reserved=5, sold=5)
            TicketCategory category = createTestCategoryWithInventory(1L, "Sold Out Category", new BigDecimal("500000.00"), 4, 10, 5, 5);

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(1);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "idem-err-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.TICKET_SOLD_OUT.getCode()));
        }

        @Test
        @DisplayName("Booking concert outside sale window returns 404 Not Found")
        void createBooking_OutsideSalePeriod_ThrowsNotFound() throws Exception {
            User user = createUniqueCustomer("outside_sale");
            String token = jwtService.generateToken(user);

            // Create a concert with sale window in the future
            Concert futureConcert = concertRepository.save(Concert.builder()
                    .name("Future Concert 2030")
                    .description("Future event")
                    .venue("Future Stadium")
                    .startAt(ZonedDateTime.now().plusYears(1))
                    .endAt(ZonedDateTime.now().plusYears(1).plusHours(3))
                    .saleStartAt(ZonedDateTime.now().plusDays(10)) // sale hasn't started
                    .saleEndAt(ZonedDateTime.now().plusDays(20))
                    .status(ConcertStatus.PUBLISHED)
                    .build());

            TicketCategory category = createTestCategoryWithInventory(futureConcert.getId(), "Early Bird", new BigDecimal("1000000.00"), 4, 100, 0, 0);

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(1);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "idem-err-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.CONCERT_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("Booking with invalid voucher code returns 404 Not Found")
        void createBooking_InvalidVoucher_ThrowsNotFound() throws Exception {
            User user = createUniqueCustomer("invalid_vouch");
            String token = jwtService.generateToken(user);

            TicketCategory category = categoryRepository.findById(1L).orElseThrow();

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(1);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));
            request.setVoucherCode("NON_EXISTENT_VOUCHER");

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "idem-err-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.VOUCHER_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("Booking with empty items list returns 400 Bad Request (VALIDATION_ERROR)")
        void createBooking_EmptyItems_Returns400BadRequest() throws Exception {
            User user = createUniqueCustomer("val_empty");
            String token = jwtService.generateToken(user);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of());

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "idem-val-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Booking with negative quantity returns 400 Bad Request (VALIDATION_ERROR)")
        void createBooking_NegativeQuantity_Returns400BadRequest() throws Exception {
            User user = createUniqueCustomer("val_neg");
            String token = jwtService.generateToken(user);

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(1L);
            item.setQuantity(-1);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "idem-val-neg-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Booking with null category id returns 400 Bad Request (VALIDATION_ERROR)")
        void createBooking_NullCategoryId_Returns400BadRequest() throws Exception {
            User user = createUniqueCustomer("val_null_cat");
            String token = jwtService.generateToken(user);

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(null);
            item.setQuantity(2);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "idem-val-null-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    // =========================================================================
    // 3. GET BOOKING & SECURITY AUTHORIZATION TESTS
    // =========================================================================
    @Nested
    @DisplayName("Query & Authorization Tests (GET /api/bookings)")
    class QueryAndAuthTests {

        @Test
        @DisplayName("Get user bookings returns only bookings belonging to authenticated user")
        void getUserBookings_ReturnsOnlyUserBookings() throws Exception {
            User user1 = createUniqueCustomer("query_u1");
            String token1 = jwtService.generateToken(user1);

            User user2 = createUniqueCustomer("query_u2");
            String token2 = jwtService.generateToken(user2);

            TicketCategory category = categoryRepository.findById(1L).orElseThrow();

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(1);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));

            // Create booking for user1
            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token1)
                            .header("Idempotency-Key", "idem-u1-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // Create booking for user2
            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token2)
                            .header("Idempotency-Key", "idem-u2-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // Get bookings as user1 -> only 1 booking returned
            mockMvc.perform(get("/api/bookings")
                            .header("Authorization", "Bearer " + token1))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].userId").value(user1.getId()));
        }

        @Test
        @DisplayName("Get booking by ID as owner returns 200 OK")
        void getBookingById_AsOwner_Success() throws Exception {
            User user = createUniqueCustomer("owner_get");
            String token = jwtService.generateToken(user);

            TicketCategory category = categoryRepository.findById(1L).orElseThrow();
            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(1);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));

            MvcResult result = mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "idem-get-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andReturn();

            long bookingId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();

            mockMvc.perform(get("/api/bookings/" + bookingId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(bookingId))
                    .andExpect(jsonPath("$.data.userId").value(user.getId()));
        }

        @Test
        @DisplayName("Get booking by ID as another user returns 403 Forbidden")
        void getBookingById_AsAnotherUser_ThrowsForbidden() throws Exception {
            User owner = createUniqueCustomer("owner_sec");
            String ownerToken = jwtService.generateToken(owner);

            User stranger = createUniqueCustomer("stranger_sec");
            String strangerToken = jwtService.generateToken(stranger);

            TicketCategory category = categoryRepository.findById(1L).orElseThrow();
            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(1);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));

            MvcResult result = mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + ownerToken)
                            .header("Idempotency-Key", "idem-sec-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andReturn();

            long bookingId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();

            // Stranger tries to get owner's booking
            mockMvc.perform(get("/api/bookings/" + bookingId)
                            .header("Authorization", "Bearer " + strangerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.FORBIDDEN.getCode()));
        }

        @Test
        @DisplayName("Unauthenticated request returns 401 Unauthorized")
        void unauthenticatedRequest_ReturnsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/bookings"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // 4. CONFIRM PAYMENT & PAYMENT FAILURE / EXPIRATION TESTS
    // =========================================================================
    @Nested
    @DisplayName("Payment Confirmation & Expiry Tests")
    class PaymentAndExpiryTests {

        @Test
        @DisplayName("Confirm payment successfully - moves reserved to sold inventory")
        void confirmPayment_Success() throws Exception {
            User user = createUniqueCustomer("pay_success");
            String token = jwtService.generateToken(user);

            TicketCategory category = categoryRepository.findById(2L).orElseThrow();
            TicketInventory initialInv = inventoryRepository.findById(category.getId()).orElseThrow();
            int initialReserved = initialInv.getReservedQuantity();
            int initialSold = initialInv.getSoldQuantity();

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(2);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));

            MvcResult createResult = mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "idem-pay-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andReturn();

            long bookingId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").get("id").asLong();

            // Confirm payment
            mockMvc.perform(post("/api/bookings/" + bookingId + "/confirm-payment")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(bookingId))
                    .andExpect(jsonPath("$.data.status").value("PAID"));

            // Verify inventory state in DB
            TicketInventory updatedInv = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(initialReserved, updatedInv.getReservedQuantity()); // reserved went up then back down
            assertEquals(initialSold + 2, updatedInv.getSoldQuantity()); // sold increased by 2

            Booking updatedBooking = bookingRepository.findById(bookingId).orElseThrow();
            assertEquals(BookingStatus.PAID, updatedBooking.getStatus());
        }

        @Test
        @DisplayName("Confirm payment on already PAID booking returns 400 Bad Request (INVALID_BOOKING_STATUS)")
        void confirmPayment_AlreadyPaid_ThrowsBadRequest() throws Exception {
            User user = createUniqueCustomer("pay_twice");
            String token = jwtService.generateToken(user);

            TicketCategory category = categoryRepository.findById(2L).orElseThrow();
            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(1);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));

            MvcResult createResult = mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "idem-pay2-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andReturn();

            long bookingId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").get("id").asLong();

            // First confirmation - OK
            mockMvc.perform(post("/api/bookings/" + bookingId + "/confirm-payment")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            // Second confirmation - Fails
            mockMvc.perform(post("/api/bookings/" + bookingId + "/confirm-payment")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_BOOKING_STATUS.getCode()));
        }

        @Test
        @DisplayName("Confirm payment on EXPIRED booking returns 400 Bad Request")
        void confirmPayment_ExpiredBooking_ThrowsBadRequest() throws Exception {
            User user = createUniqueCustomer("pay_expired");
            String token = jwtService.generateToken(user);

            TicketCategory category = categoryRepository.findById(2L).orElseThrow();
            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(1);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));

            MvcResult createResult = mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "idem-exp-pay-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andReturn();

            long bookingId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").get("id").asLong();

            // Expire the booking
            Booking booking = bookingRepository.findById(bookingId).orElseThrow();
            bookingExpiryService.expireBooking(booking);

            // Attempt payment on expired booking
            mockMvc.perform(post("/api/bookings/" + bookingId + "/confirm-payment")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_BOOKING_STATUS.getCode()));
        }

        @Test
        @DisplayName("Booking Expiry Service - marks booking as EXPIRED, releases reserved inventory and reverts voucher")
        void bookingExpiryService_ReleasesInventoryAndVoucher() {
            User user = createUniqueCustomer("expiry_test");

            TicketCategory category = createTestCategoryWithInventory(1L, "Expiry Category", new BigDecimal("800000.00"), 4, 10, 0, 0);

            // Create a dedicated voucher
            Voucher voucher = voucherRepository.save(Voucher.builder()
                    .name("Flash Expiry Voucher")
                    .code("EXPIRY_VOUCHER_" + UUID.randomUUID().toString().substring(0, 6))
                    .discountType(DiscountType.FIXED)
                    .discountValue(new BigDecimal("100000.00"))
                    .maxRedemptions(10)
                    .redeemedCount(0)
                    .maxPerUser(1)
                    .startsAt(ZonedDateTime.now().minusDays(1))
                    .endsAt(ZonedDateTime.now().plusDays(10))
                    .status(VoucherStatus.ACTIVE)
                    .build());

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(2);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));
            request.setVoucherCode(voucher.getCode());

            var bookingResponse = bookingService.createBooking(user.getId(), request, "idem-exp-srv-" + UUID.randomUUID());
            assertNotNull(bookingResponse);
            assertEquals(BookingStatus.RECEIVED, bookingResponse.getStatus());

            // Verify reserved inventory and voucher redemption
            TicketInventory invAfterBooking = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(2, invAfterBooking.getReservedQuantity());

            Voucher vAfterBooking = voucherRepository.findById(voucher.getId()).orElseThrow();
            assertEquals(1, vAfterBooking.getRedeemedCount());
            assertTrue(voucherRedemptionRepository.existsByVoucherIdAndUserId(voucher.getId(), user.getId()));

            // Trigger Expiry
            Booking booking = bookingRepository.findById(bookingResponse.getId()).orElseThrow();
            bookingExpiryService.expireBooking(booking);

            // Assertions after expiry
            Booking expiredBooking = bookingRepository.findById(bookingResponse.getId()).orElseThrow();
            assertEquals(BookingStatus.EXPIRED, expiredBooking.getStatus());

            TicketInventory invAfterExpiry = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(0, invAfterExpiry.getReservedQuantity()); // Reserved released!

            Voucher vAfterExpiry = voucherRepository.findById(voucher.getId()).orElseThrow();
            assertEquals(0, vAfterExpiry.getRedeemedCount()); // Redeemed count decremented back!

            assertFalse(voucherRedemptionRepository.existsByVoucherIdAndUserId(voucher.getId(), user.getId())); // Redemption deleted!
        }
    }

    // =========================================================================
    // 5. CONCURRENCY & OVERBOOKING PREVENTION TEST
    // =========================================================================
    @Nested
    @DisplayName("Concurrency & Race Condition Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent booking on limited inventory - prevents overbooking using DB row-level locking")
        void concurrentBookings_PreventsOverbooking() throws InterruptedException {
            int totalTickets = 5;
            int concurrentAttempts = 10;

            // Create a category with exactly totalTickets available
            TicketCategory category = createTestCategoryWithInventory(1L, "Limited Concurrency Cat", new BigDecimal("100000.00"), 1, totalTickets, 0, 0);

            ExecutorService executor = Executors.newFixedThreadPool(concurrentAttempts);
            CountDownLatch readyLatch = new CountDownLatch(concurrentAttempts);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(concurrentAttempts);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            List<String> failureReasons = new CopyOnWriteArrayList<>();

            for (int i = 0; i < concurrentAttempts; i++) {
                final int userIndex = i;
                User user = createUniqueCustomer("concurrency_user_" + userIndex);

                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();

                        BookingItemRequest item = new BookingItemRequest();
                        item.setTicketCategoryId(category.getId());
                        item.setQuantity(1);

                        CreateBookingRequest request = new CreateBookingRequest();
                        request.setItems(List.of(item));

                        bookingService.createBooking(user.getId(), request, "idem-concurrent-" + userIndex + "-" + UUID.randomUUID());
                        successCount.incrementAndGet();
                    } catch (Exception ex) {
                        failureCount.incrementAndGet();
                        failureReasons.add(ex.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // Wait for all threads to be ready, then trigger simultaneously
            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();
            boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(completed, "All concurrent tasks should complete within timeout");
            assertEquals(totalTickets, successCount.get(), "Exactly " + totalTickets + " bookings should succeed");
            assertEquals(concurrentAttempts - totalTickets, failureCount.get(), "Remaining attempts should fail");

            // Verify final inventory state in PostgreSQL
            TicketInventory finalInventory = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(totalTickets, finalInventory.getReservedQuantity());
            assertEquals(0, finalInventory.getSoldQuantity());
            assertEquals(totalTickets, finalInventory.getTotalQuantity());
        }
    }
}
