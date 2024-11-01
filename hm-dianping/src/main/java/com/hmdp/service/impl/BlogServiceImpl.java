package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.constant.SystemConstants;
import com.hmdp.constant.UserHolder;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.redis.RedisConstants;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        Boolean isLike = stringRedisTemplate.opsForSet().isMember(RedisConstants.BLOG_LIKED_KEY + id, userId);
        if (Boolean.TRUE.equals(isLike)) {
            stringRedisTemplate.opsForSet().remove(RedisConstants.BLOG_LIKED_KEY + id, userId);
            this.update().setSql("liked=liked-1").eq("id", id).update();
            log.info("取消点赞");
            return Result.ok();
        }
        stringRedisTemplate.opsForSet().add(RedisConstants.BLOG_LIKED_KEY + id, userId);
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

    private void queryUserByBlog(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    private void addIsLike(Blog blog) {
        queryUserByBlog(blog);
        Long userId = UserHolder.getUser().getId();
        Boolean isLiked = stringRedisTemplate.opsForSet()
                .isMember(RedisConstants.BLOG_LIKED_KEY + blog.getId(), String.valueOf(userId));
        //已经点过赞
        blog.setIsLike(Boolean.TRUE.equals(isLiked));
    }
}
