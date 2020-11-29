package com.test.upload.slice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude= {DataSourceAutoConfiguration.class})
public class SliceUploadServer {
    public static void main(String[] args) {
        SpringApplication.run(SliceUploadServer.class, args);
    }
}