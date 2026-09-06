package com.services.service;

import com.services.entity.SeatInstance;
import com.services.entity.SeatStatus;
import com.services.exception.SeatNotAvailableException;
import com.services.repository.SeatInstanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the fix, not just the code: without the pessimistic lock in
 * {@link SeatInstanceService#holdSeat}, this test flakes and lets more than one thread win.
 *
 * <p>Runs against a real, disposable MySQL in a container instead of the shared dev database
 * (seat_db on locationdb) - reproducible on any machine with Docker, no dev data ever touched,
 * and safe to run in parallel with a developer actively poking at the real seat_db by hand.
 * {@code @ServiceConnection} wires the container's JDBC URL/credentials into Spring's
 * DataSource autoconfiguration automatically - no manual {@code @DynamicPropertySource} needed,
 * and it takes priority over whatever config-server would otherwise supply.
 */
@Testcontainers
@SpringBootTest(properties = "eureka.client.enabled=false")
class SeatInstanceConcurrencyTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

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
