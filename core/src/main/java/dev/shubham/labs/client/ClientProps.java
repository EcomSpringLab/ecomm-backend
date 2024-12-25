package dev.shubham.labs.client;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
public class ClientProps {

    @NotBlank(message = "Base URL field cannot be Blank for ClientConfig")
    private String baseUrl;
    private Duration connectTimeout = Duration.ofSeconds(10);

}
