package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //不符合拒接
            return Result.fail("手机号校验失败");
        }
        //符合 生成验证码
        String code = RandomUtil.randomNumbers(6);
        //将验证码保存到redis中
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证码
        log.debug("发送验证码成功：{}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //不符合拒接
            return Result.fail("手机号校验失败");
        }
        //从redis中获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if(cacheCode == null || !cacheCode.equals(code)){
            //不一致报错
            return Result.fail("验证码校验失败");
        }
        //一致根据手机号查询用户
        User user = query().eq("phone", phone).one();
        if (user == null) {
            //不存在 创建用户
            user = createUserWithPhone(phone);
        }
        //保存用户信息到redis中
        //随机生成token 作为登录令牌
        String token = UUID.randomUUID().toString(true);
        //将user对象转化为hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName,fieldValue) ->fieldValue.toString()));
        //存储
        String key = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(key,userMap);
        //设置key的有效期
        stringRedisTemplate.expire(key,LOGIN_USER_TTL,TimeUnit.MINUTES);
        //返回token
         return Result.ok(token);
    }

    @Override
    public Result sign() {
        //获取当前用户
        Long userid = UserHolder.getUser().getId();
        //获取日期
        LocalDateTime now = LocalDateTime.now();
        //拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userid + keySuffix;
        //获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth() - 1;
        //写入redis
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        //获取当前用户
        Long userid = UserHolder.getUser().getId();
        //获取日期
        LocalDateTime now = LocalDateTime.now();
        //拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userid + keySuffix;
        //获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth() - 1;
        //获取本月戒指今天为止的所有签到记录，返回的是一个十进制的数字
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            //没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        int count = 0;
        //循环遍历
        while (true) {
            //让这个数字与1做与运算 得到的数字是最后一个bit位 判断这个bit位是否为零
            if ((num & 1) == 0) {
                //如果为零 说明未签到 跳出
                break;
            }else {
                //签到 计数器加1
                count++;
            }
            //将数字右移一位 抛弃最后一个bit位 继续下一个bit位
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));
        //将用户添加到数据库中
        save(user);
        return user;
    }
}
