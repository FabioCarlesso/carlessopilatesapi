package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.PacienteRequestDTO;
import com.carlesso.pilatesapi.dto.PacienteResponseDTO;
import com.carlesso.pilatesapi.dto.PacienteUpdateDTO;
import com.carlesso.pilatesapi.entity.Endereco;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class PacienteService {

    private final PacienteRepository repository;

    public PacienteService(PacienteRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PacienteResponseDTO cadastrar(PacienteRequestDTO dto) {
        Paciente paciente = new Paciente();
        paciente.setNome(dto.nome());
        paciente.setEmail(dto.email());
        paciente.setCpf(dto.cpf());
        paciente.setTelefone(dto.telefone());
        paciente.setDataNascimento(dto.dataNascimento());
        if (dto.endereco() != null) {
            paciente.setEndereco(new Endereco(
                    dto.endereco().logradouro(),
                    dto.endereco().numero(),
                    dto.endereco().bairro(),
                    dto.endereco().cidade(),
                    dto.endereco().uf(),
                    dto.endereco().cep()
            ));
        }
        return PacienteResponseDTO.from(repository.save(paciente));
    }

    public Page<PacienteResponseDTO> listar(
            String nome,
            String email,
            String cpf,
            String telefone,
            Boolean ativo,
            Pageable pageable) {
        return repository.findAll(filtros(nome, email, cpf, telefone, ativo), pageable)
                .map(PacienteResponseDTO::from);
    }

    public PacienteResponseDTO buscarPorId(Long id) {
        return PacienteResponseDTO.from(encontrar(id));
    }

    @Transactional
    public PacienteResponseDTO atualizar(Long id, PacienteUpdateDTO dto) {
        Paciente paciente = encontrar(id);
        if (dto.nome() != null) paciente.setNome(dto.nome());
        if (dto.email() != null) paciente.setEmail(dto.email());
        if (dto.telefone() != null) paciente.setTelefone(dto.telefone());
        if (dto.dataNascimento() != null) paciente.setDataNascimento(dto.dataNascimento());
        if (dto.endereco() != null) {
            paciente.setEndereco(new Endereco(
                    dto.endereco().logradouro(),
                    dto.endereco().numero(),
                    dto.endereco().bairro(),
                    dto.endereco().cidade(),
                    dto.endereco().uf(),
                    dto.endereco().cep()
            ));
        }
        return PacienteResponseDTO.from(paciente);
    }

    @Transactional
    public void ativar(Long id) {
        Paciente paciente = encontrar(id);
        paciente.setAtivo(true);
    }

    @Transactional
    public void inativar(Long id) {
        Paciente paciente = encontrar(id);
        paciente.setAtivo(false);
    }

    private Paciente encontrar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Paciente não encontrado: " + id));
    }

    private Specification<Paciente> filtros(String nome, String email, String cpf, String telefone, Boolean ativo) {
        Specification<Paciente> spec = porStatus(ativo);
        spec = spec.and(contemIgnorandoCase("nome", nome));
        spec = spec.and(contemIgnorandoCase("email", email));
        spec = spec.and(contemIgnorandoCase("cpf", cpf));
        spec = spec.and(contemIgnorandoCase("telefone", telefone));
        return spec;
    }

    private Specification<Paciente> porStatus(Boolean ativo) {
        boolean status = ativo == null || ativo;
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("ativo"), status);
    }

    private Specification<Paciente> contemIgnorandoCase(String campo, String valor) {
        if (valor == null || valor.isBlank()) {
            return Specification.where(null);
        }

        String filtro = "%" + valor.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(root.get(campo)), filtro);
    }
}
