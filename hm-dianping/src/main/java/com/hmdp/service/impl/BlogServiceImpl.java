package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constant.SystemConstants;
import com.hmdp.constant.UserHolder;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.redis.RedisConstants;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
@Slf4j
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    public static final int Max_TopBlogLikes = 5;
    private final IUserService userService;
    private final StringRedisTemplate stringRedisTemplate;
    private final FollowServiceImpl followService;

    public BlogServiceImpl(IUserService userService, StringRedisTemplate stringRedisTemplate, FollowServiceImpl followService) {
        this.userService = userService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.followService = followService;
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        //添加用户和点赞信息
        records.forEach(this::addIsLike);
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = this.getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        addIsLike(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryMyBlog(Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = this.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 点赞
     *
     * @param id
     * @return
     */
    @Transactional
    @Override
    public Result likeBlog(Long id) {
        //判断是点赞还是取消赞的操作
        String userId = String.valueOf(UserHolder.getUser().getId());
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId);
        if (score != null) {
            stringRedisTemplate.opsForZSet().remove(key, userId);
            this.update().setSql("liked=liked-1").eq("id", id).update();
            log.info("取消点赞");
            return Result.ok();
        }
        stringRedisTemplate.opsForZSet().add(key, userId, System.currentTimeMillis());
        this.update().setSql("liked=liked+1").eq("id", id).update();
        log.info("点赞成功");
        return Result.ok();
    }

    /**
     * 发布以及推送给关注用户发布的博客
     *
     * @param blog
     * @return
     */
    @Transactional
    @Override
    public Result saveBlog(@NotNull Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSave = this.save(blog);
        if (!isSave) {
            return Result.fail("发布失败");
        }
        List<Follow> followList = followService.query().eq("follow_user_id", user.getId()).list();
        for (Follow follow : followList) {
            stringRedisTemplate.opsForZSet()
                    .add(RedisConstants.FEED__FOLLOW_KEY + follow.getUserId(),
                            blog.getId().toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    /**
     * 查询点赞top5
     *
     * @param BlogId
     * @return
     */
    @Override
    public Result queryTop5BlogLikes(Long BlogId) {
        String key = RedisConstants.BLOG_LIKED_KEY + BlogId;
        Set<String> Top5likesSet = stringRedisTemplate.opsForZSet().range(key, 0, Max_TopBlogLikes - 1);
        if (Top5likesSet == null || Top5likesSet.isEmpty()) {
            return Result.ok(Collections.EMPTY_LIST);
        }
        List<Long> userIds = Top5likesSet.stream().map(Long::valueOf).collect(Collectors.toList());
        String strIdList = StrUtil.join(",", userIds);
        List<UserDTO> userList = userService.query().in("id", userIds)
                .last("order by field(id," + strIdList + ")")
                .list().stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userList);
    }

    @Override
    public Result queryBlogByUserId(Integer current, Long id) {
        Page<Blog> blogPage = this.query().eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> blogs = blogPage.getRecords();
        return Result.ok(blogs);
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FEED__FOLLOW_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .rangeByScoreWithScores(key, 0, max, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>(typedTuples.size());
        int os = 1;
        Long minTime = 0L;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            ids.add(Long.valueOf(tuple.getValue()));
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        //查询blog
        String isStr = StrUtil.join(",", ids);
        List<Blog> blogs = this.query().in("id", ids).last("order by field(id," + isStr + ")").list();
        for (Blog blog : blogs) {
            addIsLike(blog);
        }
        ScrollResult r = new ScrollResult().setList(blogs).setOffset(os).setMinTime(minTime);
        return Result.ok(r);
    }

    private void queryUserByBlog(@NotNull Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    private void addIsLike(Blog blog) {
        queryUserByBlog(blog);
        UserDTO user = UserHolder.getUser();
        if (user != null) {
            Boolean isLiked = stringRedisTemplate.opsForZSet()
                    .score(RedisConstants.BLOG_LIKED_KEY + blog.getId(), String.valueOf(user.getId())) != null;
            //已经点过赞
            blog.setIsLike(Boolean.TRUE.equals(isLiked));
        }
    }
}
