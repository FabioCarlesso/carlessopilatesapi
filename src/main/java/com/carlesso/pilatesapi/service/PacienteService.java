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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public Page<PacienteResponseDTO> listar(Pageable pageable) {
        return repository.findAllByAtivoTrue(pageable).map(PacienteResponseDTO::from);
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
    public void inativar(Long id) {
        Paciente paciente = encontrar(id);
        paciente.setAtivo(false);
    }

    private Paciente encontrar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Paciente não encontrado: " + id));
    }
}
