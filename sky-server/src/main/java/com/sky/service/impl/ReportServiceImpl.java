package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ReportMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private OrderMapper orderMapper;
    /**
     * 获取营业额统计数据
     * param begin 开始时间
     * param end 结束时间
     * return 营业额统计数据的视图对象
     */
    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);

        while (!begin.equals(end)){
            begin = begin.plusDays(1);//日期计算，获得指定日期后1天的日期
            dateList.add(begin);
        }

        List<Double> turnoverList = new ArrayList<>();
        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            Map map = new HashMap();
            map.put("status", Orders.COMPLETED);
            map.put("begin",beginTime);
            map.put("end", endTime);
            Double turnover = orderMapper.sumByMap(map);
            turnover = turnover == null ? 0.0 : turnover;
            turnoverList.add(turnover);
        }

        //数据封装
        return TurnoverReportVO.builder()
                .dateList(StringUtils.join(dateList,","))
                .turnoverList(StringUtils.join(turnoverList,","))
                .build();
//        //存放从begin到end的所有日期
//        List<LocalDate> dateList = new ArrayList<>();
//        dateList.add(begin);
//
//        while(!begin.equals(end)){
//            begin = begin.plusDays(1);
//            dateList.add(begin);
//        }
//
//        //计算总营业额
//        Map<LocalDate, BigDecimal> map = new HashMap();
//        //获取指定时间段内的订单
//        for(LocalDate date : dateList){
//            LocalDateTime startOfDay = LocalDateTime.of(date, LocalDateTime.MIN.toLocalTime());
//            LocalDateTime endOfDay = LocalDateTime.of(date, LocalDateTime.MAX.toLocalTime());
//            List<Orders> ordersList = reportMapper.findOrdersByTimeRangeAndStatus(Orders.COMPLETED, startOfDay, endOfDay);
//
//            if(ordersList == null || ordersList.size() == 0){
//                continue; //当日无订单，跳过，不终止统计
//            }
//            BigDecimal amount = BigDecimal.ZERO;
//            for (Orders order : ordersList) {
//                //累加每日订单总金额
//                amount = amount.add(order.getAmount());
//                if (map.get(date)!=null){
//                    BigDecimal dayAmount = (BigDecimal) map.get(date);
//                    map.put(date,dayAmount.add(amount));
//                }else {
//                    map.put(date,amount);
//                }
//            }
//        }
//        //准备返回数据
//        StringBuilder turnover = new StringBuilder();
//
//        dateList.forEach(date -> {
//            BigDecimal dayTurnover = map.get(date);
//            turnover.append(dayTurnover != null ? dayTurnover : BigDecimal.ZERO);
//            if(!date.equals(end)){ // 不是最后一个日期，添加逗号分隔
//                turnover.append(",");
//            }
//        });
//        String  turnoverList = turnover.toString();
//        System.out.println("日期列表："+dateList);
//        System.out.println("营业额列表："+turnoverList);
//
//        return TurnoverReportVO.builder().dateList(StringUtils.join(dateList,","))
//                .turnoverList(turnoverList).build();
    }

    /**
     * 根据时间区间统计指定状态的订单数量
     * @param beginTime
     * @param endTime
     * @param status
     * @return
     */
    private Integer getOrderCount(LocalDateTime beginTime, LocalDateTime endTime, Integer status) {
        Map map = new HashMap();
        map.put("status", status);
        map.put("begin",beginTime);
        map.put("end", endTime);
        return orderMapper.countByMap(map);
    }
    public OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);

        while (!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        //每天订单总数集合
        List<Integer> orderCountList = new ArrayList<>();
        //每天有效订单数集合
        List<Integer> validOrderCountList = new ArrayList<>();
        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            //查询每天的总订单数 select count(id) from orders where order_time > ? and order_time < ?
            Integer orderCount = getOrderCount(beginTime, endTime, null);

            //查询每天的有效订单数 select count(id) from orders where order_time > ? and order_time < ? and status = ?
            Integer validOrderCount = getOrderCount(beginTime, endTime, Orders.COMPLETED);

            orderCountList.add(orderCount);
            validOrderCountList.add(validOrderCount);
        }

        //时间区间内的总订单数
        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();
        //时间区间内的总有效订单数
        Integer validOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();
        //订单完成率
        Double orderCompletionRate = 0.0;
        if(totalOrderCount != 0){
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
        }
        return OrderReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .orderCountList(StringUtils.join(orderCountList, ","))
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
//        // ========== 1. 生成日期列表 ==========
//        List<LocalDate> dateList = new ArrayList<>();
//        LocalDate currentDate = begin;
//        while (!currentDate.isAfter(end)) {
//            dateList.add(currentDate);
//            currentDate = currentDate.plusDays(1);
//        }
//
//        // ========== 2. 初始化统计变量 ==========
//        int totalOrderCount = 0;    // 区间总订单数量
//        int validOrderCount = 0;    // 区间有效订单数量（已完成）
//        // key=日期，value=[当日订单总数, 当日有效订单数]，指定泛型消除强转风险
//        Map<LocalDate, List<Integer>> dayOrderMap = new HashMap<>();
//        // 初始化每日数据：默认0单、0有效单
//        dateList.forEach(date -> dayOrderMap.put(date, Arrays.asList(0, 0)));
//        // ========== 3. 遍历日期，统计每日订单数据 ==========
//        for (LocalDate date : dateList) {
//            // 构建当日时间范围
//            LocalDateTime startOfDay = date.atStartOfDay();
//            LocalDateTime endOfDay = LocalDateTime.of(date, LocalDateTime.MAX.toLocalTime());
//
//            // 查询当日已完成的订单
//            List<Orders> ordersList = reportMapper.findOrdersByTimeRange(startOfDay, endOfDay);
//            if (Objects.isNull(ordersList) || ordersList.isEmpty()) {
//                continue; // 当日无订单，跳过，不终止统计
//            }
//            List<Orders> validOrdersList = reportMapper.findOrdersByTimeRangeAndStatus(Orders.COMPLETED, startOfDay, endOfDay);
//            // 统计当日订单总数、有效订单数
//            int dayTotalCount = ordersList.size();
//            int dayValidCount = validOrdersList.size(); // 因为查询条件就是已完成，所以全部是有效单
//
//            // 累加区间统计数据
//            totalOrderCount += dayTotalCount;
//            validOrderCount += dayValidCount;
//
//            // 更新当日统计数据到map
//            dayOrderMap.put(date, Arrays.asList(dayTotalCount, dayValidCount));
//        }
//
//        // ========== 4. 组装返回的字符串列表（修复：正确提取数据，格式合规） ==========
//
//        StringBuilder orderCountList = new StringBuilder();
//        StringBuilder validOrderCountList = new StringBuilder();
//
//        dateList.forEach(date -> {
//            List<Integer> dayData = dayOrderMap.get(date);
//            orderCountList.append(dayData.get(0)!=null ? dayData.get(0) : 0);
//            validOrderCountList.append(dayData.get(1)!=null ? dayData.get(1) : 0);
//
//            if(!date.equals(end)){ // 不是最后一个日期，添加逗号分隔
//                orderCountList.append(",");
//                validOrderCountList.append(",");
//            }
//        });
//
//        // ========== 5. 计算订单完成率（修复：处理除数为0，避免报错） ==========
//        Double orderCompletionRate = 0.0;
//        if (totalOrderCount > 0) {
//            orderCompletionRate = (validOrderCount * 1.0) / totalOrderCount * 100;
//        }
//
//        // ========== 6. 封装返回结果 ==========
//        return OrderReportVO.builder()
//                .dateList(StringUtils.join(dateList,","))
//                .orderCountList(orderCountList.toString())
//                .validOrderCountList(validOrderCountList.toString())
//                .totalOrderCount(totalOrderCount)
//                .validOrderCount(validOrderCount)
//                .orderCompletionRate(orderCompletionRate)
//                .build();
    }

    private Integer getUserCount(LocalDateTime beginTime, LocalDateTime endTime) {
       Map map = new HashMap();
       map.put("beginTime",beginTime);
       map.put("endTime",endTime);
       return userMapper.countByMap(map);
    }
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        // ========== 1. 生成日期列表 ==========
        List<LocalDate> dateList = new ArrayList<>();
        LocalDate currentDate = begin;
        dateList.add(currentDate);
        while (!currentDate.equals(end)) {
            currentDate = currentDate.plusDays(1);
            dateList.add(currentDate);
        }

        List<Integer> newUserList = new ArrayList<>(); //新增用户数
        List<Integer> totalUserList = new ArrayList<>(); //总用户数

        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            //新增用户数量 select count(id) from user where create_time > ? and create_time < ?
            Integer newUser = getUserCount(beginTime, endTime);
            //总用户数量 select count(id) from user where  create_time < ?
            Integer totalUser = getUserCount(null, endTime);

            newUserList.add(newUser);
            totalUserList.add(totalUser);
        }

        return UserReportVO.builder()
                .dateList(StringUtils.join(dateList,","))
                .newUserList(StringUtils.join(newUserList,","))
                .totalUserList(StringUtils.join(totalUserList,","))
                .build();
    }

    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);
        List<GoodsSalesDTO> goodsSalesDTOList = orderMapper.getSalesTop10(beginTime, endTime);

        String nameList = StringUtils.join(goodsSalesDTOList.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList()), ",");
        String numberList = StringUtils.join(goodsSalesDTOList.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList()), ",");

        return SalesTop10ReportVO.builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
    }

    public void exportReport() {
        // TODO 导出报表数据为Excel文件
    }
}
