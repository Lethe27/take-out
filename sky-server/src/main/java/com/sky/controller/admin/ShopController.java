package com.sky.controller.admin;

import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("adminShopController")
@RequestMapping("/admin/shop")
@Slf4j
@Api(tags = "商铺相关接口")
public class ShopController {

    public static final String SHOP_STATUS_KEY = "SHOP_STATUS";

    @Autowired
    private RedisTemplate redisTemplate;


    /**
     * 修改商铺状态
     *param status
     * @return
     */
    @PutMapping("/{status}")
    @ApiOperation("修改商铺状态")
    public Result setStatus(@PathVariable Integer status) {
       log.info("修改商铺状态：{}", status == 1 ? "营业中" : "打烊中");
        redisTemplate.opsForValue().set(SHOP_STATUS_KEY, status);
        return Result.success();
    }

    /**
     * 获取商铺状态
     *param status
     * @return
     */
    @GetMapping("/status")
    @ApiOperation("获取商铺状态")
    public Result<Integer> getStatus() {
        Integer status = (Integer) redisTemplate.opsForValue().get(SHOP_STATUS_KEY);
        log.info("获取商铺状态为：{}", status == 1 ? "营业中" : "打烊中");
        return Result.success(status);
    }

}
