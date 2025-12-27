package com.sky.properties;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sky.map")
@Data
public class MapProperties {

    //百度地图请求地址
    private String url;

    // 百度地图AK
    private String ak;

    // 返回格式
    private String output;

}
