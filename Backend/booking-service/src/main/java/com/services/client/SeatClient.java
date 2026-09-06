package com.services.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "seat-service")
public interface SeatClient {

    @PostMapping("/api/seat-instances/{id}/hold")
    void holdSeat(@PathVariable("id") Long id);
}
