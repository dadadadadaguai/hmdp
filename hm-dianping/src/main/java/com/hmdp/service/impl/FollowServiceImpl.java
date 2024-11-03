package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.constant.UserHolder;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

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
    /**
     * 关注/取关用户
     *
     * @param id
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long id, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        if (isFollow) {
            Follow follow = new Follow().setFollowUserId(id).setUserId(userId);
            this.save(follow);
        } else {
            //取关
            this.remove(new QueryWrapper<Follow>().lambda().eq(Follow::getUserId, userId).eq(Follow::getFollowUserId, id));
        }
        return Result.ok();
    }

    //查询是否关注用户
    @Override
    public Result isFollow(Long id) {
        Long userId = UserHolder.getUser().getId();
        Integer count = this.query().eq("user_id", userId).eq("follow_user_id", id).count();
        return Result.ok(count > 0);
    }
}
