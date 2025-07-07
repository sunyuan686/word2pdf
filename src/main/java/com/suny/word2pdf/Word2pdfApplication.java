package com.suny.word2pdf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Word2PDF应用主类
 * 
 * @author suny
 */
@SpringBootApplication
@EnableConfigurationProperties
public class Word2pdfApplication {

    public static void main(String[] args) {
        SpringApplication.run(Word2pdfApplication.class, args);
    }

}
