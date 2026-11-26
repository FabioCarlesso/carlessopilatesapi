package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.PendenciaCadastroPaciente;
import com.carlesso.pilatesapi.dto.RelatorioPacientesDTO;
import com.carlesso.pilatesapi.dto.SituacaoPacienteRelatorio;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.repository.AulaRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PlanoRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import com.carlesso.pilatesapi.repository.ProximaAulaProfissionalProjection;
import com.carlesso.pilatesapi.util.PeriodoGuard;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relatório de pacientes (#230): situação, contato, plano ativo, profissional das próximas aulas e pendências de
 * cadastro. Não é paginado — o volume do estúdio é pequeno — e recusa com {@code 400} acima de
 * {@value #LIMITE_REGISTROS} linhas, o mesmo teto dos demais relatórios sem paginação.
 */
@Service
public class RelatorioPacientesService {

    static final int LIMITE_REGISTROS = PeriodoGuard.LIMITE_REGISTROS_AGENDA;

    private static final Sort POR_NOME = Sort.by("nome").and(Sort.by("id"));

    private final PacienteRepository pacienteRepository;
    private final PlanoRepository planoRepository;
    private final AulaRepository aulaRepository;
    private final ProfissionalRepository profissionalRepository;
    private final Clock relogio;

    @Autowired
    public RelatorioPacientesService(
            PacienteRepository pacienteRepository,
            PlanoRepository planoRepository,
            AulaRepository aulaRepository,
            ProfissionalRepository profissionalRepository,
            @Value("${app.fuso-horario:America/Sao_Paulo}") String fusoHorario) {
        this(
                pacienteRepository,
                planoRepository,
                aulaRepository,
                profissionalRepository,
                Clock.system(ZoneId.of(fusoHorario)));
    }

    RelatorioPacientesService(
            PacienteRepository pacienteRepository,
            PlanoRepository planoRepository,
            AulaRepository aulaRepository,
            ProfissionalRepository profissionalRepository,
            Clock relogio) {
        this.pacienteRepository = pacienteRepository;
        this.planoRepository = planoRepository;
        this.aulaRepository = aulaRepository;
        this.profissionalRepository = profissionalRepository;
        this.relogio = relogio;
    }

    /**
     * Gera o relatório. Os filtros booleanos só restringem quando {@code true}; {@code false} ou ausente é "sem
     * filtro".
     *
     * @throws IllegalArgumentException mês fora de 1–12 ou resultado acima do teto ({@code 400})
     */
    @Transactional(readOnly = true)
    public RelatorioPacientesDTO gerar(
            SituacaoPacienteRelatorio situacao,
            Boolean semPlanoAtivo,
            Boolean cadastroIncompleto,
            Integer mesNascimento,
            Long profissionalId) {
        if (mesNascimento != null && (mesNascimento < 1 || mesNascimento > 12)) {
            throw new IllegalArgumentException("mesNascimento deve estar entre 1 e 12");
        }
        SituacaoPacienteRelatorio situacaoEfetiva = situacao == null ? SituacaoPacienteRelatorio.ATIVOS : situacao;
        boolean somenteSemPlano = Boolean.TRUE.equals(semPlanoAtivo);
        boolean somenteIncompletos = Boolean.TRUE.equals(cadastroIncompleto);
        LocalDate hoje = LocalDate.now(relogio);

        List<Paciente> pacientes = buscarPacientes(situacaoEfetiva);
        Map<Long, Plano> planosAtivos = planosAtivosPorPaciente();
        Map<Long, RelatorioPacientesDTO.ProfissionalResumo> profissionais = profissionalDasProximasAulas(hoje);
        Map<Long, String> nomesProfissionais = profissionalRepository.findAll().stream()
                .collect(Collectors.toMap(Profissional::getId, Profissional::getNome));

        List<RelatorioPacientesDTO.PacienteItem> itens = new ArrayList<>();
        for (Paciente paciente : pacientes) {
            Plano plano = planosAtivos.get(paciente.getId());
            RelatorioPacientesDTO.ProfissionalResumo profissional = profissionais.get(paciente.getId());
            // Só o id do proxy LAZY: o nome vem do mapa, sem um select por paciente.
            Long padraoId = paciente.getProfissionalPadrao() == null
                    ? null
                    : paciente.getProfissionalPadrao().getId();
            RelatorioPacientesDTO.ProfissionalResumo padrao = padraoId == null
                    ? null
                    : new RelatorioPacientesDTO.ProfissionalResumo(padraoId, nomesProfissionais.get(padraoId));
            List<PendenciaCadastroPaciente> pendencias = pendencias(paciente, plano);

            if (somenteSemPlano && !(paciente.isAtivo() && plano == null)) {
                continue;
            }
            if (somenteIncompletos && pendencias.isEmpty()) {
                continue;
            }
            if (mesNascimento != null
                    && (paciente.getDataNascimento() == null
                            || paciente.getDataNascimento().getMonthValue() != mesNascimento)) {
                continue;
            }
            // O filtro casa pelo padrão (#245) ou pelo profissional das próximas aulas: quem cobre a folga do padrão
            // também enxerga o paciente.
            if (profissionalId != null
                    && !profissionalId.equals(padraoId)
                    && (profissional == null || !profissionalId.equals(profissional.id()))) {
                continue;
            }
            itens.add(montarItem(paciente, plano, profissional, padrao, pendencias, hoje));
        }

        if (itens.size() > LIMITE_REGISTROS) {
            throw new IllegalArgumentException(
                    "Relatório limitado a " + LIMITE_REGISTROS + " pacientes; aplique filtros");
        }

        String profissionalNome = profissionalId == null
                ? null
                : profissionalRepository
                        .findById(profissionalId)
                        .map(Profissional::getNome)
                        .orElse(null);
        var filtros = new RelatorioPacientesDTO.Filtros(
                situacaoEfetiva, somenteSemPlano, somenteIncompletos, mesNascimento, profissionalId, profissionalNome);
        return new RelatorioPacientesDTO(filtros, totais(itens), itens, LocalDateTime.now(relogio));
    }

    private List<Paciente> buscarPacientes(SituacaoPacienteRelatorio situacao) {
        return switch (situacao) {
            case ATIVOS -> pacienteRepository.findByAtivo(true, POR_NOME);
            case INATIVOS -> pacienteRepository.findByAtivo(false, POR_NOME);
            case TODOS -> pacienteRepository.findAll(POR_NOME);
        };
    }

    /** Há no máximo um plano ativo por paciente; se a base tiver dois, vale o de início mais recente. */
    private Map<Long, Plano> planosAtivosPorPaciente() {
        Map<Long, Plano> porPaciente = new HashMap<>();
        for (Plano plano : planoRepository.findAtivosComDiasSemana()) {
            porPaciente.merge(
                    plano.getPaciente().getId(),
                    plano,
                    (atual, outro) -> outro.getDataInicio().isAfter(atual.getDataInicio()) ? outro : atual);
        }
        return porPaciente;
    }

    /** A consulta vem ordenada por paciente, data e horário: fica a primeira linha de cada paciente. */
    private Map<Long, RelatorioPacientesDTO.ProfissionalResumo> profissionalDasProximasAulas(LocalDate hoje) {
        Map<Long, RelatorioPacientesDTO.ProfissionalResumo> porPaciente = new HashMap<>();
        for (ProximaAulaProfissionalProjection linha : aulaRepository.findProfissionaisDasProximasAulas(hoje)) {
            porPaciente.putIfAbsent(
                    linha.getPacienteId(),
                    new RelatorioPacientesDTO.ProfissionalResumo(
                            linha.getProfissionalId(), linha.getProfissionalNome()));
        }
        return porPaciente;
    }

    static List<PendenciaCadastroPaciente> pendencias(Paciente paciente, Plano planoAtivo) {
        List<PendenciaCadastroPaciente> pendencias = new ArrayList<>();
        if (vazio(paciente.getCpf())) {
            pendencias.add(PendenciaCadastroPaciente.SEM_CPF);
        }
        if (vazio(paciente.getEmail())) {
            pendencias.add(PendenciaCadastroPaciente.SEM_EMAIL);
        }
        if (vazio(paciente.getTelefone())) {
            pendencias.add(PendenciaCadastroPaciente.SEM_TELEFONE);
        }
        if (paciente.getDataNascimento() == null) {
            pendencias.add(PendenciaCadastroPaciente.SEM_DATA_NASCIMENTO);
        }
        if (paciente.isAtivo() && planoAtivo == null) {
            pendencias.add(PendenciaCadastroPaciente.SEM_PLANO_ATIVO);
        }
        return List.copyOf(pendencias);
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private RelatorioPacientesDTO.PacienteItem montarItem(
            Paciente paciente,
            Plano plano,
            RelatorioPacientesDTO.ProfissionalResumo profissional,
            RelatorioPacientesDTO.ProfissionalResumo profissionalPadrao,
            List<PendenciaCadastroPaciente> pendencias,
            LocalDate hoje) {
        Integer idade = paciente.getDataNascimento() == null
                ? null
                : Period.between(paciente.getDataNascimento(), hoje).getYears();
        return new RelatorioPacientesDTO.PacienteItem(
                paciente.getId(),
                paciente.getNome(),
                paciente.getCpf(),
                paciente.getTelefone(),
                paciente.getEmail(),
                paciente.getDataNascimento(),
                idade,
                paciente.isAtivo(),
                paciente.getDataCadastro(),
                paciente.getDataInativacao(),
                plano == null ? null : planoAtivo(plano),
                profissional,
                profissionalPadrao,
                pendencias);
    }

    private RelatorioPacientesDTO.PlanoAtivo planoAtivo(Plano plano) {
        List<DayOfWeek> dias = plano.getDiasSemana() == null
                ? List.of()
                : plano.getDiasSemana().stream()
                        .sorted(Comparator.naturalOrder())
                        .toList();
        return new RelatorioPacientesDTO.PlanoAtivo(
                plano.getId(),
                plano.getTipo(),
                plano.getFrequenciaSemanal(),
                dias,
                plano.getHorario(),
                plano.getValor());
    }

    private RelatorioPacientesDTO.Totais totais(List<RelatorioPacientesDTO.PacienteItem> itens) {
        long ativos =
                itens.stream().filter(RelatorioPacientesDTO.PacienteItem::ativo).count();
        long comPendencia =
                itens.stream().filter(item -> !item.pendencias().isEmpty()).count();
        return new RelatorioPacientesDTO.Totais(itens.size(), ativos, itens.size() - ativos, comPendencia);
    }
}
