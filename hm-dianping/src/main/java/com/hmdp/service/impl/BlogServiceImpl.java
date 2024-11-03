package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constant.SystemConstants;
import com.hmdp.constant.UserHolder;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.redis.RedisConstants;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    public static final int Max_TopBlogLikes = 5;
    private final IUserService userService;
    private final StringRedisTemplate stringRedisTemplate;

    public BlogServiceImpl(IUserService userService, StringRedisTemplate stringRedisTemplate) {
        this.userService = userService;
        this.stringRedisTemplate = stringRedisTemplate;
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

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        this.save(blog);
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

    private void queryUserByBlog(Blog blog) {
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
