package com.carlesso.pilatesapi.email;

import com.carlesso.pilatesapi.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Component
public class ThymeleafEmailTemplateService implements EmailTemplateService {

    private final SpringTemplateEngine templateEngine;
    private final int expiracaoMinutos;

    public ThymeleafEmailTemplateService(
            SpringTemplateEngine templateEngine,
            @Value("${app.email.reset-password-token-ttl-minutos:30}") int expiracaoMinutos) {
        this.templateEngine = templateEngine;
        this.expiracaoMinutos = expiracaoMinutos;
    }

    @Override
    public EmailMessage criarEmailRedefinicaoSenha(User user, String linkRedefinicao) {
        Context context = new Context();
        context.setVariable("nome", user.getName());
        context.setVariable("linkRedefinicao", linkRedefinicao);
        context.setVariable("expiracaoMinutos", expiracaoMinutos);

        String htmlBody = templateEngine.process("password-reset", context);
        String textBody = "Olá " + user.getName() + ", acesse o link a seguir para redefinir sua senha: "
                + linkRedefinicao + ". O link expira em " + expiracaoMinutos + " minutos.";

        return new EmailMessage(user.getEmail(), "Redefinição de senha - Carlesso Pilates", htmlBody, textBody);
    }
}
