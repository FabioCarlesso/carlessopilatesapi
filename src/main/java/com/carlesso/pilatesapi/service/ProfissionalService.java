package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.ProfissionalRequestDTO;
import com.carlesso.pilatesapi.dto.ProfissionalResponseDTO;
import com.carlesso.pilatesapi.dto.ProfissionalUpdateDTO;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

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
    public Page<ProfissionalResponseDTO> listar(
            String nome,
            String email,
            TipoContrato tipoContrato,
            BigDecimal percentualPagamentoAula,
            Boolean ativo,
            Pageable pageable) {
        return repository.findAll(filtros(nome, email, tipoContrato, percentualPagamentoAula, ativo), pageable)
                .map(ProfissionalResponseDTO::from);
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

    private Specification<Profissional> filtros(
            String nome,
            String email,
            TipoContrato tipoContrato,
            BigDecimal percentualPagamentoAula,
            Boolean ativo) {
        Specification<Profissional> spec = porStatus(ativo);
        spec = spec.and(contemIgnorandoCase("nome", nome));
        spec = spec.and(contemIgnorandoCase("email", email));
        spec = spec.and(igual("tipoContrato", tipoContrato));
        spec = spec.and(igual("percentualPagamentoAula", percentualPagamentoAula));
        return spec;
    }

    private Specification<Profissional> porStatus(Boolean ativo) {
        boolean status = ativo == null || ativo;
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("ativo"), status);
    }

    private Specification<Profissional> contemIgnorandoCase(String campo, String valor) {
        if (valor == null || valor.isBlank()) {
            return Specification.where(null);
        }

        String filtro = "%" + valor.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(root.get(campo)), filtro);
    }

    private <T> Specification<Profissional> igual(String campo, T valor) {
        if (valor == null) {
            return Specification.where(null);
        }

        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(campo), valor);
    }
}
