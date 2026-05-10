package com.yegkim.task_reloader_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TaskReloaderApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(TaskReloaderApiApplication.class, args);
	}

}
