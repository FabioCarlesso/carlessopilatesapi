package com.carlesso.pilatesapi.config;

import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_resourceNotFound_retornaMensagemDoErro() {
        Map<String, String> response = handler.handleNotFound(
                new ResourceNotFoundException("Paciente não encontrado: 1"));

        assertThat(response).containsEntry("erro", "Paciente não encontrado: 1");
    }

    @Test
    void handleNotFound_entityNotFound_retornaMensagemDoErro() {
        Map<String, String> response = handler.handleNotFound(
                new EntityNotFoundException("Profissional não encontrado: 1"));

        assertThat(response).containsEntry("erro", "Profissional não encontrado: 1");
    }

    @Test
    void handleConflict_retornaMensagemDoErro() {
        Map<String, String> response = handler.handleConflict(
                new ConflictException("E-mail já cadastrado"));

        assertThat(response).containsEntry("erro", "E-mail já cadastrado");
    }

    @Test
    void handleBusiness_retornaMensagemDoErro() {
        Map<String, String> response = handler.handleBusiness(
                new BusinessException("Paciente inativo não pode receber novas cobranças"));

        assertThat(response).containsEntry("erro", "Paciente inativo não pode receber novas cobranças");
    }

    @Test
    void handleBadRequest_retornaMensagemDoErro() {
        Map<String, String> response = handler.handleBadRequest(
                new IllegalArgumentException("Período inicial é obrigatório"));

        assertThat(response).containsEntry("erro", "Período inicial é obrigatório");
    }

    @Test
    void handleDataIntegrity_retornaMensagemPadrao() {
        Map<String, String> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("violação"));

        assertThat(response).containsEntry("erro",
                "Violação de integridade: registro duplicado ou constraint violada");
    }
}
