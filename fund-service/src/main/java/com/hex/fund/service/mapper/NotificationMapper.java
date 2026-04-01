package com.hex.fund.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hex.fund.service.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知记录 Mapper。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
