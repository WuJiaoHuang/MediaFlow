package com.mediaflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.mediaflow.mapper")
public class MediaFlowApplication {
  public static void main(String[] args) {
    SpringApplication.run(MediaFlowApplication.class, args);
  }
}
