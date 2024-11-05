package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constant.UserHolder;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.constant.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.redis.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author dadaguai
 * @since 2021-12-22
 */
@Slf4j   //日志注解
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //获取验证码
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确");
        }
        //创建6位验证码
        String code = RandomUtil.randomNumbers(6);
        //new:保存采用Redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);


        //使用日志的形式获取验证码
        log.debug("用户获取的验证码为:{}", code);
        return Result.ok();
    }

    //用户登录：手机号加密码或者手机号加验证码
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String code = loginForm.getCode();
        String phone = loginForm.getPhone();
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确");
        }
        //判断登录形式
        if (code == null || RegexUtils.isCodeInvalid(code)) {
            //校验验证码形式
            return Result.fail("验证码形式不正确");
        }
        //将之前用户请求保存到Redis的验证码和请求登录的验证码进行匹配
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码匹配失败");
        }
        //根据手机号判断数据库中是否有数据
        User user = this.query().eq("phone", phone).one();  //one()返回查询结果中的第一个
        if (user == null) {
            //第一次登录，则创建新用户
            user = createNewUser(phone);
        }
        String token = UUID.randomUUID().toString(true);
        //数据库有用户信息，登录成功,并将用户信息存到Redis中,使用hash存储用户对象UserDto
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);   //转为hash
        // 手动将 id 字段转换为字符串
        if (userMap.containsKey("id")) {
            userMap.put("id", String.valueOf(userMap.get("id")));
        }
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    /**
     * 查询用户信息
     *
     * @param id
     * @return
     */
    @Override
    public Result queryUserById(Long id) {
        User user = this.query().eq("id", id).one();
        if (user == null) {
            return Result.fail("用户不存在");
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    /**
     * 登出
     *
     * @return
     */
    @Override
    public Result logout(HttpServletRequest request) {
        String tokenKey = LOGIN_USER_KEY + request.getHeader("authorization");
        //删除这个key
        Boolean isDelete = stringRedisTemplate.delete(tokenKey);
        if (Boolean.TRUE.equals(isDelete)) {
            return Result.ok();
        }
        return Result.fail("登出失败");
    }

    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String key = USER_SIGN_KEY + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyy:MM"));
        int day = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key, day - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String key = USER_SIGN_KEY + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyy:MM"));
        int day = now.getDayOfMonth();
        List<Long> signList = stringRedisTemplate.opsForValue()
                .bitField(key, BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(day))
                        .valueAt(0));
        if (signList == null || signList.isEmpty()) {
            return Result.ok(0);
        }
        //签到记录数
        Long num = signList.get(0);
        if (num == 0) {
            return Result.ok(0);
        }
        int count = 0;
        while ((num & 1) == 1) {
            count++;
            num >>>= 1;
        }
        return Result.ok(count);
    }

    //根据手机号创建新用户
    private User createNewUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //保存到数据库中
        this.save(user);
        return user;
    }
}
