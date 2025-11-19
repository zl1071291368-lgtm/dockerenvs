package org.dockerenvs;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 虚拟实验环境框架 - 主启动类
 */
@SpringBootApplication
@MapperScan("org.dockerenvs.dao.mapper")
public class DockerEnvsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DockerEnvsApplication.class, args);
    }

}
