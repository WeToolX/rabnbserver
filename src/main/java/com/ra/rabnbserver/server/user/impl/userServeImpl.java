package com.ra.rabnbserver.server.user.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.mapper.UserMapper;
import com.ra.rabnbserver.pojo.User;
import com.ra.rabnbserver.server.user.userServe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class userServeImpl extends ServiceImpl<UserMapper, User> implements userServe {
}
