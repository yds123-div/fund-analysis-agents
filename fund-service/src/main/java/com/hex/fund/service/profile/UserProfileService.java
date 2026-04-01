package com.hex.fund.service.profile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hex.fund.service.entity.UserProfile;
import com.hex.fund.service.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 投资画像管理服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileMapper profileMapper;

    public UserProfile getByUser(Long userId) {
        return profileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId));
    }

    public void saveOrUpdate(UserProfile profile) {
        UserProfile existing = getByUser(profile.getUserId());
        if (existing != null) {
            profile.setId(existing.getId());
            profileMapper.updateById(profile);
        } else {
            profileMapper.insert(profile);
        }
        log.info("投资画像已更新: 用户={}", profile.getUserId());
    }
}
