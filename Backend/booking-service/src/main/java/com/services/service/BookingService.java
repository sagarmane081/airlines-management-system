package com.services.service;

import com.common.dto.FareDto;
import com.common.dto.PaymentDto;
import com.services.client.PaymentClient;
import com.services.client.PricingClient;
import com.services.client.SeatClient;
import com.services.dto.BookingDto;
import com.services.entity.Booking;
import com.services.entity.BookingStatus;
import com.services.exception.SeatUnavailableException;
import com.services.mapper.BookingMapper;
import com.services.repository.BookingRepository;
import feign.FeignException;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.stereotype.Service;

@Service
public class BookingService {

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

    public BookingDto createBooking(BookingDto bookingDto) {
        FareDto fare = pricingClient.getFareById(bookingDto.getFareId());

        try {
            seatClient.holdSeat(bookingDto.getSeatInstanceId());
        } catch (FeignException.Conflict e) {
            throw new SeatUnavailableException("Seat " + bookingDto.getSeatInstanceId() + " is no longer available");
        } catch (NoFallbackAvailableException e) {
            // spring.cloud.openfeign.circuitbreaker.enabled wraps every Feign exception this way
            // (including legitimate 4xx responses, not just infra failures) when no fallback bean exists.
            if (e.getCause() instanceof FeignException.Conflict) {
                throw new SeatUnavailableException("Seat " + bookingDto.getSeatInstanceId() + " is no longer available");
            }
            throw e;
        }

        Booking booking = new Booking();
        booking.setFlightInstanceId(bookingDto.getFlightInstanceId());
        booking.setFareId(bookingDto.getFareId());
        booking.setSeatInstanceId(bookingDto.getSeatInstanceId());
        booking.setAmount(fare.getPrice());
        booking.setStatus(BookingStatus.PENDING);
        Booking saved = bookingRepository.save(booking);

        PaymentDto paymentRequest = new PaymentDto();
        paymentRequest.setBookingId(saved.getId());
        paymentRequest.setAmount(saved.getAmount());
        PaymentDto payment = paymentClient.initiatePayment(paymentRequest);

        saved.setPaymentId(payment.getId());
        Booking withPayment = bookingRepository.save(saved);

        return BookingMapper.toDto(withPayment);
    }

    public BookingDto getBookingById(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Booking not found with id: " + id));
        return BookingMapper.toDto(booking);
    }
}
