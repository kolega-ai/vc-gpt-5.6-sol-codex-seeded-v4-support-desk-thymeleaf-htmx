package com.acme.support.channel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChannelApplication {
    public static void main(String[] args) { SpringApplication.run(ChannelApplication.class, args); }
}
