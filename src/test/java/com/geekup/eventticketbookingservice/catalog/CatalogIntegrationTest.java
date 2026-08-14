package com.geekup.eventticketbookingservice.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geekup.eventticketbookingservice.AbstractIntegrationTest;
import com.geekup.eventticketbookingservice.common.exception.ErrorCode;
import com.geekup.eventticketbookingservice.inventory.InventoryRedisService;
import com.geekup.eventticketbookingservice.security.JwtService;
import com.geekup.eventticketbookingservice.user.Role;
import com.geekup.eventticketbookingservice.user.User;
import com.geekup.eventticketbookingservice.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Catalog Module Integration Tests")
public class CatalogIntegrationTest extends AbstractIntegrationTest {

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
    private InventoryRedisService inventoryRedisService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User adminUser;
    private String adminToken;

    private User customerUser;
    private String customerToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        // Ensure Admin user
        adminUser = userRepository.findByEmail("admin@eventticket.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("admin@eventticket.com")
                        .fullName("System Admin")
                        .password("$2a$12$H/tnE.97GdULmDOTk4MRO.y6rRv/4f30bKA4IV2RESH2b9nX49wQu")
                        .role(Role.ADMIN)
                        .status("ACTIVE")
                        .build()));
        adminToken = jwtService.generateToken(adminUser);

        // Ensure Customer user
        customerUser = userRepository.findByEmail("customer1@example.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("customer1@example.com")
                        .fullName("Nguyen Van A")
                        .password("$2a$12$H/tnE.97GdULmDOTk4MRO.y6rRv/4f30bKA4IV2RESH2b9nX49wQu")
                        .role(Role.CUSTOMER)
                        .status("ACTIVE")
                        .build()));
        customerToken = jwtService.generateToken(customerUser);

        // Clear Caffeine caches before each test
        if (cacheManager.getCache("concerts") != null) {
            cacheManager.getCache("concerts").clear();
        }
        if (cacheManager.getCache("concert") != null) {
            cacheManager.getCache("concert").clear();
        }
        if (cacheManager.getCache("ticketCategories") != null) {
            cacheManager.getCache("ticketCategories").clear();
        }
    }

    // =========================================================================
    // 1. PUBLIC CONCERT CATALOG APIS (/api/concerts)
    // =========================================================================
    @Nested
    @DisplayName("Public Catalog Query Tests (GET /api/concerts)")
    class PublicConcertApiTests {

        @Test
        @DisplayName("GET /api/concerts - returns paginated published concerts without authentication")
        void getPublishedConcerts_Unauthenticated_Success() throws Exception {
            mockMvc.perform(get("/api/concerts")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", notNullValue()))
                    .andExpect(jsonPath("$.data.content", notNullValue()))
                    .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(3))))
                    .andExpect(jsonPath("$.data.content[0].id").isNumber())
                    .andExpect(jsonPath("$.data.content[0].name").isString())
                    .andExpect(jsonPath("$.data.content[0].venue").isString())
                    .andExpect(jsonPath("$.data.content[0].status").value("PUBLISHED"))
                    .andExpect(jsonPath("$.data.totalElements").isNumber())
                    .andExpect(jsonPath("$.data.totalPages").isNumber())
                    .andExpect(jsonPath("$.data.size").value(20));
        }

        @Test
        @DisplayName("GET /api/concerts - pagination query parameters page and size work correctly")
        void getPublishedConcerts_WithPaginationParams_Success() throws Exception {
            mockMvc.perform(get("/api/concerts")
                            .param("page", "0")
                            .param("size", "2")
                            .param("sort", "name,asc")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content", hasSize(2)))
                    .andExpect(jsonPath("$.data.size").value(2))
                    .andExpect(jsonPath("$.data.number").value(0))
                    .andExpect(jsonPath("$.data.totalElements").value(greaterThanOrEqualTo(3)));
        }

        @Test
        @DisplayName("GET /api/concerts - only returns PUBLISHED concerts and ignores DRAFT concerts")
        void getPublishedConcerts_ExcludesDraftConcerts() throws Exception {
            // Create a DRAFT concert directly in DB
            Concert draftConcert = concertRepository.save(Concert.builder()
                    .name("Private Draft Concert " + UUID.randomUUID().toString().substring(0, 8))
                    .description("Secret rehearsal concert")
                    .venue("Underground Studio")
                    .startAt(ZonedDateTime.now().plusDays(20))
                    .endAt(ZonedDateTime.now().plusDays(20).plusHours(2))
                    .saleStartAt(ZonedDateTime.now().plusDays(5))
                    .saleEndAt(ZonedDateTime.now().plusDays(15))
                    .status(ConcertStatus.DRAFT)
                    .build());

            // Clear cache so query hits DB
            if (cacheManager.getCache("concerts") != null) {
                cacheManager.getCache("concerts").clear();
            }

            MvcResult result = mockMvc.perform(get("/api/concerts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            assertFalse(responseBody.contains(draftConcert.getName()), "DRAFT concert should NOT appear in published concerts list");
        }

        @Test
        @DisplayName("GET /api/concerts/{id} - returns concert details when ID exists")
        void getConcertById_Success() throws Exception {
            // Concert 1 is seeded in migration
            Concert existingConcert = concertRepository.findById(1L).orElseThrow();

            mockMvc.perform(get("/api/concerts/" + existingConcert.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(existingConcert.getId()))
                    .andExpect(jsonPath("$.data.name").value(existingConcert.getName()))
                    .andExpect(jsonPath("$.data.venue").value(existingConcert.getVenue()))
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        }

        @Test
        @DisplayName("GET /api/concerts/{id} - returns 404 NOT_FOUND for non-existent ID")
        void getConcertById_NotFound_Returns404() throws Exception {
            mockMvc.perform(get("/api/concerts/999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.CONCERT_NOT_FOUND.getCode()))
                    .andExpect(jsonPath("$.error.message").value(ErrorCode.CONCERT_NOT_FOUND.getMessage()));
        }

        @Test
        @DisplayName("GET /api/concerts/{id}/ticket-categories - returns active categories with Redis available quantity")
        void getTicketCategories_WithRedisAvailableQuantity_Success() throws Exception {
            // Concert 1 has categories 1, 2, 3, 4
            Long concertId = 1L;
            TicketCategory category1 = categoryRepository.findById(1L).orElseThrow();

            // Pre-warm category 1 in Redis with specific available quantity
            inventoryRedisService.preWarm(category1.getId(), 440);

            mockMvc.perform(get("/api/concerts/" + concertId + "/ticket-categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(4))))
                    .andExpect(jsonPath("$.data[?(@.id == " + category1.getId() + ")].availableQuantity").value(440))
                    .andExpect(jsonPath("$.data[?(@.id == " + category1.getId() + ")].name").value(category1.getName()))
                    .andExpect(jsonPath("$.data[?(@.id == " + category1.getId() + ")].price").value(category1.getPrice().doubleValue()))
                    .andExpect(jsonPath("$.data[?(@.id == " + category1.getId() + ")].maxPerBooking").value(category1.getMaxPerBooking()));
        }

        @Test
        @DisplayName("GET /api/concerts/{id}/ticket-categories - falls back to DB quantity when Redis counter is absent")
        void getTicketCategories_FallbackToDbQuantity_WhenRedisKeyMissing() throws Exception {
            // Create a test concert and category
            Concert concert = concertRepository.save(Concert.builder()
                    .name("Fallback DB Concert " + UUID.randomUUID().toString().substring(0, 6))
                    .venue("Indoor Arena")
                    .startAt(ZonedDateTime.now().plusDays(10))
                    .endAt(ZonedDateTime.now().plusDays(10).plusHours(3))
                    .saleStartAt(ZonedDateTime.now().minusDays(1))
                    .saleEndAt(ZonedDateTime.now().plusDays(8))
                    .status(ConcertStatus.PUBLISHED)
                    .build());

            TicketCategory category = categoryRepository.save(TicketCategory.builder()
                    .concertId(concert.getId())
                    .name("Gold Tier")
                    .price(new BigDecimal("1800000.00"))
                    .maxPerBooking(4)
                    .status(TicketCategoryStatus.ACTIVE)
                    .build());

            inventoryRepository.save(TicketInventory.builder()
                    .ticketCategoryId(category.getId())
                    .totalQuantity(300)
                    .reservedQuantity(25)
                    .soldQuantity(75)
                    .build());
            // Expected DB available = 300 - 25 - 75 = 200

            // Ensure Redis key does NOT exist (KEY_PREFIX is "inventory:")
            redisTemplate.delete("inventory:" + category.getId());

            mockMvc.perform(get("/api/concerts/" + concert.getId() + "/ticket-categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].id").value(category.getId()))
                    .andExpect(jsonPath("$.data[0].availableQuantity").value(200));
        }

        @Test
        @DisplayName("GET /api/concerts/{id}/ticket-categories - filters out INACTIVE ticket categories")
        void getTicketCategories_IgnoresInactiveCategories() throws Exception {
            Concert concert = concertRepository.save(Concert.builder()
                    .name("Filtered Category Concert " + UUID.randomUUID().toString().substring(0, 6))
                    .venue("Concert Hall")
                    .startAt(ZonedDateTime.now().plusDays(15))
                    .endAt(ZonedDateTime.now().plusDays(15).plusHours(3))
                    .saleStartAt(ZonedDateTime.now().minusDays(1))
                    .saleEndAt(ZonedDateTime.now().plusDays(10))
                    .status(ConcertStatus.PUBLISHED)
                    .build());

            // Active category
            categoryRepository.save(TicketCategory.builder()
                    .concertId(concert.getId())
                    .name("Active Tier")
                    .price(new BigDecimal("500000.00"))
                    .maxPerBooking(2)
                    .status(TicketCategoryStatus.ACTIVE)
                    .build());

            // Inactive category
            categoryRepository.save(TicketCategory.builder()
                    .concertId(concert.getId())
                    .name("Disabled Tier")
                    .price(new BigDecimal("300000.00"))
                    .maxPerBooking(2)
                    .status(TicketCategoryStatus.INACTIVE)
                    .build());

            mockMvc.perform(get("/api/concerts/" + concert.getId() + "/ticket-categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].name").value("Active Tier"));
        }

        @Test
        @DisplayName("GET /api/concerts/{id}/ticket-categories - returns empty list when concert has no categories")
        void getTicketCategories_NoCategories_ReturnsEmptyList() throws Exception {
            mockMvc.perform(get("/api/concerts/999999/ticket-categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(0)));
        }
    }

    // =========================================================================
    // 2. ADMIN OPERATION CONCERT APIS & SECURITY (/api/operation/concerts)
    // =========================================================================
    @Nested
    @DisplayName("Admin Operation APIs & Security (/api/operation/concerts)")
    class OperationConcertApiTests {

        @Test
        @DisplayName("Unauthenticated request to operation API returns 401 Unauthorized")
        void operationApi_Unauthenticated_Returns401() throws Exception {
            String rawJson = """
                {
                    "name": "Unauthorized Concert",
                    "venue": "Unauthorized Venue"
                }
                """;

            mockMvc.perform(post("/api/operation/concerts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rawJson))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("CUSTOMER role accessing operation API returns 403 Forbidden")
        void operationApi_AsCustomer_Returns403() throws Exception {
            String rawJson = """
                {
                    "name": "Forbidden Concert",
                    "venue": "Forbidden Venue"
                }
                """;

            mockMvc.perform(post("/api/operation/concerts")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rawJson))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /api/operation/concerts - Admin creates concert successfully with DRAFT status")
        void createConcert_AsAdmin_Success() throws Exception {
            String concertName = "Sơn Tùng M-TP Sky Tour " + UUID.randomUUID().toString().substring(0, 6);
            String rawJson = String.format("""
                {
                    "name": "%s",
                    "description": "Live stage with stunning sound and visuals.",
                    "venue": "My Dinh National Stadium",
                    "startAt": "2026-10-15T19:00:00+07:00",
                    "endAt": "2026-10-15T23:00:00+07:00",
                    "saleStartAt": "2026-09-01T00:00:00+07:00",
                    "saleEndAt": "2026-10-10T23:59:59+07:00"
                }
                """, concertName);

            MvcResult result = mockMvc.perform(post("/api/operation/concerts")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rawJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").isNumber())
                    .andExpect(jsonPath("$.data.name").value(concertName))
                    .andExpect(jsonPath("$.data.venue").value("My Dinh National Stadium"))
                    .andExpect(jsonPath("$.data.status").value("DRAFT"))
                    .andReturn();

            long createdConcertId = objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("data").get("id").asLong();

            // Verify persistence in DB
            Optional<Concert> savedOpt = concertRepository.findById(createdConcertId);
            assertTrue(savedOpt.isPresent());
            assertEquals(concertName, savedOpt.get().getName());
            assertEquals(ConcertStatus.DRAFT, savedOpt.get().getStatus());
        }

        @Test
        @DisplayName("PATCH /api/operation/concerts/{id}/publish - Admin publishes concert and evicts cache")
        void publishConcert_AsAdmin_Success() throws Exception {
            Concert draftConcert = concertRepository.save(Concert.builder()
                    .name("Draft to Publish Concert " + UUID.randomUUID().toString().substring(0, 6))
                    .venue("Hanoi Opera House")
                    .startAt(ZonedDateTime.now().plusDays(30))
                    .endAt(ZonedDateTime.now().plusDays(30).plusHours(3))
                    .saleStartAt(ZonedDateTime.now().minusDays(1))
                    .saleEndAt(ZonedDateTime.now().plusDays(20))
                    .status(ConcertStatus.DRAFT)
                    .build());

            mockMvc.perform(patch("/api/operation/concerts/" + draftConcert.getId() + "/publish")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(draftConcert.getId()))
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

            // Verify DB status updated
            Concert publishedConcert = concertRepository.findById(draftConcert.getId()).orElseThrow();
            assertEquals(ConcertStatus.PUBLISHED, publishedConcert.getStatus());
        }

        @Test
        @DisplayName("POST /api/operation/concerts/{id}/ticket-categories - Admin adds ticket category")
        void addTicketCategory_AsAdmin_Success() throws Exception {
            Concert concert = concertRepository.save(Concert.builder()
                    .name("Ticket Category Test Concert")
                    .venue("Saigon Exhibition Center")
                    .startAt(ZonedDateTime.now().plusDays(25))
                    .endAt(ZonedDateTime.now().plusDays(25).plusHours(3))
                    .saleStartAt(ZonedDateTime.now().minusDays(1))
                    .saleEndAt(ZonedDateTime.now().plusDays(20))
                    .status(ConcertStatus.PUBLISHED)
                    .build());

            TicketCategory categoryRequest = TicketCategory.builder()
                    .name("VVIP Diamond")
                    .price(new BigDecimal("5000000.00"))
                    .maxPerBooking(2)
                    .build();

            MvcResult result = mockMvc.perform(post("/api/operation/concerts/" + concert.getId() + "/ticket-categories")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(categoryRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").isNumber())
                    .andExpect(jsonPath("$.data.concertId").value(concert.getId()))
                    .andExpect(jsonPath("$.data.name").value("VVIP Diamond"))
                    .andExpect(jsonPath("$.data.price").value(5000000.00))
                    .andExpect(jsonPath("$.data.maxPerBooking").value(2))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andReturn();

            long categoryId = objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("data").get("id").asLong();

            // Verify persistence in DB
            TicketCategory savedCategory = categoryRepository.findById(categoryId).orElseThrow();
            assertEquals(concert.getId(), savedCategory.getConcertId());
            assertEquals("VVIP Diamond", savedCategory.getName());
            assertEquals(TicketCategoryStatus.ACTIVE, savedCategory.getStatus());
        }

        @Test
        @DisplayName("POST /api/operation/concerts/{id}/ticket-categories/{categoryId}/inventory - Admin sets inventory and pre-warms Redis")
        void setInventory_AsAdmin_Success() throws Exception {
            Concert concert = concertRepository.save(Concert.builder()
                    .name("Inventory Test Concert")
                    .venue("Indoor Gymnasium")
                    .startAt(ZonedDateTime.now().plusDays(15))
                    .endAt(ZonedDateTime.now().plusDays(15).plusHours(2))
                    .saleStartAt(ZonedDateTime.now().minusDays(1))
                    .saleEndAt(ZonedDateTime.now().plusDays(10))
                    .status(ConcertStatus.PUBLISHED)
                    .build());

            TicketCategory category = categoryRepository.save(TicketCategory.builder()
                    .concertId(concert.getId())
                    .name("Balcony A")
                    .price(new BigDecimal("750000.00"))
                    .maxPerBooking(4)
                    .status(TicketCategoryStatus.ACTIVE)
                    .build());

            int totalQty = 1500;

            mockMvc.perform(post("/api/operation/concerts/" + concert.getId() + "/ticket-categories/" + category.getId() + "/inventory")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("totalQuantity", String.valueOf(totalQty)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.ticketCategoryId").value(category.getId()))
                    .andExpect(jsonPath("$.data.totalQuantity").value(1500))
                    .andExpect(jsonPath("$.data.reservedQuantity").value(0))
                    .andExpect(jsonPath("$.data.soldQuantity").value(0));

            // Verify in DB
            TicketInventory inventory = inventoryRepository.findById(category.getId()).orElseThrow();
            assertEquals(1500, inventory.getTotalQuantity());
            assertEquals(0, inventory.getReservedQuantity());
            assertEquals(0, inventory.getSoldQuantity());

            // Verify in Redis
            Integer redisAvailable = inventoryRedisService.getAvailable(category.getId());
            assertNotNull(redisAvailable);
            assertEquals(1500, redisAvailable);
        }
    }

    // =========================================================================
    // 3. END-TO-END CATALOG LIFECYCLE & CACHE EVICTION FLOW
    // =========================================================================
    @Nested
    @DisplayName("End-to-End Catalog Lifecycle & Caching Validation")
    class EndToEndCatalogFlowTests {

        @Test
        @DisplayName("Complete Lifecycle: Create Draft -> Add Categories -> Set Inventory -> Publish -> Query Public API")
        void completeCatalogLifecycleAndCacheEviction() throws Exception {
            String concertName = "E2E Grand Festival " + UUID.randomUUID().toString().substring(0, 6);
            String rawJson = String.format("""
                {
                    "name": "%s",
                    "description": "Annual Grand Festival",
                    "venue": "Grand Stadium",
                    "startAt": "2026-12-01T18:00:00+07:00",
                    "endAt": "2026-12-01T23:00:00+07:00",
                    "saleStartAt": "2026-11-01T00:00:00+07:00",
                    "saleEndAt": "2026-11-30T23:59:59+07:00"
                }
                """, concertName);

            // Step 1: Admin creates a concert in DRAFT state
            MvcResult createResult = mockMvc.perform(post("/api/operation/concerts")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rawJson))
                    .andExpect(status().isOk())
                    .andReturn();

            long concertId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                    .get("data").get("id").asLong();

            // Step 2: Verify DRAFT concert does NOT appear in public catalog list
            mockMvc.perform(get("/api/concerts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[?(@.id == " + concertId + ")]").doesNotExist());

            // Step 3: Admin adds a ticket category
            TicketCategory catReq = TicketCategory.builder()
                    .name("VIP Diamond Lounge")
                    .price(new BigDecimal("3000000.00"))
                    .maxPerBooking(4)
                    .build();

            MvcResult catResult = mockMvc.perform(post("/api/operation/concerts/" + concertId + "/ticket-categories")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(catReq)))
                    .andExpect(status().isOk())
                    .andReturn();

            long categoryId = objectMapper.readTree(catResult.getResponse().getContentAsString())
                    .get("data").get("id").asLong();

            // Step 4: Admin sets inventory for the category
            mockMvc.perform(post("/api/operation/concerts/" + concertId + "/ticket-categories/" + categoryId + "/inventory")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("totalQuantity", "250"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalQuantity").value(250));

            // Step 5: Admin publishes the concert
            mockMvc.perform(patch("/api/operation/concerts/" + concertId + "/publish")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

            // Step 6: Public GET /api/concerts now returns the new concert (testing concert cache eviction)
            mockMvc.perform(get("/api/concerts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[?(@.id == " + concertId + ")].name").value(concertName));

            // Step 7: Public GET /api/concerts/{id} returns details
            mockMvc.perform(get("/api/concerts/" + concertId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(concertId))
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

            // Step 8: Public GET /api/concerts/{id}/ticket-categories returns categories and available quantity
            mockMvc.perform(get("/api/concerts/" + concertId + "/ticket-categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].id").value(categoryId))
                    .andExpect(jsonPath("$.data[0].name").value("VIP Diamond Lounge"))
                    .andExpect(jsonPath("$.data[0].availableQuantity").value(250));

            // Step 9: Admin updates inventory to 600 -> category cache evicted -> public API immediately sees 600
            mockMvc.perform(post("/api/operation/concerts/" + concertId + "/ticket-categories/" + categoryId + "/inventory")
                            .header("Authorization", "Bearer " + adminToken)
                            .param("totalQuantity", "600"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalQuantity").value(600));

            mockMvc.perform(get("/api/concerts/" + concertId + "/ticket-categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].availableQuantity").value(600));
        }
    }

    // =========================================================================
    // 4. JPA REPOSITORY INTEGRATION TESTS
    // =========================================================================
    @Nested
    @DisplayName("Catalog Repository Integration Tests")
    class CatalogRepositoryTests {

        @Test
        @DisplayName("ConcertRepository.findByStatus - correctly filters concerts by status")
        void concertRepository_FindByStatus_ReturnsFilteredList() {
            List<Concert> published = concertRepository.findByStatus(ConcertStatus.PUBLISHED);
            assertFalse(published.isEmpty());
            assertTrue(published.stream().allMatch(c -> c.getStatus() == ConcertStatus.PUBLISHED));
        }

        @Test
        @DisplayName("TicketCategoryRepository.findByConcertIdAndStatus - returns only ACTIVE categories")
        void ticketCategoryRepository_FindByConcertIdAndStatus() {
            List<TicketCategory> activeCategories = categoryRepository.findByConcertIdAndStatus(1L, TicketCategoryStatus.ACTIVE);
            assertFalse(activeCategories.isEmpty());
            assertTrue(activeCategories.stream().allMatch(cat -> cat.getStatus() == TicketCategoryStatus.ACTIVE && cat.getConcertId().equals(1L)));
        }

        @Test
        @DisplayName("TicketInventoryRepository.findByIdForUpdate - executes pessimistic write lock inside transaction")
        void ticketInventoryRepository_FindByIdForUpdate_ExecutesLockSuccessfully() {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.execute(status -> {
                Optional<TicketInventory> invOpt = inventoryRepository.findByIdForUpdate(1L);
                assertTrue(invOpt.isPresent());
                assertEquals(1L, invOpt.get().getTicketCategoryId());
                assertTrue(invOpt.get().getTotalQuantity() > 0);
                return null;
            });
        }
    }
}
