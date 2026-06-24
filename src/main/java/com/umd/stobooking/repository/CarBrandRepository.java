package com.umd.stobooking.repository;

import com.umd.stobooking.model.CarBrand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CarBrandRepository extends JpaRepository<CarBrand, Long> {
    List<CarBrand> findAllByOrderByNameAsc();
}
