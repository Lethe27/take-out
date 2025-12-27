package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.LocationBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.mapper.UserMapper;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.SearchHttpAKUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
public class OrderServiceImpl implements OrderService, InitializingBean {
    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WeChatPayUtil weChatPayUtil;

    @Autowired
    private  SearchHttpAKUtil searchHttpAKUtil;

    @Autowired
    private WebSocketServer webSocketServer;

    //距离限制
    private final double DELIVERY_RANGE = 5.0;

    private final String MERCHANT_ADDRESS = "江苏省无锡市滨湖区星光广场";
    private double MERCHANT_LNG = 120.295639;
    private double MERCHANT_LAT = 31.537688;
    // 4. 核心：仅初始化商家经纬度
    @Override
    public void afterPropertiesSet() throws Exception {
        initMerchantLatLng();
    }

    // 5. 解析商家经纬度
    private void initMerchantLatLng() {
        try {
            String jsonpStr = searchHttpAKUtil.getLocation(MERCHANT_ADDRESS);
            // 步骤1：空值校验
            if (jsonpStr == null || jsonpStr.trim().isEmpty()) {
                System.err.println("商家经纬度JSONP为空，使用默认值");
                return;
            }
            System.out.println("API返回的JSONP原始数据：" + jsonpStr);

            // 步骤2：剥离JSONP前缀，提取纯JSON字符串
            String pureJson = "";
            // 情况1：带showLocation&&showLocation(...)前缀（你的场景）
            if (jsonpStr.contains("showLocation&&showLocation(")) {
                // 截取括号内的内容（纯JSON）
                int start = jsonpStr.indexOf("(") + 1;
                int end = jsonpStr.lastIndexOf(")");
                if (start > 0 && end > start) {
                    pureJson = jsonpStr.substring(start, end);
                }
            } else {
                // 情况2：如果是纯JSON，直接使用
                pureJson = jsonpStr;
            }
            System.out.println("剥离后的纯JSON：" + pureJson);

            // 步骤3：校验剥离后的JSON是否为空
            if (pureJson.isEmpty()) {
                System.err.println("提取纯JSON失败，使用默认值");
                return;
            }

            // 步骤4：解析纯JSON
            JSONObject obj = JSONObject.parseObject(pureJson);
            if (obj.getIntValue("status") == 0) {
                MERCHANT_LNG = obj.getJSONObject("result").getJSONObject("location").getDoubleValue("lng");
                MERCHANT_LAT = obj.getJSONObject("result").getJSONObject("location").getDoubleValue("lat");
                System.out.println("商家经纬度初始化成功：" + MERCHANT_LNG + "," + MERCHANT_LAT);
            } else {
                System.err.println("百度地图API返回失败：" + obj.getString("message") + "，使用默认值");
            }
        } catch (Exception e) {
            // 关键：不抛业务异常，仅记录日志
            System.err.println("初始化商家经纬度失败，使用默认值！原因：" + e.getMessage());
             throw new LocationBusinessException(MessageConstant.LOCAL_FOUND_ERROR);
        }
    }

    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional
    public OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO) {
        //异常业务处理
        //地址信息为空、购物车为空
        Long addressBookId = ordersSubmitDTO.getAddressBookId();
        AddressBook addressBook = addressBookMapper.getById(addressBookId);

        if(addressBook == null){
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        //校验地址是否在配送范围内
        String address = addressBook.getProvinceName()+addressBook.getCityName()+addressBook.getDistrictName()+addressBook.getDetail();
        if(!validDistance(address)){
            throw new LocationBusinessException(MessageConstant.OUT_OF_DELIVERY_RANGE);
        }
        Long userId = BaseContext.getCurrentId();
        ShoppingCart  shoppingCart = ShoppingCart.builder().userId(userId).build();
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);

        if(shoppingCartList==null || shoppingCartList.size() == 0){
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //向订单表插入一条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);
        orders.setAddress(address);

        orderMapper.insert(orders);


        ArrayList<OrderDetail> orderDetailList = new ArrayList<>();
        //向订单明细表插入多条数据
        for(ShoppingCart cart : shoppingCartList){
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        }
        orderMapper.insertBatch(orderDetailList);

        //清空购物车
        shoppingCartMapper.deleteByUserId(userId);

        //封装返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();

        return orderSubmitVO;
    }

    private boolean validDistance(String address) {
        String location = null;
        try {
            location = searchHttpAKUtil.getLocation(address);
        } catch (Exception e) {
            throw new LocationBusinessException(MessageConstant.LOCAL_FOUND_ERROR);
        }
        // 空值校验：避免空字符串导致解析失败
        if (location == null || location.trim().isEmpty()) {
            System.out.println("错误：获取到的位置信息为空");
            return false;
        }

        // 步骤2：剥离JSONP前缀，提取纯JSON字符串
        String pureJson = "";
        // 情况1：带showLocation&&showLocation(...)前缀（你的场景）
        if (location.contains("showLocation&&showLocation(")) {
            // 截取括号内的内容（纯JSON）
            int start = location.indexOf("(") + 1;
            int end = location.lastIndexOf(")");
            if (start > 0 && end > start) {
                pureJson = location.substring(start, end);
            }
        } else {
            // 情况2：如果是纯JSON，直接使用
            pureJson = location;
        }
        // 步骤3：校验剥离后的JSON是否为空
        if (pureJson.isEmpty()) {
            return false;
        }
        JSONObject jsonObject = JSONObject.parseObject(pureJson);

        boolean isInRange = false;


        if (jsonObject.getIntValue("status") == 0) {
            double userLng = jsonObject.getJSONObject("result").getJSONObject("location").getDoubleValue("lng");
            double userLat = jsonObject.getJSONObject("result").getJSONObject("location").getDoubleValue("lat");
            // 6. 计算用户与商家的球面距离（单位：公里）
            double distance = calculateDistance(MERCHANT_LNG, MERCHANT_LAT, userLng, userLat);
            // 6. 核心逻辑：验证经纬度是否在配送范围内（示例逻辑，你需替换成真实规则）
           isInRange = distance <= DELIVERY_RANGE;
           System.out.println("用户地址经纬度：经度=" + userLng + "，纬度=" + userLat + "，是否在配送范围：" + isInRange);
        }

        return isInRange;
    }


    /**
     * 核心方法：计算两个经纬度之间的球面距离（哈维正弦公式）
     * @param lng1 点1经度
     * @param lat1 点1纬度
     * @param lng2 点2经度
     * @param lat2 点2纬度
     * @return 距离（单位：公里）
     */
    private double calculateDistance(double lng1, double lat1, double lng2, double lat2) {
        // 地球半径（公里）
        final double EARTH_RADIUS = 6371.0;

        // 将经纬度转换为弧度（数学计算需要弧度值）
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double radLng1 = Math.toRadians(lng1);
        double radLng2 = Math.toRadians(lng2);

        // 哈维正弦公式计算球面距离
        double diffLat = radLat2 - radLat1;
        double diffLng = radLng2 - radLng1;
        double a = Math.sin(diffLat / 2) * Math.sin(diffLat / 2)
                + Math.cos(radLat1) * Math.cos(radLat2)
                * Math.sin(diffLng / 2) * Math.sin(diffLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        // 距离 = 地球半径 × 弧度差
        return EARTH_RADIUS * c;
    }



    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

        // 通过WebSocket推送来单通知 type orderId content
        Map map = new HashMap();
        map.put("type", 1); //1表示来单提醒 2表示客户催单
        map.put("orderId", ordersDB.getId());
        map.put("content", "订单号：" + outTradeNo + "，新订单待接单！");
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
        System.out.println("订单支付成功，订单号：" + outTradeNo);
    }

    /**
     * 分页查询
     * @param ordersPageQueryDTO
     * @return
     */
    public PageResult pageQuery(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        Page<Orders> ordersPageList = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> orderVOlist = new ArrayList();

        if (ordersPageList.getResult() != null || !ordersPageList.getResult().isEmpty()) {
            for (Orders orders : ordersPageList.getResult()) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                Long orderId = orders.getId();//订单id

                List<OrderDetail> orderDetailsByOrderId = orderMapper.getOrderDetailsByOrderId(orderId);
                orderVO.setOrderDetailList(orderDetailsByOrderId);

                String orderDishes = orderMapper.getDishesByOrderId(orderId);
                orderVO.setOrderDishes(orderDishes);
                orderVOlist.add(orderVO);
            }
        }
        return new PageResult(ordersPageList.getTotal(), orderVOlist);
    }
    /**
     * 根据id查询订单详情
     * @param id
     * @return
     */
    public OrderVO getOrdersById(Long id) {
        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);

        List<OrderDetail> orderDetailList = orderMapper.getOrderDetailsByOrderId(id);
        orderVO.setOrderDetailList(orderDetailList);

        String orderDishes = orderMapper.getDishesByOrderId(id);
        orderVO.setOrderDishes(orderDishes);
        return orderVO;
    }

    @Transactional
    public void cancelOrder(Long id) {
        //查询订单
        Orders orders = orderMapper.getById(id);
        if(orders == null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //只有待付款的订单可以取消
        if(!orders.getStatus().equals(Orders.PENDING_PAYMENT)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //修改订单状态为已取消
        Orders updateOrder = Orders.builder()
                .id(id)
                .status(Orders.CANCELLED)
                .build();

        orderMapper.update(updateOrder);
    }

    @Transactional
    public void repetition(Long id) {
        //查询订单
        Orders orders = orderMapper.getById(id);

        if(orders == null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //清除当前用户的购物车数据
        Long userId = BaseContext.getCurrentId();
        shoppingCartMapper.deleteByUserId(userId);

        Long OrderId = orders.getId();

        //查询该订单对应的订单明细
        List<OrderDetail> orderDetailList = orderMapper.getOrderDetailsByOrderId(OrderId);

        if(orderDetailList == null || orderDetailList.size() == 0){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //将订单明细数据批量添加到购物车表
        ArrayList<ShoppingCart> shoppingCartList = new ArrayList<>();

        for(OrderDetail orderDetail : orderDetailList){
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(orderDetail, shoppingCart);
            shoppingCart.setUserId(userId);
            shoppingCartList.add(shoppingCart);
        }

        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    /**
     * 各个状态的订单数量统计
     * @return
     */
    public OrderStatisticsVO statistics() {
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        //待接单
        Integer toBeConfirmedCount = orderMapper.countByStatus(Orders.TO_BE_CONFIRMED);
        orderStatisticsVO.setToBeConfirmed(toBeConfirmedCount);

        //待派送
        Integer confirmedCount = orderMapper.countByStatus(Orders.CONFIRMED);
        orderStatisticsVO.setConfirmed(confirmedCount);

        //派送中
        Integer deliveredInProgressCount = orderMapper.countByStatus(Orders.DELIVERY_IN_PROGRESS);
        orderStatisticsVO.setDeliveryInProgress(deliveredInProgressCount);

        return orderStatisticsVO;
    }

    /**
     * 接单
     * @param ordersConfirmDTO
     * @return
     */
    @Transactional
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        //修改订单状态为已接单
        Orders updateOrder = new Orders();
        BeanUtils.copyProperties(ordersConfirmDTO, updateOrder);
        updateOrder.setStatus(Orders.CONFIRMED);

        orderMapper.update(updateOrder);
    }

    /**
     * 拒单
     * @param ordersRejectionDTO
     * @return
     */
    @Transactional
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        //修改订单状态为已取消
        Orders updateOrder = new Orders();
        BeanUtils.copyProperties(ordersRejectionDTO, updateOrder);
        updateOrder.setStatus(Orders.CANCELLED);
        updateOrder.setRejectionReason((ordersRejectionDTO.getRejectionReason()));

        orderMapper.update(updateOrder);
    }

    /**
     * 取消订单
     * @param ordersCancelDTO
     * @return
     */
    public void cancel(OrdersCancelDTO ordersCancelDTO) {
        //修改订单状态为已取消
        Orders updateOrder = new Orders();
        BeanUtils.copyProperties(ordersCancelDTO, updateOrder);
        updateOrder.setStatus(Orders.CANCELLED);
        updateOrder.setCancelTime(LocalDateTime.now());
        updateOrder.setCancelReason((ordersCancelDTO.getCancelReason()));

        orderMapper.update(updateOrder);
    }

    /**
     * 派送订单
     * @param id
     * @return
     */
    public void delivery(Long id) {
        //修改订单状态为派送中
        Orders updateOrder = new Orders();
        updateOrder.setId(id);
        updateOrder.setStatus(Orders.DELIVERY_IN_PROGRESS);
        orderMapper.update(updateOrder);
    }

    /**
     * 完成订单
     * @param id
     * @return
     */
    public void complete(Long id) {
        //修改订单状态为已完成
        Orders updateOrder = new Orders();
        updateOrder.setId(id);
        updateOrder.setStatus(Orders.COMPLETED);
        updateOrder.setDeliveryTime(LocalDateTime.now());
        orderMapper.update(updateOrder);
    }

    /**
     * 订单支付修改版
     * @param ordersPaymentDTO
     * @return
     */
    @Override
    public OrdersSubmitModifyDTO submitOrderModify(OrdersPaymentDTO ordersPaymentDTO) {
        OrdersSubmitModifyDTO  ordersSubmitModifyDTO = new OrdersSubmitModifyDTO();

        paySuccess(ordersPaymentDTO.getOrderNumber());
        /*  获取预计送达时间*/
        Orders ordersDB = orderMapper.getByNumber(ordersPaymentDTO.getOrderNumber());

        ordersSubmitModifyDTO.setEstimatedDeliveryTime(ordersDB.getEstimatedDeliveryTime());

        return ordersSubmitModifyDTO;
    }

    /**
     * 客户催单
     * @param id
     */
    public void reminder(Long id) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getById(id);

        if(ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 通过WebSocket推送催单通知 type orderId content
        Map map = new HashMap();
        map.put("type", 2); //1表示来单提醒 2表示客户催单
        map.put("orderId", id);
        map.put("content", "订单号：" + ordersDB.getNumber() + "，客户催单啦！");

        webSocketServer.sendToAllClient(JSON.toJSONString(map));
        System.out.println("客户催单，订单id：" + id);
    }

}
