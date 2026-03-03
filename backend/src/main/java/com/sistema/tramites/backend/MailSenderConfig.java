package com.sistema.tramites.backend;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import java.util.Properties;

@Configuration
@EnableConfigurationProperties
public class MailSenderConfig {

    private static final String EMAIL_SISTEMAS_TEMPORAL = "sistemas@cabuyaro-meta.gov.co";

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String host;

    @Value("${spring.mail.port:587}")
    private int port;

    @Value("${spring.mail.username:}")
    private String username;

    @Value("${spring.mail.password:}")
    private String password;

    @Value("${spring.mail.properties.mail.smtp.connectiontimeout:10000}")
    private String connectionTimeout;

    @Value("${spring.mail.properties.mail.smtp.timeout:10000}")
    private String timeout;

    @Value("${spring.mail.properties.mail.smtp.writetimeout:10000}")
    private String writeTimeout;

    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(resolverUsername());
        mailSender.setPassword(normalizarPassword(password));
        
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        props.put("mail.smtp.connectiontimeout", connectionTimeout);
        props.put("mail.smtp.timeout", timeout);
        props.put("mail.smtp.writetimeout", writeTimeout);
        
        return mailSender;
    }

    private String normalizarPassword(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replace(" ", "").trim();
    }

    private String resolverUsername() {
        String valor = username == null ? "" : username.trim();
        if (!valor.isBlank()) {
            return valor;
        }
        return EMAIL_SISTEMAS_TEMPORAL;
    }
}
