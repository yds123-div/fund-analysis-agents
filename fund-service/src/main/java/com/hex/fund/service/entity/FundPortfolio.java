package com.hex.fund.service.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hex.fund.service.entity.base.BaseEntity;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户基金持仓实体，包含成本基准和买入明细。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("TM_FUND_PORTFOLIO")
public class FundPortfolio extends BaseEntity {

    @TableField("USER_ID")
    private Long userId;
    @TableField("FUND_CODE")
    private String fundCode;
    @TableField("HOLDING_AMOUNT")
    private BigDecimal holdingAmount;
    @TableField("AVG_COST")
    private BigDecimal avgCost;
    @TableField("PURCHASE_DATE")
    private LocalDateTime purchaseDate;
    @TableField("NOTES")
    private String notes;
    @TableField("AUTO_DIP")
    private Boolean autoDip;
    @TableField("DIP_AMOUNT")
    private BigDecimal dipAmount;
    @TableField("DIP_FREQUENCY")
    private String dipFrequency;
}
