package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@RequiredArgsConstructor
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    private final StringRedisTemplate stringRedisTemplate;
    private final IUserService userService;
    @Override
    public Result followCommon(Long id) {
        //获取当前登录user
        Long userid = UserHolder.getUser().getId();
        String key1 ="follows:" + userid;
        String key2 ="follows:" + id;
        //求两个用户的关注交际
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOS = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public Result follow(Long followUserid, Boolean isFollow) {
        Long userid = UserHolder.getUser().getId();
        String key ="follows:" + userid;
        //判断关注还是取关
        if (isFollow) {
            //关注 新增数据
            Follow follow = new Follow();
            follow.setFollowUserId(followUserid);
            follow.setUserId(userid);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                //把关注用户id放入redis的set集合中
                stringRedisTemplate.opsForSet().add(key,followUserid.toString());
            }
        }else {
            //取关
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userid)
                    .eq("follow_user_id", followUserid));
            //删除redis中的数据
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key,followUserid.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserid) {
        Long userid = UserHolder.getUser().getId();
        //查询是否关注
        long count = query().eq("user_id", userid).eq("follow_user_id", followUserid).count();
        return Result.ok(count > 0);
    }
}
