package com.hex.fund.datasource.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fund manager information.
 */
@Data
@Builder
public class FundManagerData {

    private String fundCode;
    private String managerName;
    private String managerId;
    private LocalDate startDate;
    private BigDecimal totalScale;
    private Integer yearsOfExperience;
    private String education;
    private String company;
    private String source;
}
