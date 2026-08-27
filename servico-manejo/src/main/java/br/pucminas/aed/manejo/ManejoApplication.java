package br.pucminas.aed.manejo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ManejoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ManejoApplication.class, args);
    }
}
