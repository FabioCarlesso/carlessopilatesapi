package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.config.AgendaProperties;
import com.carlesso.pilatesapi.dto.AulaResponseDTO;
import com.carlesso.pilatesapi.dto.RelatorioOcupacaoDTO;
import com.carlesso.pilatesapi.dto.RelatorioOcupacaoDTO.Aluno;
import com.carlesso.pilatesapi.dto.RelatorioOcupacaoDTO.Celula;
import com.carlesso.pilatesapi.dto.RelatorioOcupacaoDTO.ProfissionalResumo;
import com.carlesso.pilatesapi.entity.DisponibilidadeProfissional;
import com.carlesso.pilatesapi.entity.ListaEspera;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.repository.AulaRepository;
import com.carlesso.pilatesapi.repository.DisponibilidadeProfissionalRepository;
import com.carlesso.pilatesapi.repository.ListaEsperaRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ocupação da grade semanal (#235).
 *
 * <p>A base são as aulas da semana (as da agenda: paciente ativo), não os planos: a grade mostra quem de fato está
 * marcado em cada horário. As aulas se agrupam por data, horário e profissional; a ocupação é o número de alunos
 * distintos da célula e as vagas, {@code capacidadePorHorario − ocupação}, nunca negativas.
 *
 * <p>Só conta vaga a célula com profissional e com horário: sem profissional a aula não ocupa a agenda de ninguém
 * (mesma regra da capacidade em {@code AulaService}), e sem horário não há horário a vender. Essas células aparecem,
 * com {@code capacidade} e {@code vagas} nulas, para que nenhuma aula da semana suma da grade.
 *
 * <p>Disponibilidade: mesma regra de {@code GET /profissionais/{id}/disponibilidades/verificar}. Profissional sem
 * faixas cadastradas está disponível em qualquer horário; com faixas, a célula está dentro quando alguma faixa do dia
 * da semana contém o horário ({@code [horaInicio, horaFim)}). Fora dela, a célula mostra a ocupação, mas não oferece
 * vaga ({@code vagas = 0}) nem soma capacidade nos totais.
 *
 * <p>Lista de espera: entradas ativas, de paciente ativo (as mesmas de {@code GET /lista-espera}), do mesmo dia da
 * semana e cuja faixa contém o horário da célula ({@code horaInicio <= horario < horaFim}).
 */
@Service
public class RelatorioOcupacaoService {

    private static final Comparator<Celula> ORDEM = Comparator.comparing(Celula::data)
            .thenComparing(Celula::horario, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(
                    c -> c.profissional() == null ? null : c.profissional().nome(),
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(
                    c -> c.profissional() == null ? null : c.profissional().id(),
                    Comparator.nullsLast(Comparator.naturalOrder()));

    private final AulaRepository aulaRepository;
    private final ListaEsperaRepository listaEsperaRepository;
    private final DisponibilidadeProfissionalRepository disponibilidadeRepository;
    private final ProfissionalRepository profissionalRepository;
    private final AgendaProperties agendaProperties;
    private final Clock relogio;

    @Autowired
    public RelatorioOcupacaoService(
            AulaRepository aulaRepository,
            ListaEsperaRepository listaEsperaRepository,
            DisponibilidadeProfissionalRepository disponibilidadeRepository,
            ProfissionalRepository profissionalRepository,
            AgendaProperties agendaProperties,
            @Value("${app.fuso-horario:America/Sao_Paulo}") String fusoHorario) {
        this(
                aulaRepository,
                listaEsperaRepository,
                disponibilidadeRepository,
                profissionalRepository,
                agendaProperties,
                Clock.system(ZoneId.of(fusoHorario)));
    }

    RelatorioOcupacaoService(
            AulaRepository aulaRepository,
            ListaEsperaRepository listaEsperaRepository,
            DisponibilidadeProfissionalRepository disponibilidadeRepository,
            ProfissionalRepository profissionalRepository,
            AgendaProperties agendaProperties,
            Clock relogio) {
        this.aulaRepository = aulaRepository;
        this.listaEsperaRepository = listaEsperaRepository;
        this.disponibilidadeRepository = disponibilidadeRepository;
        this.profissionalRepository = profissionalRepository;
        this.agendaProperties = agendaProperties;
        this.relogio = relogio;
    }

    /**
     * @param semana qualquer data da semana; {@code null} é a semana de hoje no fuso do estúdio
     * @param profissionalId filtro opcional: só as células do profissional
     */
    @Transactional(readOnly = true)
    public RelatorioOcupacaoDTO gerar(LocalDate semana, Long profissionalId) {
        LocalDate referencia = semana == null ? LocalDate.now(relogio) : semana;
        LocalDate inicio = referencia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate fim = inicio.plusDays(6);
        int capacidadePorHorario = agendaProperties.capacidadePorHorario();

        List<AulaResponseDTO> aulas = aulaRepository.findAgendaPorPeriodo(inicio, fim, profissionalId, null, null);
        List<ListaEspera> listaEspera = listaEsperaRepository.buscarAtivas(null, null, null, null);
        Map<Long, List<DisponibilidadeProfissional>> faixasPorProfissional =
                disponibilidadeRepository.findAll().stream()
                        .collect(Collectors.groupingBy(d -> d.getProfissional().getId()));

        Map<Chave, List<AulaResponseDTO>> grupos = new LinkedHashMap<>();
        for (AulaResponseDTO aula : aulas) {
            grupos.computeIfAbsent(
                            new Chave(aula.data(), aula.horario(), aula.profissionalId()), k -> new ArrayList<>())
                    .add(aula);
        }

        List<Celula> celulas = grupos.entrySet().stream()
                .map(e -> celula(e.getKey(), e.getValue(), capacidadePorHorario, listaEspera, faixasPorProfissional))
                .sorted(ORDEM)
                .toList();

        String profissionalNome = profissionalId == null
                ? null
                : profissionalRepository
                        .findById(profissionalId)
                        .map(Profissional::getNome)
                        .orElse(null);

        return new RelatorioOcupacaoDTO(
                new RelatorioOcupacaoDTO.Semana(inicio, fim),
                capacidadePorHorario,
                new RelatorioOcupacaoDTO.Filtros(profissionalId, profissionalNome),
                totais(aulas.size(), celulas),
                celulas,
                LocalDateTime.now(relogio));
    }

    private static Celula celula(
            Chave chave,
            List<AulaResponseDTO> aulas,
            int capacidadePorHorario,
            List<ListaEspera> listaEspera,
            Map<Long, List<DisponibilidadeProfissional>> faixasPorProfissional) {
        List<Aluno> alunos = aulas.stream()
                .map(a -> new Aluno(a.pacienteId(), a.pacienteNome()))
                .distinct()
                .sorted(Comparator.comparing(Aluno::nome).thenComparing(Aluno::id))
                .toList();
        int ocupacao = alunos.size();
        DayOfWeek dia = chave.data().getDayOfWeek();
        AulaResponseDTO primeira = aulas.get(0);
        ProfissionalResumo profissional = chave.profissionalId() == null
                ? null
                : new ProfissionalResumo(chave.profissionalId(), primeira.profissionalNome());

        int espera = chave.horario() == null
                ? 0
                : (int) listaEspera.stream()
                        .filter(l -> l.getDiaSemana() == dia)
                        .filter(l -> contem(l.getHoraInicio(), l.getHoraFim(), chave.horario()))
                        .count();

        boolean contaVaga = chave.profissionalId() != null && chave.horario() != null;
        if (!contaVaga) {
            return new Celula(
                    chave.data(),
                    dia,
                    chave.horario(),
                    profissional,
                    ocupacao,
                    null,
                    null,
                    false,
                    null,
                    espera,
                    alunos);
        }
        boolean dentro = dentroDaDisponibilidade(
                faixasPorProfissional.getOrDefault(chave.profissionalId(), List.of()), dia, chave.horario());
        int vagas = dentro ? Math.max(0, capacidadePorHorario - ocupacao) : 0;
        boolean lotado = ocupacao >= capacidadePorHorario;
        return new Celula(
                chave.data(),
                dia,
                chave.horario(),
                profissional,
                ocupacao,
                capacidadePorHorario,
                vagas,
                lotado,
                dentro,
                espera,
                alunos);
    }

    /** Mesma regra do {@code verificar} da disponibilidade: sem faixas cadastradas, o profissional está disponível. */
    static boolean dentroDaDisponibilidade(List<DisponibilidadeProfissional> faixas, DayOfWeek dia, LocalTime horario) {
        if (faixas.isEmpty()) {
            return true;
        }
        return faixas.stream().filter(f -> f.getDiaSemana() == dia).anyMatch(f -> f.contem(horario));
    }

    private static boolean contem(LocalTime inicio, LocalTime fim, LocalTime horario) {
        return !horario.isBefore(inicio) && horario.isBefore(fim);
    }

    private static RelatorioOcupacaoDTO.Totais totais(int aulas, List<Celula> celulas) {
        long ocupacao = 0;
        long semProfissional = 0;
        long capacidade = 0;
        long vagas = 0;
        for (Celula c : celulas) {
            if (c.profissional() == null) {
                semProfissional += c.ocupacao();
            } else {
                ocupacao += c.ocupacao();
            }
            if (Boolean.TRUE.equals(c.dentroDaDisponibilidade())) {
                capacidade += Objects.requireNonNull(c.capacidade());
            }
            if (c.vagas() != null) {
                vagas += c.vagas();
            }
        }
        return new RelatorioOcupacaoDTO.Totais(aulas, ocupacao, semProfissional, capacidade, vagas);
    }

    private record Chave(LocalDate data, LocalTime horario, Long profissionalId) {}
}
