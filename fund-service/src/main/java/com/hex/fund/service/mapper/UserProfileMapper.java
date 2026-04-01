package com.hex.fund.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hex.fund.service.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户投资画像 Mapper。
 */
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
