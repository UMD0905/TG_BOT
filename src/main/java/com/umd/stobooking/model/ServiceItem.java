package com.umd.stobooking.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// Named ServiceItem to avoid clash with Spring's @Service annotation
@Getter
@Setter
@Entity
@Table(name = "service")
public class ServiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private ServiceCategory category;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "price_from")
    private Integer priceFrom;

    @Column(name = "price_to")
    private Integer priceTo;

    @Column(nullable = false)
    private boolean active = true;
}
