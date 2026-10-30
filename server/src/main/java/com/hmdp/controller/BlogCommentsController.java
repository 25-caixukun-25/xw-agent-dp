package com.hmdp.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 笔记评论前端控制器。
 * 原版这里是空实现（表、Service、Mapper 都有，但没有接口），补上查询与发表两条。
 * </p>
 */
@RestController
@RequestMapping("/blog-comments")
@RequiredArgsConstructor
public class BlogCommentsController {

    private static final int PAGE_SIZE = 10;
    private static final int MAX_CONTENT_LENGTH = 255;

    private final IBlogCommentsService blogCommentsService;
    private final IBlogService blogService;
    private final IUserService userService;

    /**
     * 按笔记分页查询评论，附带评论者昵称与头像
     */
    @GetMapping("/of/{blogId}")
    public Result queryComments(@PathVariable("blogId") Long blogId,
                                @RequestParam(value = "current", defaultValue = "1") Integer current) {
        Page<BlogComments> page = blogCommentsService.query()
                .eq("blog_id", blogId)
                .orderByDesc("create_time")
                .page(new Page<>(current, PAGE_SIZE));

        List<BlogComments> records = page.getRecords();
        if (records.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        Set<Long> userIds = records.stream()
                .map(BlogComments::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userService.listByIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<Map<String, Object>> list = new ArrayList<>(records.size());
        for (BlogComments comment : records) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", comment.getId());
            item.put("blogId", comment.getBlogId());
            item.put("userId", comment.getUserId());
            item.put("content", comment.getContent());
            item.put("liked", comment.getLiked());
            item.put("createTime", comment.getCreateTime());
            User author = userMap.get(comment.getUserId());
            item.put("name", author == null ? "匿名用户" : author.getNickName());
            item.put("icon", author == null ? null : author.getIcon());
            list.add(item);
        }
        return Result.ok(list);
    }

    /**
     * 发表评论，需要登录
     */
    @PostMapping
    public Result saveComment(@RequestBody BlogComments body) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录后再评论");
        }
        if (body.getBlogId() == null) {
            return Result.fail("缺少笔记 id");
        }
        if (StrUtil.isBlank(body.getContent())) {
            return Result.fail("评论内容不能为空");
        }
        String content = body.getContent().trim();
        if (content.length() > MAX_CONTENT_LENGTH) {
            return Result.fail("评论不能超过 " + MAX_CONTENT_LENGTH + " 字");
        }

        BlogComments comment = new BlogComments();
        comment.setBlogId(body.getBlogId());
        comment.setUserId(user.getId());
        comment.setContent(content);
        comment.setParentId(body.getParentId() == null ? 0L : body.getParentId());
        comment.setAnswerId(0L);
        comment.setLiked(0);
        comment.setStatus(true);
        comment.setCreateTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());
        blogCommentsService.save(comment);
        // 同步笔记的评论数，否则列表页的数字永远不变
        blogService.update().setSql("comments = comments + 1").eq("id", body.getBlogId()).update();
        return Result.ok(comment.getId());
    }
}
