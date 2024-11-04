package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author dadaguai
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Result queryHotBlog(Integer current);

    Result queryBlogById(Long id);

    Result queryMyBlog(Integer current);

    Result likeBlog(Long id);

    Result saveBlog(Blog blog);

    Result queryTop5BlogLikes(Long BlogId);

    Result queryBlogByUserId(Integer current, Long id);

    Result queryBlogOfFollow(Long max, Integer offset);
}
