package com.dineflow.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CustomerSessionRequest(
        @NotBlank(message = "Guest name is required")
        String name,

        @NotBlank(message = "Mobile number is required")
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Invalid mobile number format")
        String mobileNumber,

        @NotBlank(message = "Signed entry token is required")
        String signedEntryToken
) {}