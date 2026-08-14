package com.geekup.eventticketbookingservice.voucher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geekup.eventticketbookingservice.AbstractIntegrationTest;
import com.geekup.eventticketbookingservice.booking.Booking;
import com.geekup.eventticketbookingservice.booking.BookingExpiryService;
import com.geekup.eventticketbookingservice.booking.BookingRepository;
import com.geekup.eventticketbookingservice.booking.BookingStatus;
import com.geekup.eventticketbookingservice.booking.dto.BookingItemRequest;
import com.geekup.eventticketbookingservice.booking.dto.CreateBookingRequest;
import com.geekup.eventticketbookingservice.catalog.TicketCategory;
import com.geekup.eventticketbookingservice.catalog.TicketCategoryRepository;
import com.geekup.eventticketbookingservice.catalog.TicketInventory;
import com.geekup.eventticketbookingservice.catalog.TicketInventoryRepository;
import com.geekup.eventticketbookingservice.common.exception.AppException;
import com.geekup.eventticketbookingservice.common.exception.ErrorCode;
import com.geekup.eventticketbookingservice.inventory.InventoryRedisService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Voucher Module Integration Tests")
public class VoucherIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private VoucherRedemptionRepository voucherRedemptionRepository;

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private OperationService operationService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingExpiryService bookingExpiryService;

    @Autowired
    private TicketCategoryRepository categoryRepository;

    @Autowired
    private TicketInventoryRepository inventoryRepository;

    @Autowired
    private InventoryRedisService inventoryRedisService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private User adminUser;
    private String adminToken;

    private User customerUser;
    private String customerToken;

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

    private Voucher createTestVoucher(String code, DiscountType type, BigDecimal value, int maxRedemptions, int redeemedCount, VoucherStatus status, ZonedDateTime startsAt, ZonedDateTime endsAt) {
        return voucherRepository.save(Voucher.builder()
                .name("Test Voucher " + code)
                .code(code)
                .discountType(type)
                .discountValue(value)
                .maxRedemptions(maxRedemptions)
                .redeemedCount(redeemedCount)
                .maxPerUser(1)
                .startsAt(startsAt != null ? startsAt : ZonedDateTime.now().minusDays(1))
                .endsAt(endsAt != null ? endsAt : ZonedDateTime.now().plusDays(10))
                .status(status)
                .build());
    }

    private Booking createTestBooking(User user) {
        return bookingRepository.save(Booking.builder()
                .bookingCode("BK-TEST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .userId(user.getId())
                .status(BookingStatus.RECEIVED)
                .subtotal(new BigDecimal("1000000.00"))
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("1000000.00"))
                .idempotencyKey("idem-test-" + UUID.randomUUID())
                .build());
    }

    private String createVoucherPayload(String name, String discountType, BigDecimal discountValue,
                                         int maxRedemptions, int maxPerUser,
                                         ZonedDateTime startsAt, ZonedDateTime endsAt) {
        return String.format("""
                {
                    "name": "%s",
                    "discountType": "%s",
                    "discountValue": %s,
                    "maxRedemptions": %d,
                    "maxPerUser": %d,
                    "startsAt": "%s",
                    "endsAt": "%s"
                }
                """, name, discountType, discountValue.toPlainString(), maxRedemptions, maxPerUser,
                startsAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                endsAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    // =========================================================================
    // 1. ADMIN VOUCHER MANAGEMENT API TESTS (/api/operation/vouchers)
    // =========================================================================
    @Nested
    @DisplayName("Admin Voucher Management API Tests (/api/operation/vouchers)")
    class AdminVoucherApiTests {

        @Test
        @DisplayName("POST /api/operation/vouchers - Admin creates voucher with explicit code successfully")
        void createVoucher_WithExplicitCode_Success() throws Exception {
            String customCode = "SUPERFIXED_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

            String payload = createVoucherPayload("Super Fixed Deal", "FIXED", new BigDecimal("100000.00"),
                    200, 1, ZonedDateTime.now().minusHours(1), ZonedDateTime.now().plusDays(15));

            mockMvc.perform(post("/api/operation/vouchers")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("code", customCode)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").isNumber())
                    .andExpect(jsonPath("$.data.code").value(customCode))
                    .andExpect(jsonPath("$.data.name").value("Super Fixed Deal"))
                    .andExpect(jsonPath("$.data.discountType").value("FIXED"))
                    .andExpect(jsonPath("$.data.discountValue").value(100000.00))
                    .andExpect(jsonPath("$.data.maxRedemptions").value(200))
                    .andExpect(jsonPath("$.data.redeemedCount").value(0))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));

            // Verify in DB
            Voucher saved = voucherRepository.findByCode(customCode).orElseThrow();
            assertEquals("Super Fixed Deal", saved.getName());
            assertEquals(DiscountType.FIXED, saved.getDiscountType());
            assertEquals(0, new BigDecimal("100000.00").compareTo(saved.getDiscountValue()));
            assertEquals(200, saved.getMaxRedemptions());
            assertEquals(0, saved.getRedeemedCount());
            assertEquals(VoucherStatus.ACTIVE, saved.getStatus());
        }

        @Test
        @DisplayName("POST /api/operation/vouchers - Admin creates voucher with auto-generated code prefix FLASH-")
        void createVoucher_WithAutoGeneratedCode_Success() throws Exception {
            String payload = createVoucherPayload("Flash Sale 15%", "PERCENTAGE", new BigDecimal("15.00"),
                    50, 1, ZonedDateTime.now().minusHours(1), ZonedDateTime.now().plusDays(5));

            MvcResult result = mockMvc.perform(post("/api/operation/vouchers")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.code").value(startsWith("FLASH-")))
                    .andExpect(jsonPath("$.data.discountType").value("PERCENTAGE"))
                    .andExpect(jsonPath("$.data.discountValue").value(15.00))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andReturn();

            String generatedCode = objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("data").get("code").asText();

            assertTrue(voucherRepository.findByCode(generatedCode).isPresent());
        }

        @Test
        @DisplayName("POST /api/operation/vouchers - Customer role returns 403 Forbidden")
        void createVoucher_AsCustomer_Returns403Forbidden() throws Exception {
            String payload = createVoucherPayload("Unauthorized Campaign", "FIXED", new BigDecimal("10000.00"),
                    10, 1, ZonedDateTime.now(), ZonedDateTime.now().plusDays(1));

            mockMvc.perform(post("/api/operation/vouchers")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /api/operation/vouchers - Unauthenticated request returns 401 Unauthorized")
        void createVoucher_Unauthenticated_Returns401Unauthorized() throws Exception {
            String payload = createVoucherPayload("Anonymous Request", "PERCENTAGE", new BigDecimal("10.00"),
                    10, 1, ZonedDateTime.now(), ZonedDateTime.now().plusDays(1));

            mockMvc.perform(post("/api/operation/vouchers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("PATCH /api/operation/vouchers/{id}/disable - Admin disables voucher successfully")
        void disableVoucher_Success() throws Exception {
            Voucher voucher = createTestVoucher("DISABLE_ME_" + UUID.randomUUID().toString().substring(0, 6),
                    DiscountType.FIXED, new BigDecimal("20000.00"), 10, 0, VoucherStatus.ACTIVE, null, null);

            mockMvc.perform(patch("/api/operation/vouchers/" + voucher.getId() + "/disable")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(voucher.getId()))
                    .andExpect(jsonPath("$.data.status").value("DISABLED"));

            Voucher updated = voucherRepository.findById(voucher.getId()).orElseThrow();
            assertEquals(VoucherStatus.DISABLED, updated.getStatus());
        }

        @Test
        @DisplayName("PATCH /api/operation/vouchers/{id}/disable - Non-existent voucher returns 404 Not Found")
        void disableVoucher_NonExistent_Returns404NotFound() throws Exception {
            mockMvc.perform(patch("/api/operation/vouchers/999999/disable")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.VOUCHER_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("PATCH /api/operation/vouchers/{id}/enable - Admin enables disabled voucher successfully")
        void enableVoucher_Success() throws Exception {
            Voucher voucher = createTestVoucher("ENABLE_ME_" + UUID.randomUUID().toString().substring(0, 6),
                    DiscountType.PERCENTAGE, new BigDecimal("25.00"), 50, 5, VoucherStatus.DISABLED, null, null);

            mockMvc.perform(patch("/api/operation/vouchers/" + voucher.getId() + "/enable")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(voucher.getId()))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));

            Voucher updated = voucherRepository.findById(voucher.getId()).orElseThrow();
            assertEquals(VoucherStatus.ACTIVE, updated.getStatus());
        }

        @Test
        @DisplayName("PATCH /api/operation/vouchers/{id}/enable - Customer role returns 403 Forbidden")
        void enableVoucher_AsCustomer_Returns403Forbidden() throws Exception {
            Voucher voucher = createTestVoucher("FORBIDDEN_ENABLE_" + UUID.randomUUID().toString().substring(0, 6),
                    DiscountType.FIXED, new BigDecimal("10000.00"), 10, 0, VoucherStatus.DISABLED, null, null);

            mockMvc.perform(patch("/api/operation/vouchers/" + voucher.getId() + "/enable")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // 2. VOUCHER SERVICE & DB STATE VALIDATION TESTS
    // =========================================================================
    @Nested
    @DisplayName("Voucher Service & State Validation Tests")
    class VoucherValidationAndServiceTests {

        @Test
        @DisplayName("validateAndLock succeeds on valid active voucher")
        void validateAndLock_Success() {
            User user = createUniqueCustomer("vlock_ok");
            String code = "VLOCK_OK_" + UUID.randomUUID().toString().substring(0, 6);
            Voucher voucher = createTestVoucher(code, DiscountType.FIXED, new BigDecimal("50000.00"), 10, 2, VoucherStatus.ACTIVE, null, null);

            Voucher locked = transactionTemplate.execute(status -> voucherService.validateAndLock(code, user.getId()));

            assertNotNull(locked);
            assertEquals(voucher.getId(), locked.getId());
            assertEquals(code, locked.getCode());
        }

        @Test
        @DisplayName("validateAndLock throws VOUCHER_NOT_FOUND when voucher code does not exist")
        void validateAndLock_NonExistentCode_ThrowsNotFound() {
            User user = createUniqueCustomer("vlock_nf");
            AppException ex = assertThrows(AppException.class, () ->
                    transactionTemplate.execute(status -> voucherService.validateAndLock("NON_EXISTENT_CODE_12345", user.getId())));

            assertEquals(ErrorCode.VOUCHER_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("validateAndLock throws VOUCHER_INVALID when status is DISABLED")
        void validateAndLock_DisabledStatus_ThrowsInvalid() {
            User user = createUniqueCustomer("vlock_dis");
            String code = "VLOCK_DIS_" + UUID.randomUUID().toString().substring(0, 6);
            createTestVoucher(code, DiscountType.PERCENTAGE, new BigDecimal("10.00"), 10, 0, VoucherStatus.DISABLED, null, null);

            AppException ex = assertThrows(AppException.class, () ->
                    transactionTemplate.execute(status -> voucherService.validateAndLock(code, user.getId())));

            assertEquals(ErrorCode.VOUCHER_INVALID, ex.getErrorCode());
        }

        @Test
        @DisplayName("validateAndLock throws VOUCHER_INVALID when status is USED_UP")
        void validateAndLock_UsedUpStatus_ThrowsInvalid() {
            User user = createUniqueCustomer("vlock_used");
            String code = "VLOCK_USED_" + UUID.randomUUID().toString().substring(0, 6);
            createTestVoucher(code, DiscountType.PERCENTAGE, new BigDecimal("10.00"), 10, 10, VoucherStatus.USED_UP, null, null);

            AppException ex = assertThrows(AppException.class, () ->
                    transactionTemplate.execute(status -> voucherService.validateAndLock(code, user.getId())));

            assertEquals(ErrorCode.VOUCHER_INVALID, ex.getErrorCode());
        }

        @Test
        @DisplayName("validateAndLock throws VOUCHER_INVALID when current time is before startsAt")
        void validateAndLock_NotYetStarted_ThrowsInvalid() {
            User user = createUniqueCustomer("vlock_future");
            String code = "VLOCK_FUTURE_" + UUID.randomUUID().toString().substring(0, 6);
            createTestVoucher(code, DiscountType.FIXED, new BigDecimal("30000.00"), 10, 0, VoucherStatus.ACTIVE,
                    ZonedDateTime.now().plusDays(2), ZonedDateTime.now().plusDays(10));

            AppException ex = assertThrows(AppException.class, () ->
                    transactionTemplate.execute(status -> voucherService.validateAndLock(code, user.getId())));

            assertEquals(ErrorCode.VOUCHER_INVALID, ex.getErrorCode());
        }

        @Test
        @DisplayName("validateAndLock throws VOUCHER_INVALID when current time is after endsAt")
        void validateAndLock_Expired_ThrowsInvalid() {
            User user = createUniqueCustomer("vlock_exp");
            String code = "VLOCK_EXP_" + UUID.randomUUID().toString().substring(0, 6);
            createTestVoucher(code, DiscountType.FIXED, new BigDecimal("30000.00"), 10, 0, VoucherStatus.ACTIVE,
                    ZonedDateTime.now().minusDays(10), ZonedDateTime.now().minusDays(1));

            AppException ex = assertThrows(AppException.class, () ->
                    transactionTemplate.execute(status -> voucherService.validateAndLock(code, user.getId())));

            assertEquals(ErrorCode.VOUCHER_INVALID, ex.getErrorCode());
        }

        @Test
        @DisplayName("validateAndLock throws VOUCHER_LIMIT_REACHED when redeemedCount reaches maxRedemptions")
        void validateAndLock_LimitReached_ThrowsLimitReached() {
            User user = createUniqueCustomer("vlock_limit");
            String code = "VLOCK_LIMIT_" + UUID.randomUUID().toString().substring(0, 6);
            createTestVoucher(code, DiscountType.FIXED, new BigDecimal("30000.00"), 5, 5, VoucherStatus.ACTIVE, null, null);

            AppException ex = assertThrows(AppException.class, () ->
                    transactionTemplate.execute(status -> voucherService.validateAndLock(code, user.getId())));

            assertEquals(ErrorCode.VOUCHER_LIMIT_REACHED, ex.getErrorCode());
        }

        @Test
        @DisplayName("validateAndLock throws VOUCHER_ALREADY_REDEEMED when user already redeemed voucher")
        void validateAndLock_AlreadyRedeemedByUser_ThrowsAlreadyRedeemed() {
            User user = createUniqueCustomer("vlock_redeemed");
            Booking booking = createTestBooking(user);
            String code = "VLOCK_RED_" + UUID.randomUUID().toString().substring(0, 6);
            Voucher voucher = createTestVoucher(code, DiscountType.FIXED, new BigDecimal("30000.00"), 100, 1, VoucherStatus.ACTIVE, null, null);

            // Record redemption for this user with valid booking ID
            voucherRedemptionRepository.save(VoucherRedemption.builder()
                    .voucherId(voucher.getId())
                    .userId(user.getId())
                    .bookingId(booking.getId())
                    .discountAmount(new BigDecimal("30000.00"))
                    .build());

            AppException ex = assertThrows(AppException.class, () ->
                    transactionTemplate.execute(status -> voucherService.validateAndLock(code, user.getId())));

            assertEquals(ErrorCode.VOUCHER_ALREADY_REDEEMED, ex.getErrorCode());
        }

        @Test
        @DisplayName("calculateDiscount accurately computes FIXED and PERCENTAGE discounts")
        void calculateDiscount_Computations() {
            // FIXED discount smaller than subtotal
            Voucher fixedVoucher = Voucher.builder()
                    .discountType(DiscountType.FIXED)
                    .discountValue(new BigDecimal("50000.00"))
                    .build();
            assertEquals(0, new BigDecimal("50000.00").compareTo(
                    voucherService.calculateDiscount(fixedVoucher, new BigDecimal("200000.00"))));

            // FIXED discount larger than subtotal -> capped at subtotal
            assertEquals(0, new BigDecimal("30000.00").compareTo(
                    voucherService.calculateDiscount(fixedVoucher, new BigDecimal("30000.00"))));

            // PERCENTAGE discount
            Voucher pctVoucher = Voucher.builder()
                    .discountType(DiscountType.PERCENTAGE)
                    .discountValue(new BigDecimal("15.00"))
                    .build();
            assertEquals(0, new BigDecimal("150000.00").compareTo(
                    voucherService.calculateDiscount(pctVoucher, new BigDecimal("1000000.00"))));
        }

        @Test
        @DisplayName("applyRedemption persists VoucherRedemption and updates Voucher status to USED_UP when max is reached")
        void applyRedemption_DirectStateTransition() {
            User user = createUniqueCustomer("apply_direct");
            Booking booking = createTestBooking(user);
            String code = "APPLY_DIR_" + UUID.randomUUID().toString().substring(0, 6);
            Voucher voucher = createTestVoucher(code, DiscountType.FIXED, new BigDecimal("25000.00"), 2, 1, VoucherStatus.ACTIVE, null, null);

            transactionTemplate.executeWithoutResult(status ->
                    voucherService.applyRedemption(voucher, user.getId(), booking.getId(), new BigDecimal("25000.00")));

            // Verify voucher in DB is updated to USED_UP with redeemedCount = 2
            Voucher updated = voucherRepository.findById(voucher.getId()).orElseThrow();
            assertEquals(2, updated.getRedeemedCount());
            assertEquals(VoucherStatus.USED_UP, updated.getStatus());

            // Verify redemption record exists
            assertTrue(voucherRedemptionRepository.existsByVoucherIdAndUserId(voucher.getId(), user.getId()));
        }
    }

    // =========================================================================
    // 3. BOOKING WITH VOUCHER INTEGRATION TESTS (POST /api/bookings)
    // =========================================================================
    @Nested
    @DisplayName("Booking Flow With Voucher Integration Tests")
    class BookingWithVoucherIntegrationTests {

        @Test
        @DisplayName("Create booking with Percentage Voucher applies discount and persists redemption")
        void booking_WithPercentageVoucher_Success() throws Exception {
            User user = createUniqueCustomer("bk_pct");
            String token = jwtService.generateToken(user);

            String code = "PCT20_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            Voucher voucher = createTestVoucher(code, DiscountType.PERCENTAGE, new BigDecimal("20.00"), 50, 0, VoucherStatus.ACTIVE, null, null);

            // Category 3 (CAT 2): Price = 1,200,000.00
            TicketCategory category = categoryRepository.findById(3L).orElseThrow();

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(2); // Subtotal = 2,400,000.00

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));
            request.setVoucherCode(code);

            MvcResult result = mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "idem-bk-pct-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.subtotal").value(2400000.00))
                    .andExpect(jsonPath("$.data.discountAmount").value(480000.00)) // 20% of 2,400,000
                    .andExpect(jsonPath("$.data.totalAmount").value(1920000.00))
                    .andReturn();

            long bookingId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();

            // Verify voucher in DB
            Voucher updatedVoucher = voucherRepository.findById(voucher.getId()).orElseThrow();
            assertEquals(1, updatedVoucher.getRedeemedCount());
            assertEquals(VoucherStatus.ACTIVE, updatedVoucher.getStatus());

            // Verify VoucherRedemption in DB
            assertTrue(voucherRedemptionRepository.existsByVoucherIdAndUserId(voucher.getId(), user.getId()));
            Booking savedBooking = bookingRepository.findById(bookingId).orElseThrow();
            assertEquals(voucher.getId(), savedBooking.getVoucherId());
        }

        @Test
        @DisplayName("Create booking with Fixed Voucher applies exact fixed discount")
        void booking_WithFixedVoucher_Success() throws Exception {
            User user = createUniqueCustomer("bk_fix");
            String token = jwtService.generateToken(user);

            String code = "FIX150_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            Voucher voucher = createTestVoucher(code, DiscountType.FIXED, new BigDecimal("150000.00"), 10, 0, VoucherStatus.ACTIVE, null, null);

            // Category 4 (Standard): Price = 600,000.00
            TicketCategory category = categoryRepository.findById(4L).orElseThrow();

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(1); // Subtotal = 600,000.00

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));
            request.setVoucherCode(code);

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "idem-bk-fix-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.subtotal").value(600000.00))
                    .andExpect(jsonPath("$.data.discountAmount").value(150000.00))
                    .andExpect(jsonPath("$.data.totalAmount").value(450000.00));

            Voucher updatedVoucher = voucherRepository.findById(voucher.getId()).orElseThrow();
            assertEquals(1, updatedVoucher.getRedeemedCount());
        }

        @Test
        @DisplayName("Create booking where Fixed Discount > Subtotal caps discount at Subtotal and totalAmount becomes 0")
        void booking_WithFixedVoucher_DiscountExceedsSubtotal_CapsDiscount() throws Exception {
            User user = createUniqueCustomer("bk_cap");
            String token = jwtService.generateToken(user);

            // Fixed discount 1,000,000 VND
            String code = "HUGE_DISCOUNT_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            createTestVoucher(code, DiscountType.FIXED, new BigDecimal("1000000.00"), 10, 0, VoucherStatus.ACTIVE, null, null);

            // Category 4: Price = 600,000.00 (Subtotal = 600,000.00 < 1,000,000.00)
            TicketCategory category = categoryRepository.findById(4L).orElseThrow();

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(1);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));
            request.setVoucherCode(code);

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "idem-bk-cap-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.subtotal").value(600000.00))
                    .andExpect(jsonPath("$.data.discountAmount").value(600000.00)) // Capped at 600,000
                    .andExpect(jsonPath("$.data.totalAmount").value(0.00));
        }

        @Test
        @DisplayName("Booking transitions voucher status to USED_UP when reaching maxRedemptions; subsequent booking fails")
        void booking_ReachingMaxRedemptions_TransitionsToUsedUp() throws Exception {
            User user1 = createUniqueCustomer("bk_used_u1");
            String token1 = jwtService.generateToken(user1);

            User user2 = createUniqueCustomer("bk_used_u2");
            String token2 = jwtService.generateToken(user2);

            String code = "ONETIME_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            Voucher voucher = createTestVoucher(code, DiscountType.FIXED, new BigDecimal("50000.00"), 1, 0, VoucherStatus.ACTIVE, null, null);

            TicketCategory category = categoryRepository.findById(4L).orElseThrow();

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(1);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));
            request.setVoucherCode(code);

            // 1st booking - Success, consumes the single redemption
            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token1)
                            .header("Idempotency-Key", "idem-used-1-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // Check voucher status in DB
            Voucher updatedVoucher = voucherRepository.findById(voucher.getId()).orElseThrow();
            assertEquals(1, updatedVoucher.getRedeemedCount());
            assertEquals(VoucherStatus.USED_UP, updatedVoucher.getStatus());

            // 2nd booking by another user - Fails because voucher is USED_UP
            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token2)
                            .header("Idempotency-Key", "idem-used-2-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.VOUCHER_INVALID.getCode()));
        }

        @Test
        @DisplayName("Booking with DISABLED voucher returns 400 Bad Request")
        void booking_WithDisabledVoucher_Returns400BadRequest() throws Exception {
            User user = createUniqueCustomer("bk_dis_v");
            String token = jwtService.generateToken(user);

            String code = "DISABLED_V_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            createTestVoucher(code, DiscountType.PERCENTAGE, new BigDecimal("10.00"), 10, 0, VoucherStatus.DISABLED, null, null);

            TicketCategory category = categoryRepository.findById(4L).orElseThrow();
            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(1);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));
            request.setVoucherCode(code);

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "idem-dis-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.VOUCHER_INVALID.getCode()));
        }

        @Test
        @DisplayName("Booking with EXPIRED voucher returns 400 Bad Request")
        void booking_WithExpiredVoucher_Returns400BadRequest() throws Exception {
            User user = createUniqueCustomer("bk_exp_v");
            String token = jwtService.generateToken(user);

            String code = "EXPIRED_V_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            createTestVoucher(code, DiscountType.PERCENTAGE, new BigDecimal("10.00"), 10, 0, VoucherStatus.ACTIVE,
                    ZonedDateTime.now().minusDays(30), ZonedDateTime.now().minusDays(5));

            TicketCategory category = categoryRepository.findById(4L).orElseThrow();
            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(1);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));
            request.setVoucherCode(code);

            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "idem-exp-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.VOUCHER_INVALID.getCode()));
        }

        @Test
        @DisplayName("Reusing same voucher by same user across two bookings returns 409 Conflict")
        void booking_ReuseSameVoucher_Returns409Conflict() throws Exception {
            User user = createUniqueCustomer("bk_reuse_u");
            String token = jwtService.generateToken(user);

            String code = "REUSE_CHECK_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            createTestVoucher(code, DiscountType.FIXED, new BigDecimal("30000.00"), 10, 0, VoucherStatus.ACTIVE, null, null);

            TicketCategory category = categoryRepository.findById(4L).orElseThrow();
            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(1);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));
            request.setVoucherCode(code);

            // 1st booking - Success
            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "idem-reuse-a-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // 2nd booking - Conflict
            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "idem-reuse-b-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.VOUCHER_ALREADY_REDEEMED.getCode()));
        }
    }

    // =========================================================================
    // 4. VOUCHER ROLLBACK & CANCELLATION LIFECYCLE TESTS
    // =========================================================================
    @Nested
    @DisplayName("Voucher Rollback & Cancellation Lifecycle Tests")
    class VoucherRollbackAndCancellationTests {

        @Test
        @DisplayName("Admin cancels booking - deletes redemption record, decrements count and restores status from USED_UP to ACTIVE")
        void adminCancelBooking_RevertsVoucherRedemptionAndRestoresActiveStatus() throws Exception {
            User user1 = createUniqueCustomer("rb_admin_u1");
            String token1 = jwtService.generateToken(user1);

            User user2 = createUniqueCustomer("rb_admin_u2");
            String token2 = jwtService.generateToken(user2);

            String code = "RESTORE_ADMIN_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            Voucher voucher = createTestVoucher(code, DiscountType.FIXED, new BigDecimal("50000.00"), 1, 0, VoucherStatus.ACTIVE, null, null);

            TicketCategory category = categoryRepository.findById(4L).orElseThrow();
            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(1);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));
            request.setVoucherCode(code);

            // Step 1: User 1 books and consumes the only slot -> status becomes USED_UP
            MvcResult bookingResult = mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token1)
                            .header("Idempotency-Key", "idem-rb-admin-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andReturn();

            long bookingId = objectMapper.readTree(bookingResult.getResponse().getContentAsString()).get("data").get("id").asLong();

            Voucher consumedVoucher = voucherRepository.findById(voucher.getId()).orElseThrow();
            assertEquals(1, consumedVoucher.getRedeemedCount());
            assertEquals(VoucherStatus.USED_UP, consumedVoucher.getStatus());
            assertTrue(voucherRedemptionRepository.existsByVoucherIdAndUserId(voucher.getId(), user1.getId()));

            // Step 2: Admin cancels the booking
            operationService.cancelBookingAndReleaseInventory(bookingId, adminUser.getId());

            // Step 3: Verify Voucher is rolled back
            Voucher restoredVoucher = voucherRepository.findById(voucher.getId()).orElseThrow();
            assertEquals(0, restoredVoucher.getRedeemedCount());
            assertEquals(VoucherStatus.ACTIVE, restoredVoucher.getStatus());
            assertFalse(voucherRedemptionRepository.existsByVoucherIdAndUserId(voucher.getId(), user1.getId()));

            // Step 4: User 2 can now successfully book with the restored voucher!
            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token2)
                            .header("Idempotency-Key", "idem-rb-admin-u2-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.discountAmount").value(50000.00));
        }

        @Test
        @DisplayName("Booking expiry service reverts voucher redemption and restores status from USED_UP to ACTIVE")
        void bookingExpiry_RevertsVoucherRedemptionAndRestoresActiveStatus() throws Exception {
            User user1 = createUniqueCustomer("rb_exp_u1");
            String token1 = jwtService.generateToken(user1);

            User user2 = createUniqueCustomer("rb_exp_u2");
            String token2 = jwtService.generateToken(user2);

            String code = "RESTORE_EXP_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            Voucher voucher = createTestVoucher(code, DiscountType.PERCENTAGE, new BigDecimal("10.00"), 1, 0, VoucherStatus.ACTIVE, null, null);

            TicketCategory category = categoryRepository.findById(4L).orElseThrow();
            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(1);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));
            request.setVoucherCode(code);

            // Step 1: User 1 books ticket
            MvcResult bookingResult = mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token1)
                            .header("Idempotency-Key", "idem-rb-exp-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andReturn();

            long bookingId = objectMapper.readTree(bookingResult.getResponse().getContentAsString()).get("data").get("id").asLong();
            Booking booking = bookingRepository.findById(bookingId).orElseThrow();

            assertEquals(VoucherStatus.USED_UP, voucherRepository.findById(voucher.getId()).orElseThrow().getStatus());

            // Step 2: Trigger expiry
            bookingExpiryService.expireBooking(booking);

            // Step 3: Verify Voucher is restored
            Voucher restoredVoucher = voucherRepository.findById(voucher.getId()).orElseThrow();
            assertEquals(0, restoredVoucher.getRedeemedCount());
            assertEquals(VoucherStatus.ACTIVE, restoredVoucher.getStatus());
            assertFalse(voucherRedemptionRepository.existsByVoucherIdAndUserId(voucher.getId(), user1.getId()));

            // Step 4: User 2 can book using this voucher
            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", "Bearer " + token2)
                            .header("Idempotency-Key", "idem-rb-exp-u2-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // =========================================================================
    // 5. CONCURRENCY & PESSIMISTIC LOCK TESTS
    // =========================================================================
    @Nested
    @DisplayName("Concurrency & Pessimistic Lock Tests")
    class ConcurrencyAndPessimisticLockTests {

        @Test
        @DisplayName("Concurrent bookings competing for 1 voucher slot - exactly 1 succeeds, remaining fail without over-redemption")
        void concurrentBookings_LastVoucherSlot_OnlyOneSucceeds() throws Exception {
            String code = "CONCURRENT_V_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            // Voucher with maxRedemptions = 1
            Voucher voucher = createTestVoucher(code, DiscountType.FIXED, new BigDecimal("50000.00"), 1, 0, VoucherStatus.ACTIVE, null, null);

            TicketCategory category = categoryRepository.findById(4L).orElseThrow();

            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                User testUser = createUniqueCustomer("conc_u" + i);
                String token = jwtService.generateToken(testUser);

                BookingItemRequest item = new BookingItemRequest();
                item.setTicketCategoryId(category.getId());
                item.setQuantity(1);

                CreateBookingRequest request = new CreateBookingRequest();
                request.setItems(List.of(item));
                request.setVoucherCode(code);

                executor.submit(() -> {
                    try {
                        startLatch.await(); // Synchronize all threads to fire simultaneously

                        mockMvc.perform(post("/api/bookings")
                                        .header("Authorization", "Bearer " + token)
                                        .header("Idempotency-Key", "idem-conc-" + UUID.randomUUID())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                .andExpect(result -> {
                                    int status = result.getResponse().getStatus();
                                    if (status == 200) {
                                        successCount.incrementAndGet();
                                    } else {
                                        failureCount.incrementAndGet();
                                    }
                                });
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // Trigger concurrent execution
            startLatch.countDown();
            boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(completed, "All concurrent booking threads should finish within 15s");
            assertEquals(1, successCount.get(), "Exactly 1 user should succeed with the single voucher slot");
            assertEquals(threadCount - 1, failureCount.get(), "Remaining users should fail to redeem the consumed voucher");

            // Verify final DB state
            Voucher updatedVoucher = voucherRepository.findById(voucher.getId()).orElseThrow();
            assertEquals(1, updatedVoucher.getRedeemedCount(), "Redeemed count must strictly equal 1");
            assertEquals(VoucherStatus.USED_UP, updatedVoucher.getStatus());

            List<VoucherRedemption> redemptions = voucherRedemptionRepository.findAll().stream()
                    .filter(r -> r.getVoucherId().equals(voucher.getId()))
                    .toList();
            assertEquals(1, redemptions.size(), "Only 1 redemption record must exist for this voucher");
        }
    }

    // =========================================================================
    // 6. DATABASE INTEGRITY & CONSTRAINT TESTS
    // =========================================================================
    @Nested
    @DisplayName("Database Integrity & Constraint Tests")
    class DatabaseIntegrityAndConstraintTests {

        @Test
        @DisplayName("Database constraint: duplicate voucher code violates UNIQUE constraint uq_vouchers_code")
        void databaseConstraint_DuplicateVoucherCode_ThrowsException() {
            String duplicateCode = "DUP_CODE_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

            createTestVoucher(duplicateCode, DiscountType.FIXED, new BigDecimal("10000.00"), 10, 0, VoucherStatus.ACTIVE, null, null);

            Voucher secondVoucher = Voucher.builder()
                    .name("Second Voucher Duplicate")
                    .code(duplicateCode)
                    .discountType(DiscountType.PERCENTAGE)
                    .discountValue(new BigDecimal("10.00"))
                    .maxRedemptions(10)
                    .redeemedCount(0)
                    .maxPerUser(1)
                    .startsAt(ZonedDateTime.now())
                    .endsAt(ZonedDateTime.now().plusDays(5))
                    .status(VoucherStatus.ACTIVE)
                    .build();

            assertThrows(DataIntegrityViolationException.class, () -> {
                voucherRepository.saveAndFlush(secondVoucher);
            });
        }

        @Test
        @DisplayName("Database constraint: duplicate (voucher_id, user_id) violates UNIQUE index uq_voucher_redemptions_voucher_user")
        void databaseConstraint_DuplicateRedemptionPerUser_ThrowsException() {
            User user = createUniqueCustomer("uq_red_user");
            Booking booking1 = createTestBooking(user);
            Booking booking2 = createTestBooking(user);

            String code = "UQ_RED_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            Voucher voucher = createTestVoucher(code, DiscountType.FIXED, new BigDecimal("10000.00"), 10, 0, VoucherStatus.ACTIVE, null, null);

            // 1st redemption record with valid booking1
            voucherRedemptionRepository.saveAndFlush(VoucherRedemption.builder()
                    .voucherId(voucher.getId())
                    .userId(user.getId())
                    .bookingId(booking1.getId())
                    .discountAmount(new BigDecimal("10000.00"))
                    .build());

            // 2nd redemption record with same (voucherId, userId) but different valid booking2
            VoucherRedemption duplicateRedemption = VoucherRedemption.builder()
                    .voucherId(voucher.getId())
                    .userId(user.getId())
                    .bookingId(booking2.getId())
                    .discountAmount(new BigDecimal("10000.00"))
                    .build();

            assertThrows(DataIntegrityViolationException.class, () -> {
                voucherRedemptionRepository.saveAndFlush(duplicateRedemption);
            });
        }
    }
}
