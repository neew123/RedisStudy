package com.yml.service.impl;


import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yml.dto.Result;
import com.yml.dto.UserDTO;
import com.yml.entity.Follow;
import com.yml.mapper.FollowMapper;
import com.yml.service.IFollowService;
import com.yml.service.IUserService;
import com.yml.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yml.utils.RedisConstants.FOLLOWS_KEY;


@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取当前用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        Follow follow = new Follow();
        String key = FOLLOWS_KEY + userId;
        if(isFollow){
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            follow.setCreateTime(LocalDateTime.now());
            boolean save = save(follow);
            if(save){
                stringRedisTemplate.opsForSet().add(key,String.valueOf(followUserId));
            }
        }else {
            boolean remove = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            if(remove){
                stringRedisTemplate.opsForSet().remove(key,String.valueOf(followUserId));
            }
        }
        return Result.ok();
    }

    @Override
    public Result followOrNot(Long followUserId) {
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        //查询是否关注：select count(*) from tb_follow where user_id = ? and follow_user_id = ?
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        //当前用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        String currentKey = FOLLOWS_KEY + userId;
        String compareKey = FOLLOWS_KEY + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(currentKey, compareKey);
        if(intersect == null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> collectUsrs = userService.listByIds(ids)
                .stream()
                .map(usr -> BeanUtil.copyProperties(usr, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(collectUsrs);
    }
}
