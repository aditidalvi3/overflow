package com.orderflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProductRequest(
        @NotBlank String sku,
        @NotBlank String name,
        @NotNull @Min(0) Long priceCents,
        @NotNull @Min(0) Integer initialQuantity
) {
}
