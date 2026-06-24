package com.umd.stobooking.repository;

import com.umd.stobooking.model.ServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long> {
    List<ServiceCategory> findAllByOrderBySortOrderAsc();
}
