package com.library.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/** 静态资源 + Vue History 回退（/api/** 不参与） */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/", "classpath:/META-INF/resources/", "classpath:/webjars/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // 带扩展名的静态资源（doc.html、xxx.js 等）找不到就交给下一个 location，
                        // 只有无扩展名的前端路由才回退到 index.html
                        if (resourcePath.startsWith("api/") || resourcePath.contains(".")) {
                            return null;
                        }
                        Resource index = new ClassPathResource("static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
