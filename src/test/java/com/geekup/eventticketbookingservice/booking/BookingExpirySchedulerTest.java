package com.geekup.eventticketbookingservice.booking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingExpirySchedulerTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingExpiryService bookingExpiryService;

    @InjectMocks
    private BookingExpiryScheduler bookingExpiryScheduler;

    @Test
    @DisplayName("processExpiredBookings expires all found expired bookings")
    void processExpiredBookings_ExpiresAllFound() {
        Booking booking1 = Booking.builder().id(101L).status(BookingStatus.RECEIVED).build();
        Booking booking2 = Booking.builder().id(102L).status(BookingStatus.PENDING_PAYMENT).build();

        when(bookingRepository.findExpiredBookings(eq(List.of(BookingStatus.RECEIVED, BookingStatus.PENDING_PAYMENT)), any(ZonedDateTime.class)))
                .thenReturn(List.of(booking1, booking2));

        bookingExpiryScheduler.processExpiredBookings();

        verify(bookingExpiryService, times(1)).expireBooking(booking1);
        verify(bookingExpiryService, times(1)).expireBooking(booking2);
    }

    @Test
    @DisplayName("processExpiredBookings continues expiring subsequent bookings if one fails")
    void processExpiredBookings_ErrorOnOneBooking_ContinuesBatch() {
        Booking booking1 = Booking.builder().id(101L).status(BookingStatus.RECEIVED).build();
        Booking booking2 = Booking.builder().id(102L).status(BookingStatus.PENDING_PAYMENT).build();

        when(bookingRepository.findExpiredBookings(any(), any()))
                .thenReturn(List.of(booking1, booking2));

        doThrow(new RuntimeException("DB Lock Timeout")).when(bookingExpiryService).expireBooking(booking1);
        doNothing().when(bookingExpiryService).expireBooking(booking2);

        bookingExpiryScheduler.processExpiredBookings();

        verify(bookingExpiryService, times(1)).expireBooking(booking1);
        verify(bookingExpiryService, times(1)).expireBooking(booking2);
    }

    @Test
    @DisplayName("processExpiredBookings does nothing when no bookings are expired")
    void processExpiredBookings_NoExpiredBookings_DoesNothing() {
        when(bookingRepository.findExpiredBookings(any(), any()))
                .thenReturn(Collections.emptyList());

        bookingExpiryScheduler.processExpiredBookings();

        verify(bookingExpiryService, never()).expireBooking(any());
    }
}
