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
import com.services.entity.Passenger;
import com.services.exception.ForbiddenException;
import com.services.exception.ResourceNotFoundException;
import com.services.exception.SeatUnavailableException;
import com.services.mapper.BookingMapper;
import com.services.mapper.PassengerMapper;
import com.services.repository.BookingRepository;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class BookingService {

    private static final String ROLE_SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final PricingClient pricingClient;
    private final PaymentClient paymentClient;
    private final SeatClient seatClient;

    public BookingService(BookingRepository bookingRepository, PricingClient pricingClient,
                           PaymentClient paymentClient, SeatClient seatClient) {
        this.bookingRepository = bookingRepository;
        this.pricingClient = pricingClient;
        this.paymentClient = paymentClient;
        this.seatClient = seatClient;
    }

    public BookingDto createBooking(BookingDto bookingDto, Long requesterId, String requesterEmail, String requesterPhone) {
        FareDto fare = pricingClient.getFareById(bookingDto.getFareId());

        holdAllSeatsOrRollback(bookingDto.getPassengers());

        Booking booking = new Booking();
        booking.setUserId(requesterId);
        booking.setUserEmail(requesterEmail);
        booking.setUserPhone(requesterPhone);
        booking.setFlightInstanceId(bookingDto.getFlightInstanceId());
        booking.setFareId(bookingDto.getFareId());
        booking.setStatus(BookingStatus.PENDING);

        List<Passenger> passengers = bookingDto.getPassengers().stream().map(PassengerMapper::toEntity).toList();
        passengers.forEach(passenger -> passenger.setBooking(booking));
        booking.setPassengers(passengers);

        booking.setAmount(fare.getPrice().multiply(BigDecimal.valueOf(passengers.size())));
        Booking saved = bookingRepository.save(booking);

        PaymentDto payment = initiatePaymentOrCancelBooking(saved);

        saved.setPaymentId(payment.getId());
        Booking withPayment = bookingRepository.save(saved);

        return BookingMapper.toDto(withPayment);
    }

    /**
     * If payment initiation itself fails (payment-service down, etc.) after every seat was already
     * successfully held and the booking already persisted PENDING, the booking must not be left
     * stuck forever - releases every held seat and marks the booking CANCELLED before rethrowing.
     * This closes the saga-compensation gap holdAllSeatsOrRollback doesn't cover: that one only
     * handles a failure *during* the seat-holding loop, before the booking row even exists.
     */
    private PaymentDto initiatePaymentOrCancelBooking(Booking booking) {
        PaymentDto paymentRequest = new PaymentDto();
        paymentRequest.setBookingId(booking.getId());
        paymentRequest.setAmount(booking.getAmount());
        try {
            return paymentClient.initiatePayment(paymentRequest);
        } catch (RuntimeException e) {
            for (Passenger passenger : booking.getPassengers()) {
                releaseSeatQuietly(passenger.getSeatInstanceId());
            }
            booking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(booking);
            throw e;
        }
    }

    /**
     * Holds one seat per passenger in order. If any hold fails partway through (e.g. passenger 3's
     * seat was already taken), releases every seat already held for this attempt before rethrowing
     * - otherwise those earlier seats would stay HELD forever with no other path back to AVAILABLE.
     */
    private void holdAllSeatsOrRollback(List<PassengerDto> passengers) {
        List<Long> heldSeatIds = new ArrayList<>();
        try {
            for (PassengerDto passenger : passengers) {
                holdSeatOrThrow(passenger.getSeatInstanceId());
                heldSeatIds.add(passenger.getSeatInstanceId());
            }
        } catch (RuntimeException e) {
            for (Long seatInstanceId : heldSeatIds) {
                releaseSeatQuietly(seatInstanceId);
            }
            throw e;
        }
    }

    private void holdSeatOrThrow(Long seatInstanceId) {
        try {
            seatClient.holdSeat(seatInstanceId);
        } catch (FeignException.Conflict e) {
            throw new SeatUnavailableException("Seat " + seatInstanceId + " is no longer available");
        } catch (NoFallbackAvailableException e) {
            // spring.cloud.openfeign.circuitbreaker.enabled wraps every Feign exception this way
            // (including legitimate 4xx responses, not just infra failures) when no fallback bean exists.
            if (e.getCause() instanceof FeignException.Conflict) {
                throw new SeatUnavailableException("Seat " + seatInstanceId + " is no longer available");
            }
            throw e;
        }
    }

    /**
     * Best-effort compensation - if the release call itself fails (seat-service down, network
     * blip), the seat is left stuck HELD with no retry. That's the same class of unsolved saga-
     * compensation gap as a payment failing after a successful hold; this only closes the
     * multi-seat partial-failure case, not every way a held seat can get orphaned.
     */
    private void releaseSeatQuietly(Long seatInstanceId) {
        try {
            seatClient.releaseSeat(seatInstanceId);
        } catch (RuntimeException e) {
            log.warn("Failed to release held seat id={} during booking rollback: {}", seatInstanceId, e.getMessage());
        }
    }

    public BookingDto getBookingById(Long id, Long requesterId, String requesterRole) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));

        boolean isOwner = booking.getUserId() != null && booking.getUserId().equals(requesterId);
        boolean isAdmin = ROLE_SYSTEM_ADMIN.equals(requesterRole);
        if (!isOwner && !isAdmin) {
            throw new ForbiddenException("Booking " + id + " does not belong to the requesting user");
        }

        return BookingMapper.toDto(booking);
    }
}
