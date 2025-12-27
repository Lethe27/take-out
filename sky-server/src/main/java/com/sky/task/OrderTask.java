package com.sky.task;

import com.sky.constant.MessageConstant;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 *自定义定时任务类处理外卖订单
 */
@Component
@Slf4j
public class OrderTask {


    @Autowired
    private OrderMapper orderMapper;

    /**
     * 每分钟执行一次，处理超时未支付订单
     */

    @Scheduled(cron = "0 0/1 * * * ?")//每分钟执行一次
//    @Scheduled(cron = "1/5 * * * * ?")//每5秒执行一次(测试用)
    public void cancelOverTimeOrder(){
        log.info("处理超时未支付订单的定时任务执行了....，{}",LocalDateTime.now());

        //1.查询超时未支付订单

       LocalDateTime overTime = LocalDateTime.now().minusMinutes(15);
       List<Orders> overTimeOrders = orderMapper.findOverTimeOrders(Orders.PENDING_PAYMENT,overTime);
       if(overTimeOrders!=null&&overTimeOrders.size()>0){

          for (Orders order : overTimeOrders) {
            //2.修改订单状态为已取消
            order.setStatus(Orders.CANCELLED);
            order.setCancelReason(MessageConstant.OVER_TIME_UNPAID_CANCEL);
            order.setCancelTime(LocalDateTime.now());
            orderMapper.update(order);
          }
       }
    }


    /**
     * 每天凌晨1点执行一次，处理超时未完成订单
     */
    @Scheduled(cron = "0 0 1 * * ?")//每天凌晨1点执行一次
//    @Scheduled(cron = "0/5 * * * * ?")//每5秒执行一次(测试用)
    public void completeOverTimeOrder(){
        log.info("处理超时未完成订单的定时任务执行了...，{}",LocalDateTime.now());

        //1.查询超时未完成订单
        LocalDateTime overTime = LocalDateTime.now().minusHours(1);
        List<Orders> overTimeOrders = orderMapper.findOverTimeOrders(Orders.DELIVERY_IN_PROGRESS,overTime);

        if(overTimeOrders!=null&&overTimeOrders.size()>0){
            for (Orders order : overTimeOrders) {
                //2.修改订单状态为已完成
                order.setStatus(Orders.COMPLETED);
                order.setDeliveryTime(LocalDateTime.now());
                orderMapper.update(order);
            }
        }
    }
}
