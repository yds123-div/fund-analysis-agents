package com.hex.fund.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hex.fund.service.entity.FundRecommendation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 基金推荐记录 Mapper。
 */
@Mapper
public interface FundRecommendationMapper extends BaseMapper<FundRecommendation> {
}
