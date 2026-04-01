package com.hex.fund.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hex.fund.service.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
