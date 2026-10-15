package com.jobportal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class RecruiterVerificationDto {

    @NotBlank(message = "Official business email is required")
    @Email(message = "Invalid email format")
    private String businessEmail;

    @NotBlank(message = "Company registration / CIN number is required")
    private String companyRegistrationNumber;

    private String documentUrl;

    public RecruiterVerificationDto() {}

    public String getBusinessEmail() { return businessEmail; }
    public void setBusinessEmail(String businessEmail) { this.businessEmail = businessEmail; }

    public String getCompanyRegistrationNumber() { return companyRegistrationNumber; }
    public void setCompanyRegistrationNumber(String companyRegistrationNumber) { this.companyRegistrationNumber = companyRegistrationNumber; }

    public String getDocumentUrl() { return documentUrl; }
    public void setDocumentUrl(String documentUrl) { this.documentUrl = documentUrl; }
}
