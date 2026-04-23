package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.ProfissionalRequestDTO;
import com.carlesso.pilatesapi.dto.ProfissionalResponseDTO;
import com.carlesso.pilatesapi.dto.ProfissionalUpdateDTO;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfissionalService {

    private final ProfissionalRepository repository;

    public ProfissionalService(ProfissionalRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ProfissionalResponseDTO cadastrar(ProfissionalRequestDTO dto) {
        if (repository.existsByEmail(dto.email())) {
            throw new IllegalStateException("E-mail já cadastrado: " + dto.email());
        }
        if (repository.existsByCpf(dto.cpf())) {
            throw new IllegalStateException("CPF já cadastrado: " + dto.cpf());
        }
        Profissional profissional = new Profissional();
        profissional.setNome(dto.nome());
        profissional.setEmail(dto.email());
        profissional.setCpf(dto.cpf());
        profissional.setTelefone(dto.telefone());
        profissional.setTipoContrato(dto.tipoContrato());
        profissional.setPercentualPagamentoAula(dto.percentualPagamentoAula());
        profissional.setDataInicio(dto.dataInicio());
        return ProfissionalResponseDTO.from(repository.save(profissional));
    }

    @Transactional(readOnly = true)
    public Page<ProfissionalResponseDTO> listar(Pageable pageable) {
        return repository.findAllByAtivoTrue(pageable).map(ProfissionalResponseDTO::from);
    }

    @Transactional(readOnly = true)
    public ProfissionalResponseDTO buscarPorId(Long id) {
        return ProfissionalResponseDTO.from(encontrar(id));
    }

    @Transactional
    public ProfissionalResponseDTO atualizar(Long id, ProfissionalUpdateDTO dto) {
        Profissional profissional = encontrar(id);
        if (dto.nome() != null) profissional.setNome(dto.nome());
        if (dto.email() != null) profissional.setEmail(dto.email());
        if (dto.telefone() != null) profissional.setTelefone(dto.telefone());
        if (dto.tipoContrato() != null) profissional.setTipoContrato(dto.tipoContrato());
        if (dto.percentualPagamentoAula() != null) profissional.setPercentualPagamentoAula(dto.percentualPagamentoAula());
        if (dto.dataInicio() != null) profissional.setDataInicio(dto.dataInicio());
        return ProfissionalResponseDTO.from(profissional);
    }

    @Transactional
    public void ativar(Long id) {
        encontrar(id).setAtivo(true);
    }

    @Transactional
    public void inativar(Long id) {
        encontrar(id).setAtivo(false);
    }

    private Profissional encontrar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Profissional não encontrado: " + id));
    }
}
