package com.ra.rabnbserver.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员 -公共接口
 * 获取枚举
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/common")
public class AdminCommonController extends CommonController{
}
