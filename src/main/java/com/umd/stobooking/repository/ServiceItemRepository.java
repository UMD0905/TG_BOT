package com.umd.stobooking.repository;

import com.umd.stobooking.model.ServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceItemRepository extends JpaRepository<ServiceItem, Long> {
    List<ServiceItem> findByCategoryIdAndActiveTrue(Long categoryId);
}
