package com.carlesso.pilatesapi.email;

import com.carlesso.pilatesapi.entity.User;

public interface EmailTemplateService {

    EmailMessage criarEmailRedefinicaoSenha(User user, String linkRedefinicao);
}
