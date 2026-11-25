package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.PeriodoDTO;
import com.carlesso.pilatesapi.dto.RelatorioFrequenciaDTO;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.repository.AulaRepository;
import com.carlesso.pilatesapi.repository.AulaRepository.AulaFrequenciaProjection;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import com.carlesso.pilatesapi.util.PeriodoGuard;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relatório de frequência e faltas por paciente (#233).
 *
 * <p>Falta é a aula com data anterior a hoje (fuso do estúdio) e {@code realizada = false}: a aula de hoje e as
 * futuras ainda podem ser realizadas e não entram em nenhuma conta. A taxa de presença é realizadas / previstas, em
 * percentual com 1 casa decimal (HALF_UP). O filtro {@code taxaMaxima} compara a razão exata, sem o arredondamento:
 * entra quem tem presença estritamente abaixo do valor.
 */
@Service
public class RelatorioFrequenciaService {

    /** Mesmo teto dos demais relatórios por período. */
    static final long LIMITE_DIAS = 366;

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final AulaRepository aulaRepository;
    private final ProfissionalRepository profissionalRepository;
    private final Clock relogio;

    @Autowired
    public RelatorioFrequenciaService(
            AulaRepository aulaRepository,
            ProfissionalRepository profissionalRepository,
            @Value("${app.fuso-horario:America/Sao_Paulo}") String fusoHorario) {
        this(aulaRepository, profissionalRepository, Clock.system(ZoneId.of(fusoHorario)));
    }

    RelatorioFrequenciaService(
            AulaRepository aulaRepository, ProfissionalRepository profissionalRepository, Clock relogio) {
        this.aulaRepository = aulaRepository;
        this.profissionalRepository = profissionalRepository;
        this.relogio = relogio;
    }

    /**
     * @param taxaMaxima filtro opcional, percentual de 0 a 100: só pacientes com presença abaixo dele
     * @throws IllegalArgumentException período ausente, invertido ou acima de {@value #LIMITE_DIAS} dias,
     *     {@code taxaMaxima} fora de 0–100 ou volume acima do teto ({@code 400})
     */
    @Transactional(readOnly = true)
    public RelatorioFrequenciaDTO gerar(
            LocalDate inicio, LocalDate fim, Long profissionalId, Long pacienteId, BigDecimal taxaMaxima) {
        PeriodoGuard.exigirPeriodoValido(inicio, fim, LIMITE_DIAS);
        if (taxaMaxima != null && (taxaMaxima.signum() < 0 || taxaMaxima.compareTo(CEM) > 0)) {
            throw new IllegalArgumentException("taxaMaxima deve estar entre 0 e 100");
        }

        LocalDate ontem = LocalDate.now(relogio).minusDays(1);
        LocalDate consideradoAte = fim.isBefore(ontem) ? fim : ontem;

        List<RelatorioFrequenciaDTO.PacienteFrequencia> pacientes = consideradoAte.isBefore(inicio)
                ? List.of()
                : agrupar(aulaRepository.findParaFrequencia(inicio, consideradoAte), profissionalId, pacienteId);
        List<RelatorioFrequenciaDTO.PacienteFrequencia> filtrados = pacientes.stream()
                .filter(p -> taxaMaxima == null || abaixoDe(p, taxaMaxima))
                .sorted(ORDEM)
                .toList();
        PeriodoGuard.exigirVolumeSuportado(filtrados.size());

        String profissionalNome = profissionalId == null
                ? null
                : profissionalRepository
                        .findById(profissionalId)
                        .map(Profissional::getNome)
                        .orElse(null);
        return new RelatorioFrequenciaDTO(
                new PeriodoDTO(inicio, fim),
                consideradoAte,
                new RelatorioFrequenciaDTO.Filtros(profissionalId, profissionalNome, pacienteId, taxaMaxima),
                totais(filtrados),
                filtrados,
                LocalDateTime.now(relogio));
    }

    /** Pela menor presença (razão exata), depois mais faltas, nome e id. */
    private static final Comparator<RelatorioFrequenciaDTO.PacienteFrequencia> ORDEM = ((Comparator<
                            RelatorioFrequenciaDTO.PacienteFrequencia>)
                    RelatorioFrequenciaService::compararPresenca)
            .thenComparing(RelatorioFrequenciaDTO.PacienteFrequencia::faltas, Comparator.reverseOrder())
            .thenComparing(RelatorioFrequenciaDTO.PacienteFrequencia::pacienteNome, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(RelatorioFrequenciaDTO.PacienteFrequencia::pacienteId);

    private static int compararPresenca(
            RelatorioFrequenciaDTO.PacienteFrequencia a, RelatorioFrequenciaDTO.PacienteFrequencia b) {
        // a.realizadas / a.previstas comparado a b.realizadas / b.previstas, sem divisão.
        return Long.compare(a.aulasRealizadas() * b.aulasPrevistas(), b.aulasRealizadas() * a.aulasPrevistas());
    }

    private static boolean abaixoDe(RelatorioFrequenciaDTO.PacienteFrequencia paciente, BigDecimal taxaMaxima) {
        BigDecimal presencaVezesCem = BigDecimal.valueOf(paciente.aulasRealizadas() * 100);
        BigDecimal limite = taxaMaxima.multiply(BigDecimal.valueOf(paciente.aulasPrevistas()));
        return presencaVezesCem.compareTo(limite) < 0;
    }

    private static List<RelatorioFrequenciaDTO.PacienteFrequencia> agrupar(
            List<AulaFrequenciaProjection> aulas, Long profissionalId, Long pacienteId) {
        Map<Long, Acumulador> porPaciente = new LinkedHashMap<>();
        for (AulaFrequenciaProjection aula : aulas) {
            if (profissionalId != null && !Objects.equals(profissionalId, aula.getProfissionalId())) {
                continue;
            }
            if (pacienteId != null && !Objects.equals(pacienteId, aula.getPacienteId())) {
                continue;
            }
            porPaciente
                    .computeIfAbsent(aula.getPacienteId(), id -> new Acumulador(aula))
                    .somar(aula);
        }
        List<RelatorioFrequenciaDTO.PacienteFrequencia> linhas = new ArrayList<>(porPaciente.size());
        for (Acumulador acumulador : porPaciente.values()) {
            linhas.add(acumulador.linha());
        }
        return linhas;
    }

    private static RelatorioFrequenciaDTO.Totais totais(List<RelatorioFrequenciaDTO.PacienteFrequencia> linhas) {
        long previstas = linhas.stream()
                .mapToLong(RelatorioFrequenciaDTO.PacienteFrequencia::aulasPrevistas)
                .sum();
        long realizadas = linhas.stream()
                .mapToLong(RelatorioFrequenciaDTO.PacienteFrequencia::aulasRealizadas)
                .sum();
        return new RelatorioFrequenciaDTO.Totais(
                linhas.size(), previstas, realizadas, previstas - realizadas, taxa(realizadas, previstas));
    }

    /** Percentual com 1 casa decimal (HALF_UP); null sem aula prevista. */
    static BigDecimal taxa(long realizadas, long previstas) {
        if (previstas == 0) {
            return null;
        }
        return BigDecimal.valueOf(realizadas * 100).divide(BigDecimal.valueOf(previstas), 1, RoundingMode.HALF_UP);
    }

    private static final class Acumulador {
        private final Long pacienteId;
        private final String pacienteNome;
        private final boolean ativo;
        private long previstas;
        private long realizadas;
        private LocalDate ultimaRealizada;

        Acumulador(AulaFrequenciaProjection aula) {
            this.pacienteId = aula.getPacienteId();
            this.pacienteNome = aula.getPacienteNome();
            this.ativo = Boolean.TRUE.equals(aula.getPacienteAtivo());
        }

        Acumulador somar(AulaFrequenciaProjection aula) {
            previstas++;
            if (Boolean.TRUE.equals(aula.getRealizada())) {
                realizadas++;
                if (ultimaRealizada == null || aula.getData().isAfter(ultimaRealizada)) {
                    ultimaRealizada = aula.getData();
                }
            }
            return this;
        }

        RelatorioFrequenciaDTO.PacienteFrequencia linha() {
            return new RelatorioFrequenciaDTO.PacienteFrequencia(
                    pacienteId,
                    pacienteNome,
                    ativo,
                    previstas,
                    realizadas,
                    previstas - realizadas,
                    taxa(realizadas, previstas),
                    ultimaRealizada);
        }
    }
}
