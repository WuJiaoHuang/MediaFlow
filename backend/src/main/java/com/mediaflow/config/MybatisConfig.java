package com.mediaflow.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.*;

@Configuration
public class MybatisConfig {
  @Bean
  MybatisPlusInterceptor pagination() {
    var i = new MybatisPlusInterceptor();
    var p = new PaginationInnerInterceptor(DbType.MYSQL);
    p.setMaxLimit(100L);
    i.addInnerInterceptor(p);
    return i;
  }
}
