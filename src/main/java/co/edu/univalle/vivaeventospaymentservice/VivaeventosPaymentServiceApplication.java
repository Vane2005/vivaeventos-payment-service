package co.edu.univalle.vivaeventospaymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "co.edu.univalle.vivaeventospaymentservice",
        "co.edu.univalle.payment"
})
@EnableJpaRepositories(basePackages = "co.edu.univalle.payment.infrastructure.persistence")
@EntityScan(basePackages = "co.edu.univalle.payment.infrastructure.persistence")
public class VivaeventosPaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VivaeventosPaymentServiceApplication.class, args);
    }
}
