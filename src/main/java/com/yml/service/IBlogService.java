package com.yml.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yml.dto.Result;
import com.yml.entity.Blog;


public interface IBlogService extends IService<Blog> {

    Result queryBlogById(Long id);

    Result likeBlog(Long id);

    Result queryHotBlog(Integer current);

    Result queryBlogLikes(Long id);

    Result saveBlog(Blog blog);

    Result queryBlogOfFollow(Long max, Integer offset);
}
