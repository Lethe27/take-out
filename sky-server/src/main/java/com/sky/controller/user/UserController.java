package com.sky.controller.user;

import com.sky.dto.UserLoginDTO;
import com.sky.result.Result;
import com.sky.service.UserService;
import com.sky.vo.UserLoginVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/user/user")
@RestController
@Slf4j
@Api(tags = "C端用户相关接口")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 用户登录
     * @param
     * @return
     */
    @PostMapping("/login")
    @ApiOperation("C端用户登录")
    public Result<UserLoginVO> login(@RequestBody UserLoginDTO userLoginDTO){
        log.info("微信用户登录：{}",userLoginDTO.getCode());
        UserLoginVO userLoginVO =  userService.login(userLoginDTO);
        return Result.success(userLoginVO);
    }
}
