package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.config.AgendaProperties;
import com.carlesso.pilatesapi.dto.AulaResponseDTO;
import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.BloqueioAgenda;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.AulaRepository;
import com.carlesso.pilatesapi.repository.BloqueioAgendaRepository;
import com.carlesso.pilatesapi.repository.DisponibilidadeProfissionalRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import com.carlesso.pilatesapi.util.PaginacaoGuard;
import com.carlesso.pilatesapi.util.PeriodoGuard;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    private final BloqueioAgendaRepository bloqueioAgendaRepository;
    private final DisponibilidadeProfissionalRepository disponibilidadeRepository;

    public AulaService(
            AulaRepository aulaRepository,
            ProfissionalRepository profissionalRepository,
            AgendaProperties agendaProperties,
            BloqueioAgendaRepository bloqueioAgendaRepository,
            DisponibilidadeProfissionalRepository disponibilidadeRepository) {
        this.disponibilidadeRepository = disponibilidadeRepository;
        this.aulaRepository = aulaRepository;
        this.profissionalRepository = profissionalRepository;
        this.agendaProperties = agendaProperties;
        this.bloqueioAgendaRepository = bloqueioAgendaRepository;
    }

    @Transactional
    public List<Aula> gerarAulas(Pagamento pagamento) {
        if (pagamento.getStatus() != StatusPagamento.PAGO) {
            throw new BusinessException("Aulas só podem ser geradas para pagamentos com status PAGO");
        }
        if (!pagamento.getPaciente().isAtivo()) {
            throw new BusinessException("Paciente inativo não pode ter aulas geradas");
        }

        // A aula nasce com o fisioterapeuta padrão do paciente (#245) quando ele tem vaga e disponibilidade no horário;
        // senão, sem profissional, e a capacidade só entra em jogo quando alguém for atribuído.
        LocalTime horario = pagamento.getPlano().getHorario();

        List<LocalDate> datas = datasDoPacote(pagamento.getPlano(), pagamento.getPeriodoInicio());
        Profissional padrao = travarPadraoAtivo(pagamento.getPaciente().getProfissionalPadrao());
        List<BloqueioAgenda> feriados = bloqueioAgendaRepository.findFeriadosDiaInteiroQueIntersectam(
                datas.get(0), datas.get(datas.size() - 1));

        List<Aula> aulas = new ArrayList<>();
        // Uma aula que o paciente já tem na data não é duplicada, mas a data conta para o pacote (#243).
        for (LocalDate data : datas) {
            if (!aulaRepository.existsByPacienteAndData(pagamento.getPaciente(), data)) {
                Aula aula = new Aula();
                aula.setPaciente(pagamento.getPaciente());
                aula.setPagamento(pagamento);
                aula.setData(data);
                aula.setHorario(horario);
                // Aula em feriado já cadastrado nasce realizada (#244).
                feriadoQueCobre(feriados, data).ifPresent(aula::marcarFeriado);
                aula.setProfissional(profissionalPadraoParaAula(padrao, aula));
                aulas.add(aula);
            }
        }

        return aulaRepository.saveAll(aulas);
    }

    /**
     * Datas do pacote de aulas de um pagamento que começa em {@code inicio}: as próximas ocorrências dos
     * {@code diasSemana} do plano, a partir de {@code inicio} inclusive, até completar
     * {@link Plano#getTamanhoDoPacote()}. A
     * última data é o {@code periodoFim} do pagamento — geração e cálculo do período usam este único método para não
     * divergirem.
     */
    public static List<LocalDate> datasDoPacote(Plano plano, LocalDate inicio) {
        Set<DayOfWeek> dias = plano.getDiasSemana() == null ? Set.of() : Set.copyOf(plano.getDiasSemana());
        if (dias.isEmpty()) {
            throw new BusinessException("Plano sem dias da semana não gera pacote de aulas");
        }
        int tamanho = plano.getTamanhoDoPacote();
        List<LocalDate> datas = new ArrayList<>(tamanho);
        LocalDate data = inicio;
        while (datas.size() < tamanho) {
            if (dias.contains(data.getDayOfWeek())) {
                datas.add(data);
            }
            data = data.plusDays(1);
        }
        return datas;
    }

    /** Último dia do pacote que começa em {@code inicio}: o {@code periodoFim} do pagamento. */
    public static LocalDate fimDoPacote(Plano plano, LocalDate inicio) {
        List<LocalDate> datas = datasDoPacote(plano, inicio);
        return datas.get(datas.size() - 1);
    }

    /**
     * Aplica a marcação de feriado de um bloqueio que foi criado, editado ou excluído (#244). Primeiro desfaz o que o
     * bloqueio marcou antes — só as aulas que ele marcou, nunca uma realizada de verdade. Depois, se o bloqueio
     * ({@code null} na exclusão) é feriado de dia inteiro, marca as aulas ainda não realizadas de pacientes ativos do
     * período dele. Por fim, as datas desmarcadas que outro feriado ainda cobre voltam a ser marcadas por esse outro, e
     * as que voltam a pendentes têm o profissional conferido: na aula de feriado ele foi atribuído sem checar vaga.
     */
    @Transactional
    public void sincronizarFeriado(Long bloqueioId, BloqueioAgenda bloqueio) {
        List<Aula> desmarcadas = bloqueioId == null ? List.of() : aulaRepository.findByBloqueioFeriadoId(bloqueioId);
        desmarcadas.forEach(Aula::desmarcarFeriado);

        if (bloqueio != null && bloqueio.marcaAulasComoFeriado()) {
            aulaRepository
                    .findByRealizadaFalseAndPacienteAtivoTrueAndDataBetween(
                            bloqueio.getDataInicio(), bloqueio.getDataFim())
                    .forEach(aula -> marcarFeriado(aula, bloqueio));
        }

        List<Aula> soltas = desmarcadas.stream()
                .filter(a -> !a.isRealizada() && a.getPaciente().isAtivo())
                .toList();
        if (!soltas.isEmpty()) {
            LocalDate de =
                    soltas.stream().map(Aula::getData).min(LocalDate::compareTo).orElseThrow();
            LocalDate ate =
                    soltas.stream().map(Aula::getData).max(LocalDate::compareTo).orElseThrow();
            List<BloqueioAgenda> outros =
                    bloqueioAgendaRepository.findFeriadosDiaInteiroQueIntersectam(de, ate).stream()
                            .filter(b -> !Objects.equals(b.getId(), bloqueioId))
                            .toList();
            soltas.forEach(aula -> feriadoQueCobre(outros, aula.getData()).ifPresent(b -> marcarFeriado(aula, b)));
        }
        // Uma a uma: o flush automático antes de cada contagem faz a aula liberada antes abrir a vaga para a seguinte.
        soltas.stream().filter(a -> !a.isRealizada()).forEach(this::liberarProfissionalSemVaga);
    }

    /**
     * A aula que deixou de ser feriado volta a disputar o horário. O profissional dela pode ter sido atribuído durante o
     * feriado, sem checar vaga nem disponibilidade; se ele não cabe mais, a aula fica sem profissional e entra em
     * {@code aulasSemProfissional} do pagamento, para a recepção decidir — como na geração (#245).
     */
    private void liberarProfissionalSemVaga(Aula aula) {
        Profissional profissional = aula.getProfissional();
        if (profissional == null || aula.getHorario() == null) {
            return;
        }
        if (!cabeNoHorario(profissional, aula)) {
            aula.setProfissional(null);
        }
    }

    /**
     * Marca a aula como feriado. A aula sem profissional recebe o padrão ativo do paciente (#245): realizada, ela gera
     * repasse, e sem profissional o repasse não teria dono. Sem padrão, segue sem profissional e continua aceitando a
     * atribuição em {@link #atribuirProfissional}.
     */
    private void marcarFeriado(Aula aula, BloqueioAgenda bloqueio) {
        aula.marcarFeriado(bloqueio);
        if (aula.getProfissional() == null) {
            Profissional padrao = aula.getPaciente().getProfissionalPadrao();
            if (padrao != null && padrao.isAtivo()) {
                aula.setProfissional(padrao);
            }
        }
    }

    /**
     * O padrão do paciente (#245), travado como em {@link #exigirVagaNoHorario} para gerações concorrentes não
     * passarem da capacidade. Profissional inativo não é usado: as aulas nascem sem profissional.
     */
    private Profissional travarPadraoAtivo(Profissional padrao) {
        if (padrao == null) {
            return null;
        }
        return profissionalRepository
                .findByIdForUpdate(padrao.getId())
                .filter(Profissional::isAtivo)
                .orElse(null);
    }

    /**
     * Profissional com que a aula gerada nasce: o padrão, se ele tem vaga no horário e o horário cabe na
     * disponibilidade dele. Senão a aula nasce sem profissional, como antes da #245, e entra em
     * {@code aulasSemProfissional} do pagamento. Aula sem horário não disputa agenda e recebe o padrão; a de feriado
     * também, sem checagem, porque ninguém comparece e o repasse vai para o profissional da aula.
     */
    private Profissional profissionalPadraoParaAula(Profissional padrao, Aula aula) {
        if (padrao == null || aula.getHorario() == null || aula.isFeriado()) {
            return padrao;
        }
        return cabeNoHorario(padrao, aula) ? padrao : null;
    }

    /** O horário da aula cabe na disponibilidade do profissional e ainda há vaga nele. */
    private boolean cabeNoHorario(Profissional profissional, Aula aula) {
        return dentroDaDisponibilidade(profissional, aula.getData(), aula.getHorario())
                && ocupadasNoHorario(aula, profissional, aula.getData(), aula.getHorario())
                        < agendaProperties.capacidadePorHorario();
    }

    /** Sem disponibilidade cadastrada não há restrição, como em {@code GET /disponibilidades/verificar} (#216). */
    private boolean dentroDaDisponibilidade(Profissional profissional, LocalDate data, LocalTime horario) {
        if (!disponibilidadeRepository.existsByProfissionalId(profissional.getId())) {
            return true;
        }
        return disponibilidadeRepository
                .findByProfissionalIdAndDiaSemana(profissional.getId(), data.getDayOfWeek())
                .stream()
                .anyMatch(faixa -> faixa.contem(horario));
    }

    private static Optional<BloqueioAgenda> feriadoQueCobre(List<BloqueioAgenda> feriados, LocalDate data) {
        return feriados.stream()
                .filter(b -> !data.isBefore(b.getDataInicio()) && !data.isAfter(b.getDataFim()))
                .findFirst();
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
        // A aula de feriado é realizada sem atendimento: o profissional define só quem recebe o repasse (#244), então a
        // troca continua aberta e não disputa vaga no horário.
        if (aula.isRealizada() && !aula.isFeriado()) {
            throw new ConflictException("Aula já realizada não pode ter o profissional alterado");
        }
        Profissional profissional = resolverProfissional(profissionalId);
        if (aula.isFeriado()) {
            aula.setProfissional(profissional);
            return AulaResponseDTO.from(aula);
        }
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
        // Remarcar para um feriado de dia inteiro marca a aula, como na geração e no cadastro do feriado (#244): toda
        // aula
        // pendente num FERIADO é de feriado, e não depende de o bloqueio ser editado depois. Sem atendimento, a vaga
        // no horário não é conferida.
        Optional<BloqueioAgenda> feriado = mudaData
                ? feriadoQueCobre(
                        bloqueioAgendaRepository.findFeriadosDiaInteiroQueIntersectam(novaData, novaData), novaData)
                : Optional.empty();
        if (feriado.isEmpty()) {
            exigirVagaNoHorario(aula, aula.getProfissional(), novaData, horario);
        }
        aula.setData(novaData);
        aula.setHorario(horario);
        feriado.ifPresent(b -> marcarFeriado(aula, b));
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
        long ocupadas = ocupadasNoHorario(aula, profissional, data, horario);
        int capacidade = agendaProperties.capacidadePorHorario();
        if (ocupadas >= capacidade) {
            throw new ConflictException(
                    "Profissional %s já atende %d aula(s) em %s às %s; a capacidade por horário é %d"
                            .formatted(profissional.getNome(), ocupadas, data, horario, capacidade));
        }
    }

    /**
     * Aulas do profissional que disputam o horário. A aula ainda não salva (geração) não tem id: {@code -1} evita
     * o {@code a.id <> NULL}, que no SQL não é verdadeiro para nenhuma linha e zeraria a contagem.
     */
    private long ocupadasNoHorario(Aula aula, Profissional profissional, LocalDate data, LocalTime horario) {
        int duracao = agendaProperties.duracaoAulaMinutos();
        LocalTime de = horario.minusMinutes(duracao);
        LocalTime ate = horario.plusMinutes(duracao);
        return aulaRepository.countConcorrentesDoProfissional(
                profissional.getId(),
                data,
                de.isAfter(horario) ? null : de,
                ate.isBefore(horario) ? null : ate,
                aula.getId() != null ? aula.getId() : -1L);
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
