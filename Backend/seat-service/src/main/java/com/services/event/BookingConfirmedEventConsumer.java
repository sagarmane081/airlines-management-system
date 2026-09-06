package com.services.event;

import com.common.event.BookingConfirmedEvent;
import com.services.entity.SeatInstance;
import com.services.entity.SeatStatus;
import com.services.repository.SeatInstanceRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BookingConfirmedEventConsumer {

    private final SeatInstanceRepository seatInstanceRepository;

    public BookingConfirmedEventConsumer(SeatInstanceRepository seatInstanceRepository) {
        this.seatInstanceRepository = seatInstanceRepository;
    }

    @KafkaListener(topics = "booking.confirmed", groupId = "seat-service-group")
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        SeatInstance seatInstance = seatInstanceRepository.findById(event.getSeatInstanceId())
                .orElseThrow(() -> new RuntimeException("SeatInstance not found with id: " + event.getSeatInstanceId()));

        seatInstance.setStatus(SeatStatus.BOOKED);
        seatInstanceRepository.save(seatInstance);
    }
}
