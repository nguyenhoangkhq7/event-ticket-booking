package com.geekup.eventticketbookingservice.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geekup.eventticketbookingservice.AbstractIntegrationTest;
import com.geekup.eventticketbookingservice.booking.*;
import com.geekup.eventticketbookingservice.booking.dto.BookingItemRequest;
import com.geekup.eventticketbookingservice.booking.dto.CreateBookingRequest;
import com.geekup.eventticketbookingservice.catalog.*;
import com.geekup.eventticketbookingservice.common.exception.ErrorCode;
import com.geekup.eventticketbookingservice.inventory.InventoryRedisService;
import com.geekup.eventticketbookingservice.operation.dto.UpdateBookingRiskStatusRequest;
import com.geekup.eventticketbookingservice.operation.dto.UpdateBookingStatusRequest;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Operation Module Integration Tests")
public class OperationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
    private VoucherRepository voucherRepository;

    @Autowired
    private VoucherRedemptionRepository voucherRedemptionRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingItemRepository bookingItemRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private InventoryRedisService inventoryRedisService;

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
                        .fullName("Nguyen Van A")
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
                .fullName("Test Customer " + prefix)
                .password("$2a$12$H/tnE.97GdULmDOTk4MRO.y6rRv/4f30bKA4IV2RESH2b9nX49wQu")
                .role(Role.CUSTOMER)
                .status("ACTIVE")
                .build();
        return userRepository.save(user);
    }

    private Concert createDraftConcert(String name) {
        Concert concert = Concert.builder()
                .name(name)
                .description("Concert description for " + name)
                .venue("National Stadium")
                .startAt(ZonedDateTime.now().plusDays(30))
                .endAt(ZonedDateTime.now().plusDays(30).plusHours(4))
                .saleStartAt(ZonedDateTime.now().minusDays(1))
                .saleEndAt(ZonedDateTime.now().plusDays(25))
                .status(ConcertStatus.DRAFT)
                .build();
        return concertRepository.save(concert);
    }

    // =========================================================================
    // 1. SECURITY & RBAC AUTHORIZATION TESTS
    // =========================================================================
    @Nested
    @DisplayName("Security & Role-Based Access Control Tests")
    class SecurityAuthorizationTests {

        @Test
        @DisplayName("Customer role receives 403 Forbidden on all operation endpoints")
        void customerAccess_DeniedWithForbidden() throws Exception {
            // 1. POST /api/operation/concerts
            String concertJson = """
                {
                    "name": "Unauthorized Concert",
                    "venue": "Secret Venue",
                    "startAt": "2026-12-01T19:00:00+07:00",
                    "endAt": "2026-12-01T22:00:00+07:00",
                    "saleStartAt": "2026-11-01T00:00:00+07:00",
                    "saleEndAt": "2026-11-30T23:59:59+07:00"
                }
                """;

            mockMvc.perform(post("/api/operation/concerts")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(concertJson))
                    .andExpect(status().isForbidden());

            // 2. PATCH /api/operation/concerts/1/publish
            mockMvc.perform(patch("/api/operation/concerts/1/publish")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isForbidden());

            // 3. POST /api/operation/concerts/1/ticket-categories
            TicketCategory category = TicketCategory.builder()
                    .name("VIP")
                    .price(new BigDecimal("1000000.00"))
                    .maxPerBooking(4)
                    .build();

            mockMvc.perform(post("/api/operation/concerts/1/ticket-categories")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(category)))
                    .andExpect(status().isForbidden());

            // 4. POST /api/operation/concerts/1/ticket-categories/1/inventory
            mockMvc.perform(post("/api/operation/concerts/1/ticket-categories/1/inventory")
                            .header("Authorization", "Bearer " + customerToken)
                            .param("totalQuantity", "100"))
                    .andExpect(status().isForbidden());

            // 5. POST /api/operation/vouchers
            String voucherJson = """
                {
                    "name": "Unauthorized Voucher",
                    "discountType": "PERCENTAGE",
                    "discountValue": 10.00,
                    "maxRedemptions": 100,
                    "maxPerUser": 1,
                    "startsAt": "2026-10-01T00:00:00+07:00",
                    "endsAt": "2026-10-31T23:59:59+07:00"
                }
                """;

            mockMvc.perform(post("/api/operation/vouchers")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(voucherJson))
                    .andExpect(status().isForbidden());

            // 6. PATCH /api/operation/vouchers/1/disable
            mockMvc.perform(patch("/api/operation/vouchers/1/disable")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isForbidden());

            // 7. PATCH /api/operation/vouchers/1/enable
            mockMvc.perform(patch("/api/operation/vouchers/1/enable")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isForbidden());

            // 8. GET /api/operation/bookings
            mockMvc.perform(get("/api/operation/bookings")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isForbidden());

            // 9. PATCH /api/operation/bookings/1/status
            UpdateBookingStatusRequest statusReq = new UpdateBookingStatusRequest();
            statusReq.setStatus(BookingStatus.PAID);

            mockMvc.perform(patch("/api/operation/bookings/1/status")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(statusReq)))
                    .andExpect(status().isForbidden());

            // 10. POST /api/operation/bookings/1/cancel
            mockMvc.perform(post("/api/operation/bookings/1/cancel")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated request receives 401 Unauthorized")
        void unauthenticatedAccess_DeniedWithUnauthorized() throws Exception {
            mockMvc.perform(get("/api/operation/bookings"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(post("/api/operation/concerts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(post("/api/operation/vouchers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // 2. CONCERT OPERATION TESTS
    // =========================================================================
    @Nested
    @DisplayName("Concert Operations (POST/PATCH /api/operation/concerts)")
    class ConcertOperationTests {

        @Test
        @DisplayName("Admin creates concert successfully with DRAFT status")
        void createConcert_Success() throws Exception {
            String concertName = "World Tour In Vietnam " + UUID.randomUUID().toString().substring(0, 6);
            String nowIso = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String startIso = ZonedDateTime.now().plusMonths(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String endIso = ZonedDateTime.now().plusMonths(2).plusHours(4).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String saleEndIso = ZonedDateTime.now().plusMonths(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

            String requestJson = String.format("""
                {
                    "name": "%s",
                    "description": "The biggest music festival of the year",
                    "venue": "My Dinh National Stadium",
                    "startAt": "%s",
                    "endAt": "%s",
                    "saleStartAt": "%s",
                    "saleEndAt": "%s"
                }
                """, concertName, startIso, endIso, nowIso, saleEndIso);

            MvcResult result = mockMvc.perform(post("/api/operation/concerts")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").isNumber())
                    .andExpect(jsonPath("$.data.name").value(concertName))
                    .andExpect(jsonPath("$.data.venue").value("My Dinh National Stadium"))
                    .andExpect(jsonPath("$.data.status").value("DRAFT"))
                    .andReturn();

            long concertId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();

            // Verify in database
            Concert savedConcert = concertRepository.findById(concertId).orElseThrow();
            assertEquals(concertName, savedConcert.getName());
            assertEquals(ConcertStatus.DRAFT, savedConcert.getStatus());
        }

        @Test
        @DisplayName("Admin publishes concert successfully, updating status to PUBLISHED")
        void publishConcert_Success() throws Exception {
            Concert draftConcert = createDraftConcert("Upcoming Rock Fest " + UUID.randomUUID().toString().substring(0, 6));
            assertEquals(ConcertStatus.DRAFT, draftConcert.getStatus());

            mockMvc.perform(patch("/api/operation/concerts/" + draftConcert.getId() + "/publish")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(draftConcert.getId()))
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

            // Verify in database
            Concert publishedConcert = concertRepository.findById(draftConcert.getId()).orElseThrow();
            assertEquals(ConcertStatus.PUBLISHED, publishedConcert.getStatus());
        }

        @Test
        @DisplayName("Admin adds ticket category to concert successfully with ACTIVE status")
        void addTicketCategory_Success() throws Exception {
            Concert concert = createDraftConcert("Jazz Night Festival " + UUID.randomUUID().toString().substring(0, 6));

            TicketCategory category = TicketCategory.builder()
                    .name("VIP Diamond")
                    .price(new BigDecimal("3000000.00"))
                    .maxPerBooking(4)
                    .build();

            MvcResult result = mockMvc.perform(post("/api/operation/concerts/" + concert.getId() + "/ticket-categories")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(category)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").isNumber())
                    .andExpect(jsonPath("$.data.concertId").value(concert.getId()))
                    .andExpect(jsonPath("$.data.name").value("VIP Diamond"))
                    .andExpect(jsonPath("$.data.price").value(3000000.00))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andReturn();

            long categoryId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();

            // Verify in database
            TicketCategory savedCategory = categoryRepository.findById(categoryId).orElseThrow();
            assertEquals(concert.getId(), savedCategory.getConcertId());
            assertEquals("VIP Diamond", savedCategory.getName());
            assertEquals(TicketCategoryStatus.ACTIVE, savedCategory.getStatus());
        }

        @Test
        @DisplayName("Admin sets inventory for ticket category, pre-warming Redis and saving to DB")
        void setInventory_Success() throws Exception {
            Concert concert = createDraftConcert("Acoustic Sunset " + UUID.randomUUID().toString().substring(0, 6));
            TicketCategory category = categoryRepository.save(TicketCategory.builder()
                    .concertId(concert.getId())
                    .name("General Admission")
                    .price(new BigDecimal("500000.00"))
                    .maxPerBooking(6)
                    .status(TicketCategoryStatus.ACTIVE)
                    .build());

            // 1. Initial inventory setup: 300 tickets
            mockMvc.perform(post("/api/operation/concerts/" + concert.getId() + "/ticket-categories/" + category.getId() + "/inventory")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("totalQuantity", "300"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.ticketCategoryId").value(category.getId()))
                    .andExpect(jsonPath("$.data.totalQuantity").value(300))
                    .andExpect(jsonPath("$.data.reservedQuantity").value(0))
                    .andExpect(jsonPath("$.data.soldQuantity").value(0));

            // Verify DB and Redis state
            TicketInventory savedInventory = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(300, savedInventory.getTotalQuantity());
            assertEquals(0, savedInventory.getReservedQuantity());
            assertEquals(0, savedInventory.getSoldQuantity());

            Integer redisAvailable = inventoryRedisService.getAvailable(category.getId());
            if (redisAvailable != null) {
                assertEquals(300, redisAvailable);
            }

            // 2. Update inventory to 500 tickets
            mockMvc.perform(post("/api/operation/concerts/" + concert.getId() + "/ticket-categories/" + category.getId() + "/inventory")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("totalQuantity", "500"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalQuantity").value(500));

            TicketInventory updatedInventory = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(500, updatedInventory.getTotalQuantity());
        }

        @Test
        @DisplayName("End-to-End: Admin creates concert, adds categories, sets inventory, publishes, and verifies customer can view")
        void fullConcertLifecycle_EndToEnd_Success() throws Exception {
            String concertName = "Symphony Orchestral Gala " + UUID.randomUUID().toString().substring(0, 6);
            String nowIso = ZonedDateTime.now().minusDays(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String startIso = ZonedDateTime.now().plusDays(20).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String endIso = ZonedDateTime.now().plusDays(20).plusHours(3).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String saleEndIso = ZonedDateTime.now().plusDays(19).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

            String requestJson = String.format("""
                {
                    "name": "%s",
                    "description": "Grand classical symphony concert",
                    "venue": "Hanoi Opera House",
                    "startAt": "%s",
                    "endAt": "%s",
                    "saleStartAt": "%s",
                    "saleEndAt": "%s"
                }
                """, concertName, startIso, endIso, nowIso, saleEndIso);

            // Step 1: Create concert
            MvcResult concertResult = mockMvc.perform(post("/api/operation/concerts")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andReturn();

            long concertId = objectMapper.readTree(concertResult.getResponse().getContentAsString()).get("data").get("id").asLong();

            // Step 2: Add Category 1 (VIP)
            TicketCategory catVip = TicketCategory.builder()
                    .name("VIP Gold")
                    .price(new BigDecimal("2500000.00"))
                    .maxPerBooking(4)
                    .build();

            MvcResult catVipResult = mockMvc.perform(post("/api/operation/concerts/" + concertId + "/ticket-categories")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(catVip)))
                    .andExpect(status().isOk())
                    .andReturn();

            long vipId = objectMapper.readTree(catVipResult.getResponse().getContentAsString()).get("data").get("id").asLong();

            // Step 3: Add Category 2 (Standard)
            TicketCategory catStd = TicketCategory.builder()
                    .name("Standard Balcony")
                    .price(new BigDecimal("800000.00"))
                    .maxPerBooking(6)
                    .build();

            MvcResult catStdResult = mockMvc.perform(post("/api/operation/concerts/" + concertId + "/ticket-categories")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(catStd)))
                    .andExpect(status().isOk())
                    .andReturn();

            long stdId = objectMapper.readTree(catStdResult.getResponse().getContentAsString()).get("data").get("id").asLong();

            // Step 4: Set inventory for VIP and Standard
            mockMvc.perform(post("/api/operation/concerts/" + concertId + "/ticket-categories/" + vipId + "/inventory")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("totalQuantity", "100"))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/operation/concerts/" + concertId + "/ticket-categories/" + stdId + "/inventory")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("totalQuantity", "400"))
                    .andExpect(status().isOk());

            // Step 5: Publish concert
            mockMvc.perform(patch("/api/operation/concerts/" + concertId + "/publish")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

            // Step 6: Verify public catalog endpoint returns concert and categories to customer
            mockMvc.perform(get("/api/concerts/" + concertId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.name").value(concertName))
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

            mockMvc.perform(get("/api/concerts/" + concertId + "/ticket-categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(2)));
        }
    }

    // =========================================================================
    // 3. VOUCHER OPERATION TESTS
    // =========================================================================
    @Nested
    @DisplayName("Voucher Operations (POST/PATCH /api/operation/vouchers)")
    class VoucherOperationTests {

        @Test
        @DisplayName("Admin creates voucher with explicit custom code successfully")
        void createVoucher_WithExplicitCode_Success() throws Exception {
            String customCode = "PROMO_EXPLICIT_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            String nowIso = ZonedDateTime.now().minusDays(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String endIso = ZonedDateTime.now().plusDays(30).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

            String requestJson = String.format("""
                {
                    "name": "Autumn Special Discount",
                    "discountType": "PERCENTAGE",
                    "discountValue": 15.00,
                    "maxRedemptions": 200,
                    "maxPerUser": 2,
                    "startsAt": "%s",
                    "endsAt": "%s"
                }
                """, nowIso, endIso);

            MvcResult result = mockMvc.perform(post("/api/operation/vouchers")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("code", customCode)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").isNumber())
                    .andExpect(jsonPath("$.data.name").value("Autumn Special Discount"))
                    .andExpect(jsonPath("$.data.code").value(customCode))
                    .andExpect(jsonPath("$.data.discountType").value("PERCENTAGE"))
                    .andExpect(jsonPath("$.data.discountValue").value(15.00))
                    .andExpect(jsonPath("$.data.maxRedemptions").value(200))
                    .andExpect(jsonPath("$.data.redeemedCount").value(0))
                    .andExpect(jsonPath("$.data.maxPerUser").value(2))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andReturn();

            long voucherId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();

            // Verify in database
            Voucher savedVoucher = voucherRepository.findById(voucherId).orElseThrow();
            assertEquals(customCode, savedVoucher.getCode());
            assertEquals(VoucherStatus.ACTIVE, savedVoucher.getStatus());
            assertEquals(0, savedVoucher.getRedeemedCount());
        }

        @Test
        @DisplayName("Admin creates voucher without code generates auto FLASH- code")
        void createVoucher_WithAutoGeneratedCode_Success() throws Exception {
            String nowIso = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String endIso = ZonedDateTime.now().plusDays(7).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

            String requestJson = String.format("""
                {
                    "name": "Flash Sale 100K Off",
                    "discountType": "FIXED",
                    "discountValue": 100000.00,
                    "maxRedemptions": 50,
                    "maxPerUser": 1,
                    "startsAt": "%s",
                    "endsAt": "%s"
                }
                """, nowIso, endIso);

            MvcResult result = mockMvc.perform(post("/api/operation/vouchers")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.code").value(startsWith("FLASH-")))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andReturn();

            long voucherId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();

            Voucher savedVoucher = voucherRepository.findById(voucherId).orElseThrow();
            assertTrue(savedVoucher.getCode().startsWith("FLASH-"));
            assertEquals(DiscountType.FIXED, savedVoucher.getDiscountType());
        }

        @Test
        @DisplayName("Admin disables and enables voucher successfully")
        void disableAndEnableVoucher_Success() throws Exception {
            Voucher voucher = voucherRepository.save(Voucher.builder()
                    .name("Toggle Voucher")
                    .code("TOGGLE_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                    .discountType(DiscountType.PERCENTAGE)
                    .discountValue(new BigDecimal("10.00"))
                    .maxRedemptions(100)
                    .redeemedCount(0)
                    .maxPerUser(1)
                    .startsAt(ZonedDateTime.now().minusDays(1))
                    .endsAt(ZonedDateTime.now().plusDays(10))
                    .status(VoucherStatus.ACTIVE)
                    .build());

            // 1. Disable voucher
            mockMvc.perform(patch("/api/operation/vouchers/" + voucher.getId() + "/disable")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(voucher.getId()))
                    .andExpect(jsonPath("$.data.status").value("DISABLED"));

            Voucher disabledVoucher = voucherRepository.findById(voucher.getId()).orElseThrow();
            assertEquals(VoucherStatus.DISABLED, disabledVoucher.getStatus());

            // 2. Enable voucher
            mockMvc.perform(patch("/api/operation/vouchers/" + voucher.getId() + "/enable")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(voucher.getId()))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));

            Voucher enabledVoucher = voucherRepository.findById(voucher.getId()).orElseThrow();
            assertEquals(VoucherStatus.ACTIVE, enabledVoucher.getStatus());
        }

        @Test
        @DisplayName("Disable or enable non-existent voucher returns 404 Not Found (VOUCHER_NOT_FOUND)")
        void disableOrEnableVoucher_NotFound_Returns404() throws Exception {
            mockMvc.perform(patch("/api/operation/vouchers/999999/disable")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.VOUCHER_NOT_FOUND.getCode()));

            mockMvc.perform(patch("/api/operation/vouchers/999999/enable")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.VOUCHER_NOT_FOUND.getCode()));
        }
    }

    // =========================================================================
    // 4. BOOKING OPERATION & MANAGEMENT TESTS
    // =========================================================================
    @Nested
    @DisplayName("Booking Operations (GET/PATCH/POST /api/operation/bookings)")
    class BookingOperationTests {

        @Test
        @DisplayName("Admin gets all bookings across multiple users successfully")
        void getAllBookings_Success() throws Exception {
            User user1 = createUniqueCustomer("op_book_u1");
            User user2 = createUniqueCustomer("op_book_u2");

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(1L); // VIP SVIP
            item.setQuantity(1);

            CreateBookingRequest req1 = new CreateBookingRequest();
            req1.setItems(List.of(item));

            CreateBookingRequest req2 = new CreateBookingRequest();
            req2.setItems(List.of(item));

            bookingService.createBooking(user1.getId(), req1, "idem-op-u1-" + UUID.randomUUID());
            bookingService.createBooking(user2.getId(), req2, "idem-op-u2-" + UUID.randomUUID());

            mockMvc.perform(get("/api/operation/bookings")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", isA(List.class)))
                    .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(2)));
        }

        @Test
        @DisplayName("Admin filters bookings by riskStatus and status successfully")
        void getAllBookings_FilteredByRiskStatusAndStatus_Success() throws Exception {
            User user = createUniqueCustomer("op_book_risk_u");

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(1L);
            item.setQuantity(1);

            CreateBookingRequest req = new CreateBookingRequest();
            req.setItems(List.of(item));

            var bookingResponse = bookingService.createBooking(user.getId(), req, "idem-op-risk-" + UUID.randomUUID());

            // Mark this booking as SUSPICIOUS
            Booking booking = bookingRepository.findById(bookingResponse.getId()).orElseThrow();
            booking.setRiskStatus(RiskStatus.SUSPICIOUS);
            bookingRepository.save(booking);

            // 1. Query with riskStatus=SUSPICIOUS
            mockMvc.perform(get("/api/operation/bookings")
                            .param("riskStatus", "SUSPICIOUS")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", isA(List.class)))
                    .andExpect(jsonPath("$.data[?(@.id == " + bookingResponse.getId() + ")].riskStatus").value("SUSPICIOUS"));

            // 2. Query with non-matching riskStatus=BLOCKED -> should not contain this booking
            mockMvc.perform(get("/api/operation/bookings")
                            .param("riskStatus", "BLOCKED")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[?(@.id == " + bookingResponse.getId() + ")].id").doesNotExist());
        }

        @Test
        @DisplayName("Admin updates booking risk status successfully (NORMAL -> SUSPICIOUS -> BLOCKED)")
        void updateBookingRiskStatus_Success() throws Exception {
            User user = createUniqueCustomer("op_update_risk");

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(1L);
            item.setQuantity(1);

            CreateBookingRequest req = new CreateBookingRequest();
            req.setItems(List.of(item));

            var bookingResponse = bookingService.createBooking(user.getId(), req, "idem-op-risk-upd-" + UUID.randomUUID());
            assertEquals(RiskStatus.NORMAL, bookingResponse.getRiskStatus());

            // Update to SUSPICIOUS
            UpdateBookingRiskStatusRequest updateReq = UpdateBookingRiskStatusRequest.builder()
                    .riskStatus(RiskStatus.SUSPICIOUS)
                    .reason("High-frequency attempts detected")
                    .build();

            mockMvc.perform(patch("/api/operation/bookings/" + bookingResponse.getId() + "/risk-status")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(bookingResponse.getId()))
                    .andExpect(jsonPath("$.data.riskStatus").value("SUSPICIOUS"));

            // Verify in DB
            Booking updatedBooking = bookingRepository.findById(bookingResponse.getId()).orElseThrow();
            assertEquals(RiskStatus.SUSPICIOUS, updatedBooking.getRiskStatus());
        }

        @Test
        @DisplayName("Admin updates booking status successfully")
        void updateBookingStatus_Success() throws Exception {
            User user = createUniqueCustomer("op_update_status");

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(1L);
            item.setQuantity(1);

            CreateBookingRequest req = new CreateBookingRequest();
            req.setItems(List.of(item));

            var bookingResponse = bookingService.createBooking(user.getId(), req, "idem-op-status-" + UUID.randomUUID());
            assertEquals(BookingStatus.RECEIVED, bookingResponse.getStatus());

            UpdateBookingStatusRequest updateReq = new UpdateBookingStatusRequest();
            updateReq.setStatus(BookingStatus.PAID);
            updateReq.setReason("Admin manual verification");

            mockMvc.perform(patch("/api/operation/bookings/" + bookingResponse.getId() + "/status")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(bookingResponse.getId()))
                    .andExpect(jsonPath("$.data.status").value("PAID"));

            // Verify in database
            Booking updatedBooking = bookingRepository.findById(bookingResponse.getId()).orElseThrow();
            assertEquals(BookingStatus.PAID, updatedBooking.getStatus());
        }

        @Test
        @DisplayName("Admin cancels booking with RECEIVED status: releases reserved inventory and restores voucher")
        void cancelBooking_StatusReceived_ReleasesReservedInventoryAndVoucher() throws Exception {
            User user = createUniqueCustomer("op_cancel_rcv");

            // Dedicated Voucher
            Voucher voucher = voucherRepository.save(Voucher.builder()
                    .name("Cancel Test Voucher")
                    .code("OP_CANCEL_RCV_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                    .discountType(DiscountType.FIXED)
                    .discountValue(new BigDecimal("100000.00"))
                    .maxRedemptions(1)
                    .redeemedCount(0)
                    .maxPerUser(1)
                    .startsAt(ZonedDateTime.now().minusDays(1))
                    .endsAt(ZonedDateTime.now().plusDays(10))
                    .status(VoucherStatus.ACTIVE)
                    .build());

            TicketCategory category = categoryRepository.findById(3L).orElseThrow();
            TicketInventory initialInv = inventoryRepository.findById(category.getId()).orElseThrow();
            int initialReserved = initialInv.getReservedQuantity();

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(2);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));
            request.setVoucherCode(voucher.getCode());

            var bookingResponse = bookingService.createBooking(user.getId(), request, "idem-op-cancel-rcv-" + UUID.randomUUID());
            long bookingId = bookingResponse.getId();

            // Verify state before cancel
            TicketInventory invAfterBooking = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(initialReserved + 2, invAfterBooking.getReservedQuantity());

            Voucher vAfterBooking = voucherRepository.findById(voucher.getId()).orElseThrow();
            assertEquals(1, vAfterBooking.getRedeemedCount());
            assertEquals(VoucherStatus.USED_UP, vAfterBooking.getStatus()); // maxRedemptions = 1 -> USED_UP
            assertTrue(voucherRedemptionRepository.existsByVoucherIdAndUserId(voucher.getId(), user.getId()));

            // Admin cancels booking
            mockMvc.perform(post("/api/operation/bookings/" + bookingId + "/cancel")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // Verify booking status
            Booking cancelledBooking = bookingRepository.findById(bookingId).orElseThrow();
            assertEquals(BookingStatus.CANCELLED, cancelledBooking.getStatus());

            // Verify reserved inventory released
            TicketInventory finalInv = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(initialReserved, finalInv.getReservedQuantity());

            // Verify voucher redeemed count decremented and status restored from USED_UP to ACTIVE
            Voucher finalVoucher = voucherRepository.findById(voucher.getId()).orElseThrow();
            assertEquals(0, finalVoucher.getRedeemedCount());
            assertEquals(VoucherStatus.ACTIVE, finalVoucher.getStatus());

            // Verify voucher redemption record deleted
            assertFalse(voucherRedemptionRepository.existsByVoucherIdAndUserId(voucher.getId(), user.getId()));
        }

        @Test
        @DisplayName("Admin cancels booking with PAID status: releases sold inventory")
        void cancelBooking_StatusPaid_ReleasesSoldQuantity() throws Exception {
            User user = createUniqueCustomer("op_cancel_paid");

            TicketCategory category = categoryRepository.findById(2L).orElseThrow();
            TicketInventory initialInv = inventoryRepository.findById(category.getId()).orElseThrow();
            int initialSold = initialInv.getSoldQuantity();

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(2);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));

            var bookingResponse = bookingService.createBooking(user.getId(), request, "idem-op-cancel-paid-" + UUID.randomUUID());
            long bookingId = bookingResponse.getId();

            // Confirm payment -> status PAID, sold quantity incremented
            bookingService.confirmPayment(bookingId, user.getId());

            TicketInventory invAfterPayment = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(initialSold + 2, invAfterPayment.getSoldQuantity());

            // Admin cancels PAID booking
            mockMvc.perform(post("/api/operation/bookings/" + bookingId + "/cancel")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // Verify booking status
            Booking cancelledBooking = bookingRepository.findById(bookingId).orElseThrow();
            assertEquals(BookingStatus.CANCELLED, cancelledBooking.getStatus());

            // Verify sold inventory released
            TicketInventory finalInv = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(initialSold, finalInv.getSoldQuantity());
        }

        @Test
        @DisplayName("Admin cancels already CANCELLED or EXPIRED booking is idempotent and does not double release")
        void cancelBooking_AlreadyCancelledOrExpired_IsIdempotent() throws Exception {
            User user = createUniqueCustomer("op_cancel_idem");

            TicketCategory category = categoryRepository.findById(1L).orElseThrow();
            TicketInventory initialInv = inventoryRepository.findById(category.getId()).orElseThrow();
            int initialReserved = initialInv.getReservedQuantity();

            BookingItemRequest item = new BookingItemRequest();
            item.setTicketCategoryId(category.getId());
            item.setQuantity(1);

            CreateBookingRequest request = new CreateBookingRequest();
            request.setItems(List.of(item));

            var bookingResponse = bookingService.createBooking(user.getId(), request, "idem-op-idem-cancel-" + UUID.randomUUID());
            long bookingId = bookingResponse.getId();

            // First cancellation
            mockMvc.perform(post("/api/operation/bookings/" + bookingId + "/cancel")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());

            TicketInventory invAfterFirstCancel = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(initialReserved, invAfterFirstCancel.getReservedQuantity());

            // Second cancellation on already CANCELLED booking
            mockMvc.perform(post("/api/operation/bookings/" + bookingId + "/cancel")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());

            // Inventory reserved count remains unchanged
            TicketInventory invAfterSecondCancel = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(initialReserved, invAfterSecondCancel.getReservedQuantity());
        }
    }
}
