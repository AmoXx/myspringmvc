package com.alibaba.test.demo.controller;

import com.alibaba.test.demo.service.UserService;
import com.alibaba.test.framework.annotation.MyAutowired;
import com.alibaba.test.framework.annotation.MyController;
import com.alibaba.test.framework.annotation.MyRequestMapping;
import com.alibaba.test.framework.annotation.MyRequestParam;

@MyController
@MyRequestMapping("/test")
public class TestController {

    @MyAutowired
    private UserService userService;

    @MyRequestMapping("/query")
    public String query(@MyRequestParam("name") String name) {
        return userService.query(name);
    }
}
