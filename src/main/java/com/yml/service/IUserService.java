package com.yml.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yml.dto.LoginFormDTO;
import com.yml.dto.Result;
import com.yml.entity.User;

import javax.servlet.http.HttpSession;


public interface IUserService extends IService<User> {



    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sedCode(String phone, HttpSession session);

    Result sign();

    Result signCount();
}
