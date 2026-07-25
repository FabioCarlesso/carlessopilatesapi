package com.carlesso.pilatesapi.util;

import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.exception.BusinessException;

/**
 * Regra compartilhada de escrita no prontuário: paciente inativo é ex-aluno, e
 * o histórico dele continua legível e editável, mas não recebe registro novo.
 *
 * <p>Existe para que os oito pontos de criação (sessão, anamnese, avaliação,
 * plano de tratamento, reavaliação, NFSE, evolução e análise postural)
 * respondam a mesma coisa: {@code 404} quando o paciente não existe e
 * {@code 422} quando existe mas está inativo. Ver issue #152.
 */
public final class PacienteGuard {

    private static final String MENSAGEM_INATIVO = "Paciente inativo não aceita novos registros clínicos: ";

    private PacienteGuard() {}

    /**
     * Recusa a criação de registro clínico quando o paciente está inativo.
     *
     * @throws BusinessException mapeada para {@code 422} pelo GlobalExceptionHandler
     */
    public static void exigirAtivo(Paciente paciente) {
        if (!paciente.isAtivo()) {
            throw new BusinessException(MENSAGEM_INATIVO + paciente.getId());
        }
    }
}
