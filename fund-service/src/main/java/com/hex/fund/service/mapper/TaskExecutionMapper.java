package com.hex.fund.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hex.fund.service.entity.TaskExecution;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务执行记录 Mapper。
 */
@Mapper
public interface TaskExecutionMapper extends BaseMapper<TaskExecution> {
}
