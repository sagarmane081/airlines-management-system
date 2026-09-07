package com.services.service;

import com.common.dto.FareDto;
import com.common.dto.PaymentDto;
import com.services.client.PaymentClient;
import com.services.client.PricingClient;
import com.services.client.SeatClient;
import com.services.dto.BookingDto;
import com.services.dto.PassengerDto;
import com.services.entity.Booking;
import com.services.entity.BookingStatus;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.exception.SeatUnavailableException;
import com.services.repository.BookingRepository;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PricingClient pricingClient;

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private SeatClient seatClient;

    @InjectMocks
    private BookingService bookingService;

    private PassengerDto passenger(Long seatInstanceId) {
        PassengerDto dto = new PassengerDto();
        dto.setFirstName("Test");
        dto.setLastName("Passenger");
        dto.setSeatInstanceId(seatInstanceId);
        return dto;
    }

    private BookingDto requestWithSeats(Long... seatInstanceIds) {
        BookingDto dto = new BookingDto();
        dto.setFlightInstanceId(1L);
        dto.setFareId(2L);
        dto.setPassengers(Arrays.stream(seatInstanceIds).map(this::passenger).toList());
        return dto;
    }

    private BookingDto request() {
        return requestWithSeats(3L);
    }

    @Test
    void createBookingUsesFarePriceHoldsSeatAndStampsRequesterAsOwner() {
        when(pricingClient.getFareById(2L)).thenReturn(new FareDto(2L, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD"));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(10L);
            }
            return b;
        });
        PaymentDto payment = new PaymentDto(5L, 10L, BigDecimal.valueOf(250), "PENDING");
        when(paymentClient.initiatePayment(any(PaymentDto.class))).thenReturn(payment);

        BookingDto result = bookingService.createBooking(request(), 42L, "test@example.com");

        assertEquals(BigDecimal.valueOf(250), result.getAmount());
        assertEquals(BookingStatus.PENDING, result.getStatus());
        assertEquals(5L, result.getPaymentId());
        assertEquals(42L, result.getUserId());
        assertEquals("test@example.com", result.getUserEmail());
        assertEquals(1, result.getPassengers().size());
        verify(seatClient, times(1)).holdSeat(3L);
        verify(bookingRepository, times(2)).save(any(Booking.class));
    }

    @Test
    void createBookingMultipliesFareByPassengerCountAndHoldsEverySeat() {
        when(pricingClient.getFareById(2L)).thenReturn(new FareDto(2L, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD"));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(10L);
            }
            return b;
        });
        PaymentDto payment = new PaymentDto(5L, 10L, BigDecimal.valueOf(500), "PENDING");
        when(paymentClient.initiatePayment(any(PaymentDto.class))).thenReturn(payment);

        BookingDto result = bookingService.createBooking(requestWithSeats(3L, 4L), 42L, "test@example.com");

        assertEquals(BigDecimal.valueOf(500), result.getAmount());
        assertEquals(2, result.getPassengers().size());
        verify(seatClient, times(1)).holdSeat(3L);
        verify(seatClient, times(1)).holdSeat(4L);
    }

    @Test
    void createBookingReleasesAlreadyHeldSeatsWhenALaterSeatFails() {
        when(pricingClient.getFareById(2L)).thenReturn(new FareDto(2L, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD"));
        doNothing().when(seatClient).holdSeat(3L);
        doThrow(mock(FeignException.Conflict.class)).when(seatClient).holdSeat(4L);

        assertThrows(SeatUnavailableException.class, () -> bookingService.createBooking(requestWithSeats(3L, 4L), 42L, "test@example.com"));

        verify(seatClient, times(1)).holdSeat(3L);
        verify(seatClient, times(1)).releaseSeat(3L);
        verify(seatClient, never()).releaseSeat(4L);
        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(paymentClient);
    }

    @Test
    void createBookingStillThrowsOriginalErrorEvenIfRollbackReleaseFails() {
        when(pricingClient.getFareById(2L)).thenReturn(new FareDto(2L, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD"));
        doNothing().when(seatClient).holdSeat(3L);
        doThrow(mock(FeignException.Conflict.class)).when(seatClient).holdSeat(4L);
        doThrow(new RuntimeException("seat-service down")).when(seatClient).releaseSeat(3L);

        assertThrows(SeatUnavailableException.class, () -> bookingService.createBooking(requestWithSeats(3L, 4L), 42L, "test@example.com"));

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBookingCancelsBookingAndReleasesSeatWhenPaymentInitiationFails() {
        when(pricingClient.getFareById(2L)).thenReturn(new FareDto(2L, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD"));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(10L);
            }
            return b;
        });
        doThrow(new RuntimeException("payment-service down")).when(paymentClient).initiatePayment(any(PaymentDto.class));

        assertThrows(RuntimeException.class, () -> bookingService.createBooking(request(), 42L, "test@example.com"));

        verify(seatClient, times(1)).releaseSeat(3L);
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository, times(2)).save(captor.capture());
        assertEquals(BookingStatus.CANCELLED, captor.getValue().getStatus());
    }

    @Test
    void createBookingReleasesEverySeatWhenPaymentInitiationFailsForMultiPassengerBooking() {
        when(pricingClient.getFareById(2L)).thenReturn(new FareDto(2L, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD"));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(10L);
            }
            return b;
        });
        doThrow(new RuntimeException("payment-service down")).when(paymentClient).initiatePayment(any(PaymentDto.class));

        assertThrows(RuntimeException.class, () -> bookingService.createBooking(requestWithSeats(3L, 4L), 42L, "test@example.com"));

        verify(seatClient, times(1)).releaseSeat(3L);
        verify(seatClient, times(1)).releaseSeat(4L);
    }

    @Test
    void createBookingFailsCleanlyWhenSeatConflictIsRaw() {
        when(pricingClient.getFareById(2L)).thenReturn(new FareDto(2L, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD"));
        doThrow(mock(FeignException.Conflict.class)).when(seatClient).holdSeat(3L);

        assertThrows(SeatUnavailableException.class, () -> bookingService.createBooking(request(), 42L, "test@example.com"));
        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(paymentClient);
    }

    @Test
    void createBookingFailsCleanlyWhenSeatConflictIsWrappedByCircuitBreaker() {
        when(pricingClient.getFareById(2L)).thenReturn(new FareDto(2L, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD"));
        FeignException.Conflict conflict = mock(FeignException.Conflict.class);
        doThrow(new NoFallbackAvailableException("no fallback", conflict)).when(seatClient).holdSeat(3L);

        assertThrows(SeatUnavailableException.class, () -> bookingService.createBooking(request(), 42L, "test@example.com"));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBookingRethrowsWhenNoFallbackCauseIsNotASeatConflict() {
        when(pricingClient.getFareById(2L)).thenReturn(new FareDto(2L, 1L, "ECONOMY", BigDecimal.valueOf(250), "USD"));
        NoFallbackAvailableException unrelated = new NoFallbackAvailableException("seat-service down", new RuntimeException("timeout"));
        doThrow(unrelated).when(seatClient).holdSeat(3L);

        NoFallbackAvailableException thrown = assertThrows(NoFallbackAvailableException.class,
                () -> bookingService.createBooking(request(), 42L, "test@example.com"));
        assertSame(unrelated, thrown);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void getBookingByIdThrowsWhenMissing() {
        when(bookingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bookingService.getBookingById(99L, 42L, "ROLE_CUSTOMER"));
    }

    @Test
    void getBookingByIdSucceedsForOwner() {
        Booking booking = new Booking();
        booking.setId(1L);
        booking.setUserId(42L);
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        BookingDto result = bookingService.getBookingById(1L, 42L, "ROLE_CUSTOMER");

        assertEquals(1L, result.getId());
    }

    @Test
    void getBookingByIdSucceedsForSystemAdminEvenWhenNotOwner() {
        Booking booking = new Booking();
        booking.setId(1L);
        booking.setUserId(42L);
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        BookingDto result = bookingService.getBookingById(1L, 999L, "ROLE_SYSTEM_ADMIN");

        assertEquals(1L, result.getId());
    }

    @Test
    void getBookingByIdThrowsForbiddenForNonOwnerNonAdmin() {
        Booking booking = new Booking();
        booking.setId(1L);
        booking.setUserId(42L);
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThrows(ForbiddenException.class, () -> bookingService.getBookingById(1L, 999L, "ROLE_CUSTOMER"));
    }
}
