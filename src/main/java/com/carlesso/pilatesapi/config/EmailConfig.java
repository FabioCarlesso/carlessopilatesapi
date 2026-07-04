package com.carlesso.pilatesapi.config;

import com.carlesso.pilatesapi.email.EmailSender;
import com.carlesso.pilatesapi.email.SmtpEmailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class EmailConfig {

    @Bean
    @ConditionalOnProperty(name = "app.email.provider", havingValue = "smtp", matchIfMissing = true)
    public EmailSender smtpEmailSender(JavaMailSender mailSender,
                                        @Value("${app.email.from}") String from) {
        return new SmtpEmailSender(mailSender, from);
    }
}
