package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.TomadorPacienteDTO;
import com.carlesso.pilatesapi.dto.TomadorRequestDTO;
import com.carlesso.pilatesapi.dto.TomadorResponseDTO;
import com.carlesso.pilatesapi.dto.TomadorUpdateDTO;
import com.carlesso.pilatesapi.entity.Tomador;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.TomadorRepository;
import com.carlesso.pilatesapi.util.VersaoGuard;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro de tomadores da NFSE (#229): quem recebe a nota quando não é o próprio paciente. */
@Service
public class TomadorService {

    private final TomadorRepository repository;
    private final PacienteRepository pacienteRepository;

    public TomadorService(TomadorRepository repository, PacienteRepository pacienteRepository) {
        this.repository = repository;
        this.pacienteRepository = pacienteRepository;
    }

    /**
     * CPF/CNPJ só com dígitos, que precisam ser 11 (CPF) ou 14 (CNPJ). Os dígitos verificadores não são conferidos
     * aqui, como no CPF do paciente: a tela confere, e a API não recusa documento que a prefeitura aceitaria.
     */
    static String normalizarCpfCnpj(String valor) {
        String digitos = valor == null ? "" : valor.replaceAll("\\D", "");
        if (digitos.length() != 11 && digitos.length() != 14) {
            throw new IllegalArgumentException("cpfCnpj deve ter 11 (CPF) ou 14 (CNPJ) dígitos");
        }
        return digitos;
    }

    @Transactional
    public TomadorResponseDTO cadastrar(TomadorRequestDTO dto) {
        String cpfCnpj = normalizarCpfCnpj(dto.cpfCnpj());
        if (repository.existsByCpfCnpj(cpfCnpj)) {
            throw new ConflictException("CPF/CNPJ já cadastrado para outro tomador");
        }
        Tomador tomador = new Tomador();
        tomador.setNome(dto.nome().trim());
        tomador.setCpfCnpj(cpfCnpj);
        tomador.setEmail(vazioComoNulo(dto.email()));
        tomador.setTelefone(vazioComoNulo(dto.telefone()));
        tomador.setObservacoes(vazioComoNulo(dto.observacoes()));
        return TomadorResponseDTO.from(repository.save(tomador), 0);
    }

    @Transactional(readOnly = true)
    public Page<TomadorResponseDTO> listar(String nome, String cpfCnpj, Boolean ativo, Pageable pageable) {
        Page<Tomador> pagina = repository.findAll(filtros(nome, cpfCnpj, ativo), pageable);
        Map<Long, Long> quantidades =
                contarPacientes(pagina.getContent().stream().map(Tomador::getId).toList());
        return pagina.map(t -> TomadorResponseDTO.from(t, quantidades.getOrDefault(t.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public TomadorResponseDTO buscarPorId(Long id) {
        Tomador tomador = encontrar(id);
        return TomadorResponseDTO.from(tomador, contarPacientes(List.of(id)).getOrDefault(id, 0L));
    }

    @Transactional(readOnly = true)
    public List<TomadorPacienteDTO> listarPacientes(Long id) {
        encontrar(id);
        return pacienteRepository.findByTomadorIdOrderByNomeAsc(id).stream()
                .map(p -> new TomadorPacienteDTO(p.getId(), p.getNome(), p.isAtivo(), p.isCpfNaNota()))
                .toList();
    }

    @Transactional
    public TomadorResponseDTO atualizar(Long id, TomadorUpdateDTO dto) {
        Tomador tomador = encontrar(id);
        VersaoGuard.exigirVersaoAtual(dto.version(), tomador.getVersion(), Tomador.class, id);
        if (dto.nome() != null && !dto.nome().isBlank())
            tomador.setNome(dto.nome().trim());
        if (dto.cpfCnpj() != null) {
            String cpfCnpj = normalizarCpfCnpj(dto.cpfCnpj());
            if (!cpfCnpj.equals(tomador.getCpfCnpj()) && repository.existsByCpfCnpjAndIdNot(cpfCnpj, id)) {
                throw new ConflictException("CPF/CNPJ já cadastrado para outro tomador");
            }
            tomador.setCpfCnpj(cpfCnpj);
        }
        if (dto.email() != null) tomador.setEmail(vazioComoNulo(dto.email()));
        if (dto.telefone() != null) tomador.setTelefone(vazioComoNulo(dto.telefone()));
        if (dto.observacoes() != null) tomador.setObservacoes(vazioComoNulo(dto.observacoes()));
        Tomador salvo = repository.saveAndFlush(tomador);
        return TomadorResponseDTO.from(salvo, contarPacientes(List.of(id)).getOrDefault(id, 0L));
    }

    /** Inativar não desvincula os pacientes: só impede novos vínculos. */
    @Transactional
    public void inativar(Long id) {
        encontrar(id).setAtivo(false);
    }

    @Transactional
    public void ativar(Long id) {
        encontrar(id).setAtivo(true);
    }

    Tomador encontrar(Long id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tomador não encontrado: " + id));
    }

    private Map<Long, Long> contarPacientes(List<Long> ids) {
        Map<Long, Long> quantidades = new HashMap<>();
        if (ids.isEmpty()) {
            return quantidades;
        }
        for (Object[] linha : pacienteRepository.contarPorTomador(ids)) {
            quantidades.put((Long) linha[0], (Long) linha[1]);
        }
        return quantidades;
    }

    private Specification<Tomador> filtros(String nome, String cpfCnpj, Boolean ativo) {
        boolean status = ativo == null || ativo;
        Specification<Tomador> spec = (root, query, cb) -> cb.equal(root.get("ativo"), status);
        if (nome != null && !nome.isBlank()) {
            String filtro = "%" + nome.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("nome")), filtro));
        }
        String digitos = cpfCnpj == null ? "" : cpfCnpj.replaceAll("\\D", "");
        if (!digitos.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.like(root.get("cpfCnpj"), "%" + digitos + "%"));
        }
        return spec;
    }

    private static String vazioComoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
