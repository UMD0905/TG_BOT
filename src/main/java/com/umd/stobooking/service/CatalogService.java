package com.umd.stobooking.service;

import com.umd.stobooking.model.ServiceCategory;
import com.umd.stobooking.model.ServiceItem;
import com.umd.stobooking.repository.ServiceCategoryRepository;
import com.umd.stobooking.repository.ServiceItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ServiceCategoryRepository categoryRepository;
    private final ServiceItemRepository serviceItemRepository;

    public List<ServiceCategory> listCategories() {
        return categoryRepository.findAllByOrderBySortOrderAsc();
    }

    public List<ServiceItem> listServices(long categoryId) {
        return serviceItemRepository.findByCategoryIdAndActiveTrue(categoryId);
    }

    public Optional<ServiceItem> findService(long serviceId) {
        return serviceItemRepository.findById(serviceId);
    }
}
