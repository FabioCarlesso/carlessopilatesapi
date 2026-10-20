package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.config.AgendaProperties;
import com.carlesso.pilatesapi.dto.AulaResponseDTO;
import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.AulaRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import com.carlesso.pilatesapi.util.PaginacaoGuard;
import com.carlesso.pilatesapi.util.PeriodoGuard;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AulaService {

    static final Set<String> CAMPOS_ORDENAVEIS_POR_PACIENTE = Set.of("data", "horario", "realizada");

    private final AulaRepository aulaRepository;
    private final ProfissionalRepository profissionalRepository;
    private final AgendaProperties agendaProperties;

    public AulaService(
            AulaRepository aulaRepository,
            ProfissionalRepository profissionalRepository,
            AgendaProperties agendaProperties) {
        this.aulaRepository = aulaRepository;
        this.profissionalRepository = profissionalRepository;
        this.agendaProperties = agendaProperties;
    }

    @Transactional
    public List<Aula> gerarAulas(Pagamento pagamento) {
        if (pagamento.getStatus() != StatusPagamento.PAGO) {
            throw new BusinessException("Aulas só podem ser geradas para pagamentos com status PAGO");
        }
        if (!pagamento.getPaciente().isAtivo()) {
            throw new BusinessException("Paciente inativo não pode ter aulas geradas");
        }

        var diasSemana = pagamento.getPlano().getDiasSemana();
        // A aula nasce sem profissional, então o horário copiado do plano não passa pela regra de capacidade: ela só
        // entra em jogo quando um profissional é atribuído.
        LocalTime horario = pagamento.getPlano().getHorario();
        LocalDate inicio = pagamento.getPeriodoInicio();
        LocalDate fim = pagamento.getPeriodoFim();

        List<Aula> aulas = new ArrayList<>();
        LocalDate data = inicio;
        while (!data.isAfter(fim)) {
            if (diasSemana.contains(data.getDayOfWeek())) {
                if (!aulaRepository.existsByPacienteAndData(pagamento.getPaciente(), data)) {
                    Aula aula = new Aula();
                    aula.setPaciente(pagamento.getPaciente());
                    aula.setPagamento(pagamento);
                    aula.setData(data);
                    aula.setHorario(horario);
                    aulas.add(aula);
                }
            }
            data = data.plusDays(1);
        }

        return aulaRepository.saveAll(aulas);
    }

    @Transactional(readOnly = true)
    public Page<AulaResponseDTO> buscarPorPaciente(Long pacienteId, Pageable pageable) {
        return aulaRepository.findByPacienteId(
                pacienteId,
                PaginacaoGuard.comNulosNoFimEDesempatePorId(
                        pageable, CAMPOS_ORDENAVEIS_POR_PACIENTE, Sort.Direction.DESC));
    }

    @Transactional(readOnly = true)
    public List<AulaResponseDTO> listarPorPeriodo(
            LocalDate inicio, LocalDate fim, Long profissionalId, Long pacienteId, Boolean realizada) {
        PeriodoGuard.exigirPeriodoValido(inicio, fim);
        List<AulaResponseDTO> aulas =
                aulaRepository.findAgendaPorPeriodo(inicio, fim, profissionalId, pacienteId, realizada);
        PeriodoGuard.exigirVolumeSuportado(aulas.size());
        return aulas;
    }

    @Transactional(readOnly = true)
    public List<AulaResponseDTO> buscarPorPagamento(Long pagamentoId) {
        return aulaRepository.findByPagamentoIdOrderByData(pagamentoId).stream()
                .map(AulaResponseDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AulaResponseDTO buscarPorId(Long id) {
        return AulaResponseDTO.from(encontrar(id));
    }

    @Transactional
    public AulaResponseDTO realizarAula(Long id) {
        return realizarAula(id, null);
    }

    /**
     * Realiza a aula. Com {@code profissionalId}, o vínculo é sobrescrito <b>sem</b> checar a capacidade do horário:
     * a rota registra uma aula que já aconteceu, e recusar o fato obrigaria a recepção a lançá-lo com o profissional
     * errado — o que corromperia o repasse.
     */
    @Transactional
    public AulaResponseDTO realizarAula(Long id, Long profissionalId) {
        Aula aula = encontrar(id);
        if (aula.isRealizada()) {
            throw new ConflictException("Aula já foi marcada como realizada");
        }
        Profissional profissional = resolverProfissional(profissionalId);
        // Sem profissionalId no corpo, o vínculo previamente atribuído é preservado.
        if (profissional != null) {
            aula.setProfissional(profissional);
        }
        aula.setRealizada(true);
        return AulaResponseDTO.from(aula);
    }

    @Transactional
    public AulaResponseDTO atribuirProfissional(Long id, Long profissionalId) {
        Aula aula = encontrar(id);
        if (aula.isRealizada()) {
            throw new ConflictException("Aula já realizada não pode ter o profissional alterado");
        }
        Profissional profissional = resolverProfissional(profissionalId);
        // Reatribuir quem já está na aula não ocupa vaga nova. Sem este atalho, um horário que passou da capacidade
        // por um realizar (que não confere vaga) recusaria com 409 uma operação que não muda nada.
        if (mesmoProfissional(profissional, aula.getProfissional())) {
            return AulaResponseDTO.from(aula);
        }
        exigirVagaNoHorario(aula, profissional, aula.getData(), aula.getHorario());
        aula.setProfissional(profissional);
        return AulaResponseDTO.from(aula);
    }

    /**
     * Troca a data e, opcionalmente, o horário de uma aula ainda não realizada. {@code novoHorario} nulo preserva o
     * horário atual — a rota é um {@code PATCH}, e o campo ausente não apaga o que já estava na agenda.
     *
     * <p>A nova data não é confrontada com {@code periodoInicio}/{@code periodoFim} do pagamento de origem, e pode
     * estar no passado: a decisão de negócio da #170 foi deixar a recepção remarcar livremente, inclusive
     * registrando reposições já ocorridas. O {@code pagamento_id} nunca muda — a aula continua pertencendo ao
     * pagamento que a gerou, mesmo caindo em outro mês, e é por isso que o repasse do profissional pode contá-la
     * num período diferente do que a pagou.
     *
     * <p>Com profissional atribuído, o novo dia e horário precisam ter vaga na agenda dele (ver
     * {@link #exigirVagaNoHorario}).
     */
    @Transactional
    public AulaResponseDTO remarcar(Long id, LocalDate novaData, LocalTime novoHorario) {
        Aula aula = encontrar(id);
        if (aula.isRealizada()) {
            throw new ConflictException("Aula já realizada não pode ser remarcada");
        }
        LocalTime horario = novoHorario != null ? novoHorario : aula.getHorario();
        boolean mudaData = !novaData.equals(aula.getData());
        if (!mudaData && Objects.equals(horario, aula.getHorario())) {
            return AulaResponseDTO.from(aula);
        }
        // Só a troca de dia pode colidir com a (paciente_id, data): mudar a hora no mesmo dia mantém a própria aula.
        if (mudaData && aulaRepository.existsByPacienteAndData(aula.getPaciente(), novaData)) {
            throw new ConflictException(conflitoDeData(novaData));
        }
        exigirVagaNoHorario(aula, aula.getProfissional(), novaData, horario);
        aula.setData(novaData);
        aula.setHorario(horario);
        // A checagem acima não fecha a janela entre o exists e o update — a constraint (paciente_id, data) é a única
        // garantia real. Sem este flush a violação só apareceria no commit, longe deste catch, e escaparia como 500.
        try {
            aulaRepository.saveAndFlush(aula);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(conflitoDeData(novaData));
        }
        return AulaResponseDTO.from(aula);
    }

    /**
     * Recusa com {@code 409} quando o profissional já atende {@code capacidadePorHorario} aulas que disputam o horário.
     *
     * <p>Toda aula dura {@code duracaoAulaMinutos}, então outra aula disputa o horário quando seu início fica a menos
     * dessa duração do início desta. A contagem é conservadora: duas aulas que não se sobrepõem entre si mas sobrepõem
     * esta contam ambas. Só pesa em grades com horários quebrados; com turmas em horários alinhados à duração, a conta é
     * exata.
     *
     * <p>Sem profissional ou sem horário não há o que conferir: a aula ainda não ocupa a agenda de ninguém. O lock no
     * profissional serializa as checagens concorrentes, já que nenhuma constraint do banco garante a capacidade.
     */
    private void exigirVagaNoHorario(Aula aula, Profissional profissional, LocalDate data, LocalTime horario) {
        if (profissional == null || horario == null) {
            return;
        }
        profissionalRepository.findByIdForUpdate(profissional.getId());
        int duracao = agendaProperties.duracaoAulaMinutos();
        LocalTime de = horario.minusMinutes(duracao);
        LocalTime ate = horario.plusMinutes(duracao);
        long ocupadas = aulaRepository.countConcorrentesDoProfissional(
                profissional.getId(),
                data,
                de.isAfter(horario) ? null : de,
                ate.isBefore(horario) ? null : ate,
                aula.getId());
        int capacidade = agendaProperties.capacidadePorHorario();
        if (ocupadas >= capacidade) {
            throw new ConflictException(
                    "Profissional %s já atende %d aula(s) em %s às %s; a capacidade por horário é %d"
                            .formatted(profissional.getNome(), ocupadas, data, horario, capacidade));
        }
    }

    private static boolean mesmoProfissional(Profissional novo, Profissional atual) {
        return novo != null && atual != null && Objects.equals(novo.getId(), atual.getId());
    }

    private String conflitoDeData(LocalDate data) {
        return "Paciente já possui aula na data " + data;
    }

    private Profissional resolverProfissional(Long profissionalId) {
        if (profissionalId == null) {
            return null;
        }
        Profissional profissional = profissionalRepository
                .findById(profissionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Profissional não encontrado: " + profissionalId));
        if (!profissional.isAtivo()) {
            throw new BusinessException("Profissional inativo não pode ser vinculado à aula");
        }
        return profissional;
    }

    private Aula encontrar(Long id) {
        return aulaRepository
                .findByIdAndPacienteAtivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aula não encontrada: " + id));
    }
}
