package com.hmdp.utils;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("オンラインショッピングシステム - 課題提出用")
                        .version("1.0")
                        .description("職業訓練校の課題：Spring Boot + MyBatis-Plus + Knife4j を用いたオンラインショッピング後端システム")
                        .contact(new Contact()
                                .name("潘 文博")   // ← ここに自分の氏名や学籍番号を入れるとよい
                                .email("xxx@example.com") // メールは省略可
                        )
                );
    }
}
