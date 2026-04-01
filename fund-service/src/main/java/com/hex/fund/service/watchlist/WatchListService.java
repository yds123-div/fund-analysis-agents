package com.hex.fund.service.watchlist;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hex.fund.service.entity.WatchList;
import com.hex.fund.service.mapper.WatchListMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 自选基金管理服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WatchListService {

    private final WatchListMapper watchListMapper;

    /**
     * 查询用户自选列表，按优先级排序。
     */
    public List<WatchList> listByUser(Long userId) {
        return watchListMapper.selectList(new LambdaQueryWrapper<WatchList>()
                .eq(WatchList::getUserId, userId).orderByDesc(WatchList::getPriority));
    }

    /**
     * 添加自选基金。
     */
    public void add(Long userId, String fundCode, String notes) {
        WatchList existing = watchListMapper.selectOne(new LambdaQueryWrapper<WatchList>()
                .eq(WatchList::getUserId, userId).eq(WatchList::getFundCode, fundCode));
        if (existing != null) {
            log.info("自选基金已存在: 用户={}, 基金={}", userId, fundCode);
            return;
        }
        watchListMapper.insert(WatchList.builder()
                .userId(userId).fundCode(fundCode).priority(0).notes(notes)
                .createdAt(LocalDateTime.now()).build());
        log.info("自选基金已添加: 用户={}, 基金={}", userId, fundCode);
    }

    /**
     * 移除自选基金。
     */
    public void remove(Long userId, String fundCode) {
        watchListMapper.delete(new LambdaQueryWrapper<WatchList>()
                .eq(WatchList::getUserId, userId).eq(WatchList::getFundCode, fundCode));
        log.info("自选基金已移除: 用户={}, 基金={}", userId, fundCode);
    }

    /**
     * 更新优先级或备注。
     */
    public void update(WatchList watchList) {
        watchListMapper.updateById(watchList);
    }
}
