package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisWorker redisWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;



    @Override
    public Result seckillVoucher(Long voucherId) {

        // 1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        log.info("当前时间: {}, 秒杀开始时间: {}, 秒杀结束时间: {}",
                LocalDateTime.now(), voucher.getBeginTime(), voucher.getEndTime());
        // 检查voucher对象是否为null
        if (voucher == null) {
            return Result.fail("优惠券信息不存在");
        }

        // 检查beginTime是否为null
        if (voucher.getBeginTime() == null) {
            return Result.fail("优惠券开始时间未设置");
        }
        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {


            return Result.fail("秒杀尚未开始");
        }
        // 3.判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }
        // 4.判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        // 7.返回订单id
        Long id = UserHolder.getUser().getId();
       // synchronized (id.toString().intern()) {
        //创建锁的对象
        SimpleRedisLock lock = new SimpleRedisLock("order:" + id, stringRedisTemplate);
        //尝试获取锁
        boolean tryLock = lock.tryLock(1200);
        //判断是否获取锁成功
        if (!tryLock) {
            return Result.fail("不允许重复下单");
        }
        //获取代理对象*（事务）
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }
        //    }

    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //5，实现一人一单业务
        // 5.1.获取用户
        Long id = UserHolder.getUser().getId();
        // 5.2.查询订单
        int count = query().eq("user_id", id)
                .eq("voucher_id", voucherId)
                .count();
        if (count > 0) {
            return Result.fail("不能重复下单");
        }
        // 6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                //      .eq("stock", voucher.getStock())
                .gt("stock", 0) //库存大于0
                .update();
        if (!success) {
            return Result.fail("库存不足");
        }

        // 6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 6.1.订单id
        long order = redisWorker.nextId("order");
        voucherOrder.setId(order);
        // 6.2.用户id
        voucherOrder.setUserId(id);
        // 6.3.优惠券id
        voucherOrder.setVoucherId(voucherId);
        // 6.4.保存订单
        save(voucherOrder);
        return Result.ok(order);
    }

}
