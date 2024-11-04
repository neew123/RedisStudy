package com.yml.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yml.dto.LoginFormDTO;
import com.yml.dto.Result;
import com.yml.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IUserService extends IService<User> {



    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sedCode(String phone, HttpSession session);
}
