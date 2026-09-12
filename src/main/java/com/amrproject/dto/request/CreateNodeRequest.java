package com.amrproject.dto.request;

import com.amrproject.domain.entity.NodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateNodeRequest(
        @NotBlank String code,
        @NotNull BigDecimal x,
        @NotNull BigDecimal y,
        @NotNull NodeType nodeType
) {
}
