package com.library.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 接口文档配置：访问 /doc.html */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME = "BearerAuth";

    @Bean
    public OpenAPI libraryOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("图书管理系统 API")
                        .version("2.0.0")
                        .description("管理员后台 + 读者门户双端接口。先调用 /auth/login 获取 token，点右上角「Authorize」填入即可调试。")
                        .contact(new Contact().name("KuAgLan").url("https://github.com/KuAgLan/LibrarySystem"))
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes(SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录返回的 token，无需手动加 Bearer 前缀")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME));
    }
}