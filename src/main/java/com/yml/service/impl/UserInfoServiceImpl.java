package com.yml.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yml.entity.UserInfo;
import com.yml.mapper.UserInfoMapper;
import com.yml.service.IUserInfoService;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
