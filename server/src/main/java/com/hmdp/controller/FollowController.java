package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
@RequiredArgsConstructor
public class FollowController {
    private final IFollowService followService;

    @GetMapping("/common/{id}")
    public Result followCommon(@PathVariable("id")Long id){
        return followService.followCommon(id);

    }

    @PutMapping("{id}/{isFollow}")
    public Result follow(@PathVariable("id")Long followUserid, @PathVariable("isFollow") Boolean isFollow){
        return followService.follow(followUserid,isFollow);
    }
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id")Long followUserid){
        return followService.isFollow(followUserid);
    }
}
