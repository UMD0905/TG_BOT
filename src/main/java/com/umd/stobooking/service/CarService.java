package com.umd.stobooking.service;

import com.umd.stobooking.model.Car;
import com.umd.stobooking.model.CarBrand;
import com.umd.stobooking.model.CarModel;
import com.umd.stobooking.model.Client;
import com.umd.stobooking.repository.CarBrandRepository;
import com.umd.stobooking.repository.CarModelRepository;
import com.umd.stobooking.repository.CarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CarService {

    private final CarRepository carRepository;
    private final CarBrandRepository carBrandRepository;
    private final CarModelRepository carModelRepository;

    public List<Car> getCarsForClient(long clientId) {
        return carRepository.findByClientId(clientId);
    }

    public Optional<CarBrand> findBrand(long brandId) {
        return carBrandRepository.findById(brandId);
    }

    public Optional<CarModel> findModel(long modelId) {
        return carModelRepository.findById(modelId);
    }

    public List<CarBrand> getAllBrands() {
        return carBrandRepository.findAllByOrderByNameAsc();
    }

    public List<CarModel> getModelsForBrand(long brandId) {
        return carModelRepository.findByBrandIdOrderByNameAsc(brandId);
    }

    @Transactional
    public Car addCar(Client client, long brandId, long modelId, int year, String engineInfo) {
        CarBrand brand = carBrandRepository.findById(brandId)
                .orElseThrow(() -> new IllegalArgumentException("Brand not found: " + brandId));
        CarModel model = carModelRepository.findById(modelId)
                .orElseThrow(() -> new IllegalArgumentException("Model not found: " + modelId));

        Car car = new Car();
        car.setClient(client);
        car.setBrand(brand);
        car.setModel(model);
        car.setYear(year);
        car.setEngineInfo(engineInfo);
        car.setCreatedAt(LocalDateTime.now());
        return carRepository.save(car);
    }
}
