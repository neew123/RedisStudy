package com.yml.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yml.dto.Result;
import com.yml.dto.UserDTO;
import com.yml.entity.Blog;
import com.yml.entity.User;
import com.yml.mapper.BlogMapper;
import com.yml.service.IBlogService;
import com.yml.service.IUserService;
import com.yml.utils.SystemConstants;
import com.yml.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yml.utils.RedisConstants.BLOG_LIKED_KEY;


@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userservice;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("this blog doesn't exists");
        }
        this.queryBlogUser(blog);
        // 查询blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String blogKey = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(blogKey, userId.toString());
        if (score == null) {
            //未点赞
            boolean isSuccess = update().setSql("liked = liked+1").eq("id", id).update();
            if (isSuccess) {
                //use sorted_set: zadd key value score
                stringRedisTemplate.opsForZSet().add(blogKey, userId.toString(),System.currentTimeMillis());
            }
        } else {
            //已点赞
            boolean isSuccess = update().setSql("liked = liked-1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(blogKey, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogLikes(Long id) {
        // 查询blog top5点赞用户
        String blogKey = BLOG_LIKED_KEY + id;
        Set<String> top5Id = stringRedisTemplate.opsForZSet().range(blogKey, 0, 4);
        if(top5Id == null || top5Id.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = top5Id.stream().map(Long::valueOf).collect(Collectors.toList());
        //直接查询ids不会按照传入参数顺序进行返回，因此需要设置： where id IN (5,1) ORDER BY FIELD(id,5,1)
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOs = userservice.query()
                        .in("id",ids).last("ORDER BY FIELD(id,"+idStr+")").list()
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return  Result.ok(userDTOs);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userservice.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    private void isBlogLiked(Blog blog) {
        // 获取当前用户
        UserDTO user = UserHolder.getUser();
        if(user == null){
            return;
        }
        Long userId = user.getId();
        String blogKey = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(blogKey, userId.toString());
        blog.setIsLike(score!=null);
    }

}
