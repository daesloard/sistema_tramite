package com.sistema.tramites.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
<<<<<<< HEAD
import org.springframework.http.client.SimpleClientHttpRequestFactory;
=======
import org.springframework.web.util.DefaultUriBuilderFactory;
>>>>>>> 1eaa8d0a9624dcb57f889d91356a54bd4f96ab2d

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
<<<<<<< HEAD
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(30000);
        return new RestTemplate(factory);
=======
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory());
        return restTemplate;
>>>>>>> 1eaa8d0a9624dcb57f889d91356a54bd4f96ab2d
    }
}
