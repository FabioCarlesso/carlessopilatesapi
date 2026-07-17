package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.PagamentoResumoDTO;
import com.carlesso.pilatesapi.dto.PeriodoDTO;
import com.carlesso.pilatesapi.dto.ProfissionalPagamentoAulaDTO;
import com.carlesso.pilatesapi.dto.ProfissionalPagamentoRelatorioDTO;
import com.carlesso.pilatesapi.dto.ProfissionalRequestDTO;
import com.carlesso.pilatesapi.dto.ProfissionalResponseDTO;
import com.carlesso.pilatesapi.dto.ProfissionalResumoDTO;
import com.carlesso.pilatesapi.dto.ProfissionalUpdateDTO;
import com.carlesso.pilatesapi.dto.ResumoFinanceiroDTO;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.AulaRepository;
import com.carlesso.pilatesapi.repository.AulaRepository.ProfissionalPagamentoAulaProjection;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfissionalService {

    private static final long LIMITE_DIAS_RELATORIO_PAGAMENTO = 366;
    private static final int LIMITE_AULAS_RELATORIO_PAGAMENTO = 5_000;

    private final ProfissionalRepository repository;
    private final AulaRepository aulaRepository;

    public ProfissionalService(ProfissionalRepository repository, AulaRepository aulaRepository) {
        this.repository = repository;
        this.aulaRepository = aulaRepository;
    }

    @Transactional
    public ProfissionalResponseDTO cadastrar(ProfissionalRequestDTO dto) {
        if (repository.existsByEmail(dto.email())) {
            throw new ConflictException("E-mail já cadastrado: " + dto.email());
        }
        if (repository.existsByCpf(dto.cpf())) {
            throw new ConflictException("CPF já cadastrado: " + dto.cpf());
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
        return repository
                .findAll(filtros(nome, email, tipoContrato, percentualPagamentoAula, ativo), pageable)
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
        if (dto.percentualPagamentoAula() != null)
            profissional.setPercentualPagamentoAula(dto.percentualPagamentoAula());
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
        long diasPeriodo = ChronoUnit.DAYS.between(inicio, fim) + 1;
        if (diasPeriodo > LIMITE_DIAS_RELATORIO_PAGAMENTO) {
            throw new IllegalArgumentException(
                    "Relatório de pagamento limitado a " + LIMITE_DIAS_RELATORIO_PAGAMENTO + " dias");
        }

        Profissional profissional = encontrar(id);
        List<ProfissionalPagamentoAulaDTO> aulas =
                aulaRepository.findRelatorioPagamentoByProfissionalIdAndPeriodo(id, inicio, fim).stream()
                        .map(aula -> mapearAulaPagamento(aula, profissional.getPercentualPagamentoAula()))
                        .toList();
        if (aulas.size() > LIMITE_AULAS_RELATORIO_PAGAMENTO) {
            throw new IllegalArgumentException(
                    "Relatório de pagamento limitado a " + LIMITE_AULAS_RELATORIO_PAGAMENTO + " aulas");
        }

        List<PagamentoResumoDTO> pagamentos = agruparPorPagamento(aulas);

        BigDecimal totalProfissional = aulas.stream()
                .map(ProfissionalPagamentoAulaDTO::valorProfissional)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalBruto = pagamentos.stream()
                .map(PagamentoResumoDTO::valorPagamento)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        ResumoFinanceiroDTO resumo =
                new ResumoFinanceiroDTO(aulas.size(), pagamentos.size(), totalBruto, totalProfissional);

        return new ProfissionalPagamentoRelatorioDTO(
                ProfissionalResumoDTO.from(profissional),
                new PeriodoDTO(inicio, fim),
                resumo,
                pagamentos,
                aulas,
                LocalDateTime.now());
    }

    private List<PagamentoResumoDTO> agruparPorPagamento(List<ProfissionalPagamentoAulaDTO> aulas) {
        Map<Long, PagamentoAcumulador> acumulado = new LinkedHashMap<>();
        for (ProfissionalPagamentoAulaDTO aula : aulas) {
            PagamentoAcumulador acumulador = acumulado.computeIfAbsent(
                    aula.pagamentoId(),
                    id -> new PagamentoAcumulador(
                            aula.valorPagamento(), aula.quantidadeAulasPagamento(), aula.valorBaseAula()));
            acumulador.adicionar(aula.valorProfissional());
        }
        return acumulado.entrySet().stream()
                .map(entry -> new PagamentoResumoDTO(
                        entry.getKey(),
                        entry.getValue().valorPagamento,
                        entry.getValue().quantidadeAulasPagamento,
                        entry.getValue().quantidadeNoPeriodo,
                        entry.getValue().valorBaseAula,
                        entry.getValue().totalProfissional.setScale(2, RoundingMode.HALF_UP)))
                .toList();
    }

    private Profissional encontrar(Long id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profissional não encontrado: " + id));
    }

    private ProfissionalPagamentoAulaDTO mapearAulaPagamento(
            ProfissionalPagamentoAulaProjection aula, BigDecimal percentualPagamentoAula) {
        long quantidadeAulasPagamento = aula.getQuantidadeAulasPagamento();
        if (quantidadeAulasPagamento == 0) {
            throw new BusinessException("Pagamento sem aulas geradas: " + aula.getPagamentoId());
        }

        BigDecimal valorBaseAula =
                aula.getValorPagamento().divide(BigDecimal.valueOf(quantidadeAulasPagamento), 6, RoundingMode.HALF_UP);
        BigDecimal valorProfissional = valorBaseAula
                .multiply(percentualPagamentoAula)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        return new ProfissionalPagamentoAulaDTO(
                aula.getAulaId(),
                aula.getData(),
                aula.getPacienteId(),
                aula.getPacienteNome(),
                aula.getPagamentoId(),
                aula.getValorPagamento(),
                quantidadeAulasPagamento,
                valorBaseAula.setScale(2, RoundingMode.HALF_UP),
                percentualPagamentoAula,
                valorProfissional);
    }

    private Specification<Profissional> filtros(
            String nome, String email, TipoContrato tipoContrato, BigDecimal percentualPagamentoAula, Boolean ativo) {
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
        return (root, query, criteriaBuilder) -> criteriaBuilder.like(criteriaBuilder.lower(root.get(campo)), filtro);
    }

    private <T> Specification<Profissional> igual(String campo, T valor) {
        if (valor == null) {
            return Specification.where(null);
        }

        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(campo), valor);
    }

    private static final class PagamentoAcumulador {
        private final BigDecimal valorPagamento;
        private final long quantidadeAulasPagamento;
        private final BigDecimal valorBaseAula;
        private long quantidadeNoPeriodo;
        private BigDecimal totalProfissional;

        private PagamentoAcumulador(
                BigDecimal valorPagamento, long quantidadeAulasPagamento, BigDecimal valorBaseAula) {
            this.valorPagamento = valorPagamento;
            this.quantidadeAulasPagamento = quantidadeAulasPagamento;
            this.valorBaseAula = valorBaseAula;
            this.quantidadeNoPeriodo = 0;
            this.totalProfissional = BigDecimal.ZERO;
        }

        private void adicionar(BigDecimal valorProfissional) {
            this.quantidadeNoPeriodo++;
            this.totalProfissional = this.totalProfissional.add(valorProfissional);
        }
    }
}
