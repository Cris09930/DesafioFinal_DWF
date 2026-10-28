package sv.edu.dwf404.ventas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VentasApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(VentasApiApplication.class, args);
    }
}
