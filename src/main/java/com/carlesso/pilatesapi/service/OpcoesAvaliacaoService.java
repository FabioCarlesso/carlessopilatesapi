package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.OpcoesAvaliacaoResponseDTO;
import org.springframework.stereotype.Service;

/**
 * Catálogo das opções pré-definidas (#248). As listas vivem nos enums de {@code entity.enums} e não mudam em tempo de
 * execução, então o catálogo é montado uma vez — sem banco nem cache.
 */
@Service
public class OpcoesAvaliacaoService {

    private static final OpcoesAvaliacaoResponseDTO CATALOGO = OpcoesAvaliacaoResponseDTO.deTodosOsCampos();

    public OpcoesAvaliacaoResponseDTO catalogo() {
        return CATALOGO;
    }
}
