package br.fatec.chamaja;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChamaJaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChamaJaApplication.class, args);
    }
}
