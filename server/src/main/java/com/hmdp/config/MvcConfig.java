package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import com.hmdp.utils.SystemConstants;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 把图片上传目录直接暴露成 /imgs/**。
     * 原方案依赖 nginx 提供 /imgs，本地只跑 vite + 8081 时上传的图片会 404，
     * 这里补一份映射，nginx 仍然可用，互不影响。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = SystemConstants.IMAGE_UPLOAD_DIR.replace("\\", "/");
        registry.addResourceHandler("/imgs/**")
                .addResourceLocations("file:" + location);
    }

    @Override
    //token拦截器
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor()).excludePathPatterns(
                "/user/code",
                "/user/login",
                "/blog/hot",
                "/shop-type/**",
                "/voucher/**",
                "/upload/**",
                "/shop/**",
                "/imgs/**",
                "/agent/**",
                "/sse",
                "/mcp/**",
                // 资源不存在时 Spring 会转发到 /error，若不排除，
                // 未登录用户访问任何 404 都会被拦成 401，掩盖真实状态
                "/error"
        ).order(1);
        //token刷新拦截器
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);
    }
}
