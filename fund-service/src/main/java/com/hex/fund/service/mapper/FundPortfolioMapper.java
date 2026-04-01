package com.hex.fund.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hex.fund.service.entity.FundPortfolio;
import org.apache.ibatis.annotations.Mapper;

/**
 * 基金持仓 Mapper。
 */
@Mapper
public interface FundPortfolioMapper extends BaseMapper<FundPortfolio> {
}
