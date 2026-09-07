package com.services.controller;

import com.services.dto.BookingDto;
import com.services.service.BookingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingDto> createBooking(@RequestBody BookingDto bookingDto,
                                                     @RequestHeader("X-User-Id") Long requesterId,
                                                     @RequestHeader("X-User-Email") String requesterEmail) {
        return new ResponseEntity<>(bookingService.createBooking(bookingDto, requesterId, requesterEmail), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingDto> getBookingById(@PathVariable Long id,
                                                      @RequestHeader("X-User-Id") Long requesterId,
                                                      @RequestHeader("X-User-Roles") String role) {
        return ResponseEntity.ok(bookingService.getBookingById(id, requesterId, role));
    }
}
