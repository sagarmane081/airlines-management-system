package com.services.event;

import com.common.event.BookingConfirmedEvent;
import com.services.entity.SeatInstance;
import com.services.entity.SeatStatus;
import com.services.exception.ResourceNotFoundException;
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
        for (Long seatInstanceId : event.getSeatInstanceIds()) {
            markSeatBooked(seatInstanceId);
        }
    }

    private void markSeatBooked(Long seatInstanceId) {
        SeatInstance seatInstance = seatInstanceRepository.findById(seatInstanceId)
                .orElseThrow(() -> new ResourceNotFoundException("SeatInstance not found with id: " + seatInstanceId));

        if (seatInstance.getStatus() == SeatStatus.BOOKED) {
            // Redelivered BookingConfirmedEvent - already applied, skip the redundant write.
            return;
        }

        seatInstance.setStatus(SeatStatus.BOOKED);
        seatInstanceRepository.save(seatInstance);
    }
}
