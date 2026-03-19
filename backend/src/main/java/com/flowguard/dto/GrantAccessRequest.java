package com.flowguard.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class GrantAccessRequest {
    @NotBlank
    @Email
    public String accountantEmail;

    public GrantAccessRequest() {
    }

    public GrantAccessRequest(String accountantEmail) {
        this.accountantEmail = accountantEmail;
    }
}
