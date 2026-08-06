package it.sd.lucrezia.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LucreziaAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                LucreziaAiApplication.class,
                args
        );
    }
}