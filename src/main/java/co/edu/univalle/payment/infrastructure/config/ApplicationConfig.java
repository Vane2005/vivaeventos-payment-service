package co.edu.univalle.payment.infrastructure.config;

import co.edu.univalle.payment.infrastructure.gateway.WompiProperties;
import co.edu.univalle.payment.infrastructure.messaging.MessagingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({WompiProperties.class, MessagingProperties.class})
public class ApplicationConfig {}
