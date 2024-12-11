package com.yml.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yml.dto.Result;
import com.yml.dto.ScrollResult;
import com.yml.dto.UserDTO;
import com.yml.entity.Blog;
import com.yml.entity.Follow;
import com.yml.entity.User;
import com.yml.mapper.BlogMapper;
import com.yml.service.IBlogService;
import com.yml.service.IFollowService;
import com.yml.service.IUserService;
import com.yml.utils.SystemConstants;
import com.yml.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yml.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.yml.utils.RedisConstants.FEED_USER_KEY;


@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userservice;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

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

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean save = save(blog);
        if(!save){
            return Result.fail("save blog failed");
        }
        //查询笔记作者的所有粉丝 select * from tb_follow where follow_user_id = ?
        List<Follow> folllows = followService.query().eq("follow_user_id", user.getId()).list();
        for(Follow folllow : folllows){
            Long userId = folllow.getUserId();
            //put to every fan inbox
            String inboxKey = FEED_USER_KEY + userId;
            stringRedisTemplate.opsForZSet().add(inboxKey,blog.getId().toString(),System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 获取当前用户收件箱
        Long userId = UserHolder.getUser().getId();
        String feedKey = FEED_USER_KEY + userId;
        //查询收件箱 ZREVRANGEBYSCORE key Max Min limit offset count
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(feedKey, 0, max, offset, 3);
        if(typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }
        //返回数据：blogId->blog、最小时间戳、offset(集合里面分数值等于最小时间戳的个数)
        List<Long> ids = new ArrayList<>(typedTuples.size());
        Long minTime = 0l;
        int reOffSet = 1;
        for(ZSetOperations.TypedTuple<String> tuple:typedTuples){
            ids.add(Long.valueOf(tuple.getValue()));
            long time = tuple.getScore().longValue();
            if(time == minTime){
                reOffSet++;
            }else {
                minTime = time;
                reOffSet = 1;
            }
        }
        // 根据id查询blogs
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        blogs.forEach(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        //返回封装结果
        ScrollResult scro = new ScrollResult();
        scro.setList(blogs);
        scro.setMinTime(minTime);
        scro.setOffset(reOffSet);
        return Result.ok(scro);
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
