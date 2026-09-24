package tn.bricocomptoir.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;

@SpringBootApplication(scanBasePackages = "tn.bricocomptoir")
@AutoConfigurationPackage(basePackages = "tn.bricocomptoir")
public class BricoComptoirApplication {

	public static void main(String[] args) {
		SpringApplication.run(BricoComptoirApplication.class, args);
	}

}
