package com.sky.config;


import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@Slf4j
public class RedisConfiguration {

    @Bean
    public RedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        log.info("开始装配Redis的模板对象");
        RedisTemplate redisTemplate = new RedisTemplate();
        //设置redis的连接工厂
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        //设置key的序列化器为字符串序列化器,用于将key序列化为字符串（默认是JdkSerializationRedisSerializer，存储时会出现乱码）
        redisTemplate.setKeySerializer(new StringRedisSerializer());

        log.info("RedisTemplate装配完成");
        return redisTemplate;
    }
}
