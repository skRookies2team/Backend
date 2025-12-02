package com.story.game;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class StoryGameApplication {

    public static void main(String[] args) {
        SpringApplication.run(StoryGameApplication.class, args);
    }
}
