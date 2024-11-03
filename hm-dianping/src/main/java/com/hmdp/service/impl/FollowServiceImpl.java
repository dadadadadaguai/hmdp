package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constant.UserHolder;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.redis.RedisConstants;
import com.hmdp.service.IFollowService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author dadaguai
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    private final StringRedisTemplate stringRedisTemplate;
    private final UserServiceImpl userService;

    public FollowServiceImpl(StringRedisTemplate stringRedisTemplate, UserServiceImpl userService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userService = userService;
    }

    /**
     * 关注/取关用户
     *
     * @param id
     * @param isFollow
     * @return
     */
    @Transactional
    @Override
    public Result follow(Long id, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FOLLOW_USER_KEY + userId;
        if (isFollow) {
            Follow follow = new Follow().setFollowUserId(id).setUserId(userId);
            boolean isSave = this.save(follow);
            if (isSave) {
                stringRedisTemplate.opsForSet().add(key, String.valueOf(id));
            }
        } else {
            //取关
            this.remove(new QueryWrapper<Follow>().lambda().eq(Follow::getUserId, userId).eq(Follow::getFollowUserId, id));
            stringRedisTemplate.opsForSet().remove(key, String.valueOf(id));
        }
        return Result.ok();
    }

    //查询是否关注用户
    @Override
    public Result isFollow(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FOLLOW_USER_KEY + userId;
//        Integer count = this.query().eq("user_id", userId).eq("follow_user_id", id).count();
        Boolean isFollow = stringRedisTemplate.opsForSet().isMember(key, String.valueOf(id));
        return Result.ok(Boolean.TRUE.equals(isFollow));
    }

    @Override
    public Result commonFollow(Long id) {
        //当前用户
        String key = RedisConstants.FOLLOW_USER_KEY + UserHolder.getUser().getId();
        //目标用户
        String key2 = RedisConstants.FOLLOW_USER_KEY + id;
        Set<String> commonFollowsSet = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (commonFollowsSet == null || commonFollowsSet.isEmpty()) {
            return Result.ok(Collections.EMPTY_LIST);
        }
        List<Long> ids = commonFollowsSet.stream().map(Long::valueOf).collect(Collectors.toList());
        List<User> commonFollowUser = userService.query().in("id", ids).list();
        List<UserDTO> users = commonFollowUser.stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(users);
    }
}
