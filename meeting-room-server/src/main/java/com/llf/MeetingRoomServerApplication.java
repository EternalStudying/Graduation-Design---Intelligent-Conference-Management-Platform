package com.llf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MeetingRoomServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(MeetingRoomServerApplication.class, args);
    }
}
