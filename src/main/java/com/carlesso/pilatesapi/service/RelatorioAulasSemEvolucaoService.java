package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.PeriodoDTO;
import com.carlesso.pilatesapi.dto.RelatorioAulasSemEvolucaoDTO;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.repository.AulaRepository;
import com.carlesso.pilatesapi.repository.AulaSemEvolucaoProjection;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import com.carlesso.pilatesapi.util.PeriodoGuard;
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
 * Relatório de aulas realizadas sem evolução registrada (#234).
 *
 * <p>A agenda e o repasse ficam na {@code Aula}, e o prontuário na {@code SessaoPilates} com a sua
 * {@code EvolucaoSessao}: são cadastros sem vínculo. Uma aula realizada conta como "com evolução" quando existe sessão
 * do mesmo paciente, na mesma data, com evolução registrada — qualquer sessão do dia, de qualquer profissional e
 * status.
 */
@Service
public class RelatorioAulasSemEvolucaoService {

    /** Mesmo teto dos relatórios por período do financeiro. */
    static final long LIMITE_DIAS = 366;

    static final String SEM_PROFISSIONAL = "Sem profissional";

    private final AulaRepository aulaRepository;
    private final ProfissionalRepository profissionalRepository;
    private final Clock relogio;

    @Autowired
    public RelatorioAulasSemEvolucaoService(
            AulaRepository aulaRepository,
            ProfissionalRepository profissionalRepository,
            @Value("${app.fuso-horario:America/Sao_Paulo}") String fusoHorario) {
        this(aulaRepository, profissionalRepository, Clock.system(ZoneId.of(fusoHorario)));
    }

    RelatorioAulasSemEvolucaoService(
            AulaRepository aulaRepository, ProfissionalRepository profissionalRepository, Clock relogio) {
        this.aulaRepository = aulaRepository;
        this.profissionalRepository = profissionalRepository;
        this.relogio = relogio;
    }

    /**
     * @param profissionalId filtro opcional: só as aulas desse profissional
     * @throws IllegalArgumentException período ausente, invertido, acima de {@value #LIMITE_DIAS} dias ou volume acima
     *     do teto de registros ({@code 400})
     */
    @Transactional(readOnly = true)
    public RelatorioAulasSemEvolucaoDTO gerar(LocalDate inicio, LocalDate fim, Long profissionalId) {
        PeriodoGuard.exigirPeriodoValido(inicio, fim, LIMITE_DIAS);

        // O filtro vai em memória: o período já limita o volume, e parâmetro opcional nulo no JPQL exige casts no
        // PostgreSQL — mesma escolha do relatório de recebimentos.
        List<RelatorioAulasSemEvolucaoDTO.AulaSemEvolucao> aulas =
                aulaRepository.findRealizadasSemEvolucao(inicio, fim).stream()
                        .filter(a -> profissionalId == null || Objects.equals(a.getProfissionalId(), profissionalId))
                        .map(RelatorioAulasSemEvolucaoService::linha)
                        .toList();
        PeriodoGuard.exigirVolumeSuportado(aulas.size());

        String profissionalNome = profissionalId == null
                ? null
                : profissionalRepository
                        .findById(profissionalId)
                        .map(Profissional::getNome)
                        .orElse(null);
        return new RelatorioAulasSemEvolucaoDTO(
                new PeriodoDTO(inicio, fim),
                new RelatorioAulasSemEvolucaoDTO.Filtros(profissionalId, profissionalNome),
                new RelatorioAulasSemEvolucaoDTO.Resumo(aulas.size(), porProfissional(aulas)),
                aulas,
                LocalDateTime.now(relogio));
    }

    private static RelatorioAulasSemEvolucaoDTO.AulaSemEvolucao linha(AulaSemEvolucaoProjection aula) {
        return new RelatorioAulasSemEvolucaoDTO.AulaSemEvolucao(
                aula.getAulaId(),
                aula.getData(),
                aula.getHorario(),
                aula.getPacienteId(),
                aula.getPacienteNome(),
                aula.getProfissionalId(),
                aula.getProfissionalNome());
    }

    /** Por nome do profissional; o grupo sem profissional fica por último. */
    private static List<RelatorioAulasSemEvolucaoDTO.TotalPorProfissional> porProfissional(
            List<RelatorioAulasSemEvolucaoDTO.AulaSemEvolucao> aulas) {
        Map<Long, String> nomes = new LinkedHashMap<>();
        Map<Long, Long> quantidades = new LinkedHashMap<>();
        long semProfissional = 0;
        for (RelatorioAulasSemEvolucaoDTO.AulaSemEvolucao aula : aulas) {
            if (aula.profissionalId() == null) {
                semProfissional++;
            } else {
                nomes.putIfAbsent(aula.profissionalId(), aula.profissionalNome());
                quantidades.merge(aula.profissionalId(), 1L, Long::sum);
            }
        }
        List<RelatorioAulasSemEvolucaoDTO.TotalPorProfissional> totais = new ArrayList<>();
        nomes.forEach((id, nome) ->
                totais.add(new RelatorioAulasSemEvolucaoDTO.TotalPorProfissional(id, nome, quantidades.get(id))));
        totais.sort(Comparator.comparing(
                        RelatorioAulasSemEvolucaoDTO.TotalPorProfissional::profissionalNome,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(RelatorioAulasSemEvolucaoDTO.TotalPorProfissional::profissionalId));
        if (semProfissional > 0) {
            totais.add(new RelatorioAulasSemEvolucaoDTO.TotalPorProfissional(null, SEM_PROFISSIONAL, semProfissional));
        }
        return List.copyOf(totais);
    }
}
