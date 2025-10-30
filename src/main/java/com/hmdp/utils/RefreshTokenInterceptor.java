package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取请求头里的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            // 没有token，直接放行，让LoginInterceptor决定是否需要拦截
            return true;
        }
        
        // 检查是否是Bearer token格式
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        
        //2.基于token获取用户
        String key = LOGIN_USER_KEY + token;
        Map<Object, Object> user = stringRedisTemplate.opsForHash().entries(key);
        
        //3.判断用户是否存在
        if (user.isEmpty()) {
            // token无效，直接放行，让LoginInterceptor决定是否需要拦截
            return true;
        }
        
        //4.将查询到的hash数据转换为UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(user, new UserDTO(), false);
        
        //5.保存用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);
        
        //6.刷新token有效期
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
        log.debug("检测到用户登录状态，刷新 token TTL，用户ID={}", userDTO.getId());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //清除ThreadLocal中的用户信息
        UserHolder.removeUser();
    }
}