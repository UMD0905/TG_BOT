package com.umd.stobooking.repository;

import com.umd.stobooking.model.BotStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface BotStateRepository extends JpaRepository<BotStateEntity, Long> {

    // Expire stale states: reset to IDLE anything older than the given cutoff
    @Modifying
    @Query("""
            UPDATE BotStateEntity s
            SET s.currentState = 'IDLE', s.stateData = NULL, s.updatedAt = :now
            WHERE s.updatedAt < :cutoff AND s.currentState <> 'IDLE'
            """)
    int resetStaleStates(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);
}
