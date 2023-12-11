package com.dp.service.impl;

import com.dp.dto.Result;
import com.dp.entity.SeckillVoucher;
import com.dp.entity.VoucherOrder;
import com.dp.mapper.VoucherOrderMapper;
import com.dp.service.ISeckillVoucherService;
import com.dp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.utils.RedisIdWorker;
import com.dp.utils.SimpleRedisLock;
import com.dp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static com.dp.utils.RedisConstants.LOCK_SHOP_KEY;
import static com.dp.utils.RedisConstants.LOCK_SHOP_TTL;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author zy
 * @since 2023-12-06
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 秒杀优惠券
     * @param voucherId 优惠券ID
     * @return 返回消息
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀尚未开始！");
        }
        // 3.判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀已经结束！");
        }
        // 4.判断库存是否充足
        if (voucher.getStock() < 1) {
            // 库存不足
            return Result.fail("库存不足！");
        }
        Long userId = UserHolder.getUser().getId();
        RLock lock = redissonClient.getLock(LOCK_SHOP_KEY + userId);
        boolean tried = false;
        try {
            tried = lock.tryLock(0,LOCK_SHOP_TTL, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if(!tried){
            return Result.fail("不能重复购买！");
        }
        try {
            //暴露代理对象,否则Spring事务失效
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createOrderByVoucherId(voucherId);//事务和锁范围要一致
        }finally {
            lock.unlock();
        }
    }

    @Override
    @Transactional
    public Result createOrderByVoucherId(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if(count > 0){
            return Result.fail("不能重复购买！");
        }
        //5，扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock",0).update();
        if (!success) {
            //扣减库存
            return Result.fail("库存不足！");
        }
        //6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 6.1.订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 6.2.用户id
        voucherOrder.setUserId(userId);
        // 6.3.代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        return Result.ok(orderId);
    }
}
