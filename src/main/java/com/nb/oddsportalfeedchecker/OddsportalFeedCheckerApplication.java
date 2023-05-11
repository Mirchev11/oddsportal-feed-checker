package com.nb.oddsportalfeedchecker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OddsportalFeedCheckerApplication {

    public static void main(String[] args) {
        SpringApplication.run(OddsportalFeedCheckerApplication.class, args);
    }

}
