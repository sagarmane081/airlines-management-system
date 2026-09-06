package com.services.service;

import com.services.entity.SeatInstance;
import com.services.entity.SeatStatus;
import com.services.exception.SeatNotAvailableException;
import com.services.repository.SeatInstanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the fix, not just the code: without the pessimistic lock in
 * {@link SeatInstanceService#holdSeat}, this test flakes and lets more than one thread win.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
class SeatInstanceConcurrencyTest {

    @Autowired
    private SeatInstanceService seatInstanceService;

    @Autowired
    private SeatInstanceRepository seatInstanceRepository;

    @Test
    void onlyOneConcurrentHoldWinsForTheSameSeat() throws InterruptedException {
        SeatInstance seat = new SeatInstance();
        seat.setFlightInstanceId(-1L);
        seat.setSeatNumber("CONCURRENCY-TEST");
        seat.setCabinClass("ECONOMY");
        seat.setStatus(SeatStatus.AVAILABLE);
        Long seatId = seatInstanceRepository.save(seat).getId();

        int threadCount = 15;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    seatInstanceService.holdSeat(seatId);
                    successCount.incrementAndGet();
                } catch (SeatNotAvailableException e) {
                    conflictCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        assertEquals(true, executor.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(1, successCount.get());
        assertEquals(threadCount - 1, conflictCount.get());

        SeatInstance finalState = seatInstanceRepository.findById(seatId).orElseThrow();
        assertEquals(SeatStatus.HELD, finalState.getStatus());
    }
}
