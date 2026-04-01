package com.hex.fund.service.snapshot;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hex.fund.service.entity.DataSnapshot;
import com.hex.fund.service.mapper.DataSnapshotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Point-in-time data snapshot service for recommendation audit and analysis traceability.
 * Every analysis/recommendation run saves a snapshot of its input data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final DataSnapshotMapper snapshotMapper;

    /**
     * Save a data snapshot and return its unique ID.
     * Format: {type}_{date}_{uuid8}
     */
    public String saveSnapshot(String type, Object data) {
        return saveSnapshot(type, data, null);
    }

    public String saveSnapshot(String type, Object data, String source) {
        String snapshotId = type + "_" + LocalDate.now().format(DATE_FMT) + "_" + IdUtil.fastSimpleUUID().substring(0, 8);
        DataSnapshot snapshot = DataSnapshot.builder()
                .snapshotId(snapshotId)
                .snapshotType(type)
                .snapshotDate(LocalDate.now())
                .dataContent(JSONUtil.toJsonStr(data))
                .source(source)
                .build();
        snapshotMapper.insert(snapshot);
        log.debug("数据快照已保存: {} (类型={})", snapshotId, type);
        return snapshotId;
    }

    /**
     * Retrieve snapshot by ID.
     */
    public DataSnapshot getSnapshot(String snapshotId) {
        return snapshotMapper.selectOne(
                new LambdaQueryWrapper<DataSnapshot>().eq(DataSnapshot::getSnapshotId, snapshotId));
    }

    /**
     * Retrieve and deserialize snapshot data.
     */
    public <T> T getSnapshotData(String snapshotId, Class<T> clazz) {
        DataSnapshot snapshot = getSnapshot(snapshotId);
        if (snapshot == null || snapshot.getDataContent() == null) return null;
        return JSONUtil.toBean(snapshot.getDataContent(), clazz);
    }
}
