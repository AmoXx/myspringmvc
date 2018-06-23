package com.alibaba.test.demo.service.impl;

import com.alibaba.test.demo.service.UserService;
import com.alibaba.test.framework.annotation.MyService;

@MyService
public class UserServiceImpl implements UserService {
    @Override
    public String query(String name) {
        return "Hello " + name;
    }
}
