package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.PacienteRequestDTO;
import com.carlesso.pilatesapi.dto.PacienteResponseDTO;
import com.carlesso.pilatesapi.dto.PacienteUpdateDTO;
import com.carlesso.pilatesapi.entity.Endereco;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.Tomador;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import com.carlesso.pilatesapi.repository.TomadorRepository;
import com.carlesso.pilatesapi.util.VersaoGuard;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PacienteService {

    private final PacienteRepository repository;
    private final TomadorRepository tomadorRepository;
    private final ProfissionalRepository profissionalRepository;
    private final AulaService aulaService;

    public PacienteService(
            PacienteRepository repository,
            TomadorRepository tomadorRepository,
            ProfissionalRepository profissionalRepository,
            AulaService aulaService) {
        this.repository = repository;
        this.tomadorRepository = tomadorRepository;
        this.profissionalRepository = profissionalRepository;
        this.aulaService = aulaService;
    }

    @Transactional
    public PacienteResponseDTO cadastrar(PacienteRequestDTO dto) {
        if (dto.email() != null && repository.existsByEmail(dto.email())) {
            throw new ConflictException("E-mail já cadastrado");
        }
        if (dto.cpf() != null && repository.existsByCpf(dto.cpf())) {
            throw new ConflictException("CPF já cadastrado");
        }
        Paciente paciente = new Paciente();
        paciente.setNome(dto.nome());
        paciente.setEmail(dto.email());
        paciente.setCpf(dto.cpf());
        paciente.setTelefone(dto.telefone());
        paciente.setDataNascimento(dto.dataNascimento());
        paciente.setDataCadastro(LocalDate.now());
        paciente.setCpfNaNota(Boolean.TRUE.equals(dto.cpfNaNota()));
        if (dto.tomadorId() != null) {
            paciente.setTomador(tomadorAtivo(dto.tomadorId()));
        }
        if (dto.profissionalPadraoId() != null) {
            paciente.setProfissionalPadrao(profissionalAtivo(dto.profissionalPadraoId()));
        }
        if (dto.endereco() != null) {
            paciente.setEndereco(new Endereco(
                    dto.endereco().logradouro(),
                    dto.endereco().numero(),
                    dto.endereco().bairro(),
                    dto.endereco().cidade(),
                    dto.endereco().uf(),
                    dto.endereco().cep()));
        }
        return PacienteResponseDTO.from(repository.save(paciente));
    }

    @Transactional(readOnly = true)
    public Page<PacienteResponseDTO> listar(
            String nome,
            String email,
            String cpf,
            String telefone,
            Boolean ativo,
            Long profissionalId,
            Pageable pageable) {
        return repository
                .findAll(filtros(nome, email, cpf, telefone, ativo, profissionalId), pageable)
                .map(PacienteResponseDTO::from);
    }

    public PacienteResponseDTO buscarPorId(Long id) {
        return PacienteResponseDTO.from(encontrar(id));
    }

    @Transactional
    public PacienteResponseDTO atualizar(Long id, PacienteUpdateDTO dto) {
        Paciente paciente = encontrar(id);
        VersaoGuard.exigirVersaoAtual(dto.version(), paciente.getVersion(), Paciente.class, id);
        if (dto.nome() != null) paciente.setNome(dto.nome());
        if (dto.email() != null) paciente.setEmail(dto.email());
        if (dto.telefone() != null) paciente.setTelefone(dto.telefone());
        if (dto.dataNascimento() != null) paciente.setDataNascimento(dto.dataNascimento());
        atualizarCpf(paciente, dto.cpf());
        if (dto.cpfNaNota() != null) paciente.setCpfNaNota(dto.cpfNaNota());
        if (dto.profissionalPadraoId() != null) definirProfissionalPadrao(paciente, dto.profissionalPadraoId());
        if (dto.endereco() != null) {
            paciente.setEndereco(new Endereco(
                    dto.endereco().logradouro(),
                    dto.endereco().numero(),
                    dto.endereco().bairro(),
                    dto.endereco().cidade(),
                    dto.endereco().uf(),
                    dto.endereco().cep()));
        }
        return PacienteResponseDTO.from(repository.saveAndFlush(paciente));
    }

    @Transactional
    public void ativar(Long id) {
        Paciente paciente = encontrar(id);
        boolean estavaInativo = !paciente.isAtivo();
        paciente.setAtivo(true);
        paciente.setDataInativacao(null);
        // Inativo, o paciente ficou fora da marcação de feriado (#244); de volta, as aulas pendentes dele em feriado
        // são marcadas.
        if (estavaInativo) {
            aulaService.marcarFeriadosDoPaciente(paciente.getId());
        }
    }

    @Transactional
    public void inativar(Long id) {
        Paciente paciente = encontrar(id);
        // Inativar quem já está inativo não muda a data de saída: a evasão conta no mês em que ela aconteceu.
        if (paciente.isAtivo()) {
            paciente.setDataInativacao(LocalDate.now());
        }
        paciente.setAtivo(false);
    }

    /**
     * Completa ou troca o CPF. Ausente ou vazio mantém o atual: o cadastro importado do seufisio pode não ter CPF, e
     * sem esta rota ele nunca seria preenchido. Não há como apagar um CPF pelo PUT.
     */
    private void atualizarCpf(Paciente paciente, String cpf) {
        String novo = vazioComoNulo(cpf);
        if (novo == null || novo.equals(paciente.getCpf())) {
            return;
        }
        if (repository.existsByCpf(novo)) {
            throw new ConflictException("CPF já cadastrado");
        }
        paciente.setCpf(novo);
    }

    /**
     * Vincula ({@code tomadorId}) ou desvincula ({@code null}) o tomador da NFSE do paciente. Tomador inativo não
     * recebe vínculo novo; manter o vínculo que já existe com um tomador que foi inativado depois continua valendo.
     */
    @Transactional
    public PacienteResponseDTO vincularTomador(Long id, Long tomadorId) {
        Paciente paciente = encontrar(id);
        if (tomadorId == null) {
            paciente.setTomador(null);
        } else if (paciente.getTomador() == null
                || !tomadorId.equals(paciente.getTomador().getId())) {
            paciente.setTomador(tomadorAtivo(tomadorId));
        }
        return PacienteResponseDTO.from(repository.saveAndFlush(paciente));
    }

    /**
     * Define ({@code profissionalId}) ou remove ({@code null}) o fisioterapeuta padrão (#245). As aulas já geradas não
     * mudam: só as próximas gerações usam o novo padrão.
     */
    @Transactional
    public PacienteResponseDTO definirProfissionalPadrao(Long id, Long profissionalId) {
        Paciente paciente = encontrar(id);
        if (profissionalId == null) {
            paciente.setProfissionalPadrao(null);
        } else {
            definirProfissionalPadrao(paciente, profissionalId);
        }
        return PacienteResponseDTO.from(repository.saveAndFlush(paciente));
    }

    /** Manter o padrão que já existe vale mesmo com o profissional inativado depois; vínculo novo exige ativo. */
    private void definirProfissionalPadrao(Paciente paciente, Long profissionalId) {
        Profissional atual = paciente.getProfissionalPadrao();
        if (atual == null || !profissionalId.equals(atual.getId())) {
            paciente.setProfissionalPadrao(profissionalAtivo(profissionalId));
        }
    }

    private Profissional profissionalAtivo(Long profissionalId) {
        Profissional profissional = profissionalRepository
                .findById(profissionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Profissional não encontrado: " + profissionalId));
        if (!profissional.isAtivo()) {
            throw new BusinessException("Profissional inativo não pode ser o padrão do paciente: " + profissionalId);
        }
        return profissional;
    }

    private Tomador tomadorAtivo(Long tomadorId) {
        Tomador tomador = tomadorRepository
                .findById(tomadorId)
                .orElseThrow(() -> new ResourceNotFoundException("Tomador não encontrado: " + tomadorId));
        if (!tomador.isAtivo()) {
            throw new BusinessException("Tomador inativo não pode ser vinculado: " + tomadorId);
        }
        return tomador;
    }

    private static String vazioComoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private Paciente encontrar(Long id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado: " + id));
    }

    private Specification<Paciente> filtros(
            String nome, String email, String cpf, String telefone, Boolean ativo, Long profissionalId) {
        Specification<Paciente> spec = porStatus(ativo);
        if (profissionalId != null) {
            spec = spec.and(
                    (root, query, cb) -> cb.equal(root.get("profissionalPadrao").get("id"), profissionalId));
        }
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
            return Specification.unrestricted();
        }

        String filtro = "%" + valor.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) -> criteriaBuilder.like(criteriaBuilder.lower(root.get(campo)), filtro);
    }
}
