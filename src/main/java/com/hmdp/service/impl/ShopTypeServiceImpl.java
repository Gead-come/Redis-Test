package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result cacheShopTypeList() {
        //1 先查看redis中是否存在
        String cacheShopTypeKey = CACHE_SHOP_TYPE_KEY;
        String ShopTypeKey= stringRedisTemplate.opsForValue().get(cacheShopTypeKey);
        log.debug("Redis中查到的店铺类型缓存：{}", ShopTypeKey);
        //2 不存在，查询数据库
        if (StrUtil.isNotBlank(ShopTypeKey)) {
            log.debug("命中缓存，直接返回");
            //存在，直接返回
            List<ShopType> typeList = JSONUtil.toList(ShopTypeKey, ShopType.class);
            return Result.ok(typeList);

        }
        //不存在，查询数据库
        log.debug("缓存未命中，从数据库查询");
        List<ShopType> list = list();
        if (list == null || list.isEmpty()) {
            return Result.fail("店铺类型不存在");

        }

        //3 存储到redis中
        stringRedisTemplate.opsForValue().set(
                cacheShopTypeKey,
                JSONUtil.toJsonStr(list),
                 CACHE_SHOP_TYPE_TTL,
                 TimeUnit.MINUTES

        );
        //4 返回结果
        log.debug("成功写入 Redis 缓存：{}",list);
        return Result.ok(list);

    }
}
