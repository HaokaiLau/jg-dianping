package com.jgdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jgdp.dto.Result;
import com.jgdp.entity.ShopType;
import com.jgdp.mapper.ShopTypeMapper;
import com.jgdp.service.IShopTypeService;
import com.jgdp.utils.RedisConstants;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询店铺分类
     * @return
     */
    @Override
    public Result queryList() {
        //1.从redis中查询店铺分类缓存
        String shopTypeKey = RedisConstants.CACHE_SHOP_TYPE_KEY;
        String shopTypeJson = stringRedisTemplate.opsForValue().get(shopTypeKey);

        //2.判断是否存在
        if (StrUtil.isNotBlank(shopTypeJson)) {
            //3.存在，直接从redis返回
            List<ShopType> shopTypeList = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(shopTypeList);
        }

        //4.不存在，查询数据库
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();

        //5.判断是否存在
        if (CollectionUtils.isEmpty(shopTypeList)) {
            //6.不存在，返回错误信息
            return Result.fail("查询店铺分类异常...");
        }

        //7.存在，把数据写入redis中
        stringRedisTemplate.opsForValue().set(shopTypeKey,JSONUtil.toJsonStr(shopTypeList));

        //8.返回
        return Result.ok(shopTypeList);
    }
}
