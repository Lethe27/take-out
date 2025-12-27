package com.sky.mapper;

import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReportMapper {

    @Select("SELECT * FROM orders WHERE status = #{status} AND order_time BETWEEN #{begin} AND #{end}")
    List<Orders> findOrdersByTimeRangeAndStatus(Integer status, LocalDateTime begin, LocalDateTime end);

    @Select("SELECT * FROM orders WHERE order_time BETWEEN #{begin} AND #{end}")
    List<Orders> findOrdersByTimeRange(LocalDateTime begin, LocalDateTime end);

}
