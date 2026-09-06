package com.services.repository;

import com.services.entity.SeatInstance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SeatInstanceRepository extends JpaRepository<SeatInstance, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SeatInstance s where s.id = :id")
    Optional<SeatInstance> findByIdForUpdate(@Param("id") Long id);
}
