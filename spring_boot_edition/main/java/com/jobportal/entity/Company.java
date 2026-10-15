package com.jobportal.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 100)
    private String industry;

    @Column(length = 100)
    private String location;

    @Column(length = 255)
    private String website;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String logoUrl;

    @Column(nullable = false)
    private boolean verified = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Company() {}

    public Company(String name, String industry, String location, String website, String description) {
        this.name = name;
        this.industry = industry;
        this.location = location;
        this.website = website;
        this.description = description;
        this.verified = false;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
