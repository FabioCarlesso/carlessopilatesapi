package com.carlesso.pilatesapi.dto;

import java.util.List;

/**
 * Campos estruturados do exame físico (#248) que a avaliação fisioterapêutica e a reavaliação recebem com os mesmos
 * nomes. Os quatro DTOs de escrita das duas implementam esta interface — os componentes do record satisfazem os
 * métodos —, e a validação e a gravação ficam num ponto só.
 */
public interface CamposExameFisicoOpcoesDTO {

    List<String> achadosPosturais();

    String achadosPosturaisComplemento();

    String padraoRespiratorioOpcao();

    String padraoRespiratorioComplemento();

    String equilibrioOpcao();

    String equilibrioComplemento();

    String coordenacaoMotoraOpcao();

    String coordenacaoMotoraComplemento();

    List<TesteFuncionalRealizadoDTO> testesFuncionais();

    String testesFuncionaisComplemento();
}
