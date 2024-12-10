package com.yml.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yml.dto.Result;
import com.yml.entity.Follow;

public interface IFollowService extends IService<Follow> {
    Result follow(Long followUserId, Boolean isFollow);

    Result followOrNot(Long followUserId);

    Result followCommons(Long id);
}
