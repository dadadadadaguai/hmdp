package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author dadaguai
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    private final IFollowService followService;

    public FollowController(IFollowService followService) {
        this.followService = followService;
    }

    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long id, @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(id, isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long id) {
        return followService.isFollow(id);
    }
}
