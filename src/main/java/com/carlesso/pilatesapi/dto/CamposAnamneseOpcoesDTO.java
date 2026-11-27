package com.carlesso.pilatesapi.dto;

import java.util.List;

/**
 * Campos estruturados da anamnese (#248), comuns ao DTO de criação e ao de edição, para que a validação e a gravação
 * fiquem num ponto só — ver {@link CamposExameFisicoOpcoesDTO}.
 */
public interface CamposAnamneseOpcoesDTO {

    List<String> regioesDor();

    String regioesDorComplemento();

    List<String> condicoesSaude();

    String condicoesSaudeComplemento();

    String nivelAtividadeFisicaOpcao();

    String nivelAtividadeFisicaComplemento();

    List<String> objetivosOpcoes();

    String objetivosComplemento();
}
