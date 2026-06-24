package com.umd.stobooking.repository;

import com.umd.stobooking.model.WorkingHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, Long> {
    Optional<WorkingHours> findByDayOfWeek(int dayOfWeek);
}
