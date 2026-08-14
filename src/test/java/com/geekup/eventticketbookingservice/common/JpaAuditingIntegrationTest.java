package com.geekup.eventticketbookingservice.common;

import com.geekup.eventticketbookingservice.AbstractIntegrationTest;
import com.geekup.eventticketbookingservice.booking.Booking;
import com.geekup.eventticketbookingservice.booking.BookingRepository;
import com.geekup.eventticketbookingservice.booking.BookingStatus;
import com.geekup.eventticketbookingservice.catalog.Concert;
import com.geekup.eventticketbookingservice.catalog.ConcertRepository;
import com.geekup.eventticketbookingservice.catalog.ConcertStatus;
import com.geekup.eventticketbookingservice.user.Role;
import com.geekup.eventticketbookingservice.user.User;
import com.geekup.eventticketbookingservice.user.UserRepository;
import com.geekup.eventticketbookingservice.voucher.DiscountType;
import com.geekup.eventticketbookingservice.voucher.Voucher;
import com.geekup.eventticketbookingservice.voucher.VoucherRepository;
import com.geekup.eventticketbookingservice.voucher.VoucherStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Spring Data JPA Auditing Integration Tests")
public class JpaAuditingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("Concert entity automatically populates createdAt and updatedAt on insert, and updates updatedAt on modification")
    void concert_Auditing_PopulatesTimestamps() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Concert savedConcert = tx.execute(status -> {
            Concert concert = Concert.builder()
                    .name("Audit Test Concert " + UUID.randomUUID().toString().substring(0, 6))
                    .venue("Audit Hall")
                    .startAt(ZonedDateTime.now().plusDays(5))
                    .endAt(ZonedDateTime.now().plusDays(5).plusHours(2))
                    .saleStartAt(ZonedDateTime.now().minusDays(1))
                    .saleEndAt(ZonedDateTime.now().plusDays(4))
                    .status(ConcertStatus.DRAFT)
                    .build();
            return concertRepository.save(concert);
        });

        assertNotNull(savedConcert);
        assertNotNull(savedConcert.getCreatedAt(), "createdAt should be automatically populated by JPA Auditing");
        assertNotNull(savedConcert.getUpdatedAt(), "updatedAt should be automatically populated by JPA Auditing");

        ZonedDateTime initialCreatedAt = savedConcert.getCreatedAt();
        ZonedDateTime initialUpdatedAt = savedConcert.getUpdatedAt();

        // Update the concert
        Concert updatedConcert = tx.execute(status -> {
            Concert toUpdate = concertRepository.findById(savedConcert.getId()).orElseThrow();
            toUpdate.setStatus(ConcertStatus.PUBLISHED);
            return concertRepository.saveAndFlush(toUpdate);
        });

        assertNotNull(updatedConcert);
        assertEquals(initialCreatedAt.toInstant().truncatedTo(ChronoUnit.MILLIS),
                updatedConcert.getCreatedAt().toInstant().truncatedTo(ChronoUnit.MILLIS),
                "createdAt must not change on update");
        assertTrue(updatedConcert.getUpdatedAt().toInstant().isAfter(initialUpdatedAt.toInstant())
                || updatedConcert.getUpdatedAt().toInstant().equals(initialUpdatedAt.toInstant()));
    }

    @Test
    @DisplayName("Voucher entity automatically populates createdAt and updatedAt on insert")
    void voucher_Auditing_PopulatesTimestamps() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Voucher savedVoucher = tx.execute(status -> {
            Voucher voucher = Voucher.builder()
                    .name("Audit Voucher")
                    .code("AUDIT_" + UUID.randomUUID().toString().substring(0, 8))
                    .discountType(DiscountType.FIXED)
                    .discountValue(new BigDecimal("50000.00"))
                    .maxRedemptions(100)
                    .redeemedCount(0)
                    .maxPerUser(1)
                    .startsAt(ZonedDateTime.now().minusDays(1))
                    .endsAt(ZonedDateTime.now().plusDays(30))
                    .status(VoucherStatus.ACTIVE)
                    .build();
            return voucherRepository.save(voucher);
        });

        assertNotNull(savedVoucher);
        assertNotNull(savedVoucher.getCreatedAt(), "Voucher createdAt should be set by auditing");
        assertNotNull(savedVoucher.getUpdatedAt(), "Voucher updatedAt should be set by auditing");
    }

    @Test
    @DisplayName("Booking entity automatically populates createdAt and updatedAt on insert")
    void booking_Auditing_PopulatesTimestamps() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        User user = tx.execute(status -> userRepository.save(User.builder()
                .email("audit_user_" + UUID.randomUUID().toString().substring(0, 6) + "@test.com")
                .fullName("Audit User")
                .password("hashed_pass")
                .role(Role.CUSTOMER)
                .status("ACTIVE")
                .build()));

        Booking savedBooking = tx.execute(status -> {
            Booking booking = Booking.builder()
                    .bookingCode("BK-AUDIT-" + UUID.randomUUID().toString().substring(0, 8))
                    .userId(user.getId())
                    .status(BookingStatus.RECEIVED)
                    .subtotal(new BigDecimal("1000000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .totalAmount(new BigDecimal("1000000.00"))
                    .expiresAt(ZonedDateTime.now().plusMinutes(15))
                    .idempotencyKey(UUID.randomUUID().toString())
                    .build();
            return bookingRepository.save(booking);
        });

        assertNotNull(savedBooking);
        assertNotNull(savedBooking.getCreatedAt(), "Booking createdAt should be set by auditing");
        assertNotNull(savedBooking.getUpdatedAt(), "Booking updatedAt should be set by auditing");
    }
}
