package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.ProfissionalPagamentoAulaDTO;
import com.carlesso.pilatesapi.dto.ProfissionalPagamentoRelatorioDTO;
import com.carlesso.pilatesapi.dto.ProfissionalRequestDTO;
import com.carlesso.pilatesapi.dto.ProfissionalResponseDTO;
import com.carlesso.pilatesapi.dto.ProfissionalUpdateDTO;
import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import com.carlesso.pilatesapi.repository.AulaRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class ProfissionalService {

    private final ProfissionalRepository repository;
    private final AulaRepository aulaRepository;

    public ProfissionalService(ProfissionalRepository repository, AulaRepository aulaRepository) {
        this.repository = repository;
        this.aulaRepository = aulaRepository;
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

    @Transactional(readOnly = true)
    public ProfissionalPagamentoRelatorioDTO gerarRelatorioPagamento(Long id, LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null) {
            throw new IllegalArgumentException("Período inicial e final são obrigatórios");
        }
        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException("Período inicial não pode ser maior que o período final");
        }

        Profissional profissional = encontrar(id);
        List<ProfissionalPagamentoAulaDTO> aulas = aulaRepository
                .findByProfissionalIdAndRealizadaTrueAndDataBetweenOrderByData(id, inicio, fim)
                .stream()
                .map(aula -> mapearAulaPagamento(aula, profissional.getPercentualPagamentoAula()))
                .toList();

        BigDecimal totalPagamento = aulas.stream()
                .map(ProfissionalPagamentoAulaDTO::valorProfissional)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ProfissionalPagamentoRelatorioDTO(
                profissional.getId(),
                profissional.getNome(),
                inicio,
                fim,
                aulas.size(),
                totalPagamento.setScale(2, RoundingMode.HALF_UP),
                aulas);
    }

    private Profissional encontrar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Profissional não encontrado: " + id));
    }

    private ProfissionalPagamentoAulaDTO mapearAulaPagamento(Aula aula, BigDecimal percentualPagamentoAula) {
        long quantidadeAulasPagamento = aulaRepository.countByPagamentoId(aula.getPagamento().getId());
        if (quantidadeAulasPagamento == 0) {
            throw new IllegalStateException("Pagamento sem aulas geradas: " + aula.getPagamento().getId());
        }

        BigDecimal valorBaseAula = aula.getPagamento().getValor()
                .divide(BigDecimal.valueOf(quantidadeAulasPagamento), 6, RoundingMode.HALF_UP);
        BigDecimal valorProfissional = valorBaseAula
                .multiply(percentualPagamentoAula)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        return new ProfissionalPagamentoAulaDTO(
                aula.getId(),
                aula.getData(),
                aula.getPaciente().getId(),
                aula.getPaciente().getNome(),
                aula.getPagamento().getId(),
                aula.getPagamento().getValor(),
                quantidadeAulasPagamento,
                valorBaseAula.setScale(2, RoundingMode.HALF_UP),
                percentualPagamentoAula,
                valorProfissional);
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
