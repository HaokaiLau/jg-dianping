package com.jgdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jgdp.dto.Result;
import com.jgdp.entity.SeckillVoucher;
import com.jgdp.entity.VoucherOrder;
import com.jgdp.mapper.VoucherOrderMapper;
import com.jgdp.service.ISeckillVoucherService;
import com.jgdp.service.IVoucherOrderService;
import com.jgdp.utils.RedisConstants;
import com.jgdp.utils.RedisIdBuilder;
import com.jgdp.utils.SimpleRedisLock;
import com.jgdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdBuilder redisIdBuilder;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 秒杀优惠券
     *
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        //2.判断秒杀是否开始
        //秒杀开始时间是否在当前时间之后
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始!");
        }

        //3.判断秒杀是否结束
        //秒杀结束时间是否在当前时间之前
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束!");
        }

        //4.判断库存是否充足
        Integer stock = seckillVoucher.getStock();
        if (stock < 1) {
            return Result.fail("库存不足!");
        }

        //使用悲观锁来实现一人一单功能
        Long userId = UserHolder.getUser().getId();
        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
        //尝试获取锁
        boolean isLock = lock.tryLock(RedisConstants.LOCK_TTL);
        if (!isLock) {
            //获取锁失败
            return Result.fail("每位用户仅限购买一次!!");
        }
        try {
            //防止事务失效，要使用该对象的代理对象进行方法的调用
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.creatVoucherOrder(voucherId);
        } finally {
            //最后必须要释放锁，防止出现死锁
            lock.unlock();
        }

    }

    @Transactional
    public Result creatVoucherOrder(Long voucherId) {
        //一人一单
        //用户id
        Long userId = UserHolder.getUser().getId();

        //查询订单
        Long count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        //判断订单是否存在
        if (count > 0) {
            //该用户已经购买过了，不允许继续购买
            return Result.fail("每位用户仅限购买一次!!");
        }


        //5.扣减库存（使用乐观锁的CAS方法解决超卖）
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")//set stock = stock - 1
                .eq("voucher_id", voucherId)
                .gt("stock", 0)//where id = #{voucherId} and stock > 0
                .update();
        if (!success) {
            return Result.fail("系统繁忙，请稍后重试...");
        }

        //6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //7.返回订单id
        long orderId = redisIdBuilder.nextId("order");
        voucherOrder.setId(orderId);
        //用户id
        voucherOrder.setUserId(userId);
        //代金券id
        voucherOrder.setVoucherId(voucherId);
        //保存到数据库
        save(voucherOrder);

        return Result.ok(orderId);
    }
}
