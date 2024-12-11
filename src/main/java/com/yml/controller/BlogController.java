package com.yml.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yml.dto.Result;
import com.yml.dto.UserDTO;
import com.yml.entity.Blog;
import com.yml.entity.User;
import com.yml.service.IBlogService;
import com.yml.service.IUserService;
import com.yml.utils.SystemConstants;
import com.yml.utils.UserHolder;
import jakarta.websocket.server.PathParam;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;


@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id){
        return blogService.queryBlogById(id);
    }

    @GetMapping("/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id){
        return blogService.queryBlogLikes (id);
    }


    /**
     *
     * @param max:上一次查询的最小时间戳
     * @param offset：偏移量，即集合里面分数值等于最小时间戳的个数
     * @return List<Blog> 小于最小时间戳的笔记集合、minTime 本次查询推送的最小时间戳、offset 偏移量
     */
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(@RequestParam("lastId") Long max,@RequestParam(value = "offset",defaultValue = "0")Integer offset){
        return blogService.queryBlogOfFollow(max,offset);
    }

}
