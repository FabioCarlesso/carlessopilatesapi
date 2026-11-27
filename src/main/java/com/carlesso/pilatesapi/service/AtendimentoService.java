package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.AtendimentoEvolucaoDTO;
import com.carlesso.pilatesapi.dto.AtendimentoRequestDTO;
import com.carlesso.pilatesapi.dto.AtendimentoResponseDTO;
import com.carlesso.pilatesapi.dto.EvolucaoSessaoRequestDTO;
import com.carlesso.pilatesapi.dto.EvolucaoSessaoResponseDTO;
import com.carlesso.pilatesapi.dto.SessaoPilatesResponseDTO;
import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.SessaoPilates;
import com.carlesso.pilatesapi.entity.enums.StatusSessao;
import com.carlesso.pilatesapi.entity.enums.TipoSessao;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.EvolucaoSessaoRepository;
import com.carlesso.pilatesapi.repository.SessaoPilatesRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atendimento a partir da aula (#246): a aula da agenda e a sessão do prontuário seguem sendo cadastros separados — a
 * aula é a base do financeiro, a sessão o registro clínico —, e o atendimento as une. Registrá-lo cria a sessão
 * vinculada à aula (ou reaproveita a que já existe), marca a aula como realizada e, opcionalmente, registra a evolução,
 * numa transação só.
 */
@Service
public class AtendimentoService {

    private final AulaService aulaService;
    private final SessaoPilatesRepository sessaoRepository;
    private final EvolucaoSessaoService evolucaoService;
    private final EvolucaoSessaoRepository evolucaoRepository;
    private final Clock relogio;

    @Autowired
    public AtendimentoService(
            AulaService aulaService,
            SessaoPilatesRepository sessaoRepository,
            EvolucaoSessaoService evolucaoService,
            EvolucaoSessaoRepository evolucaoRepository,
            @Value("${app.fuso-horario:America/Sao_Paulo}") String fusoHorario) {
        this(aulaService, sessaoRepository, evolucaoService, evolucaoRepository, Clock.system(ZoneId.of(fusoHorario)));
    }

    AtendimentoService(
            AulaService aulaService,
            SessaoPilatesRepository sessaoRepository,
            EvolucaoSessaoService evolucaoService,
            EvolucaoSessaoRepository evolucaoRepository,
            Clock relogio) {
        this.aulaService = aulaService;
        this.sessaoRepository = sessaoRepository;
        this.evolucaoService = evolucaoService;
        this.evolucaoRepository = evolucaoRepository;
        this.relogio = relogio;
    }

    /**
     * Registra o atendimento da aula. É idempotente na sessão: chamado de novo, reaproveita a sessão vinculada e nunca
     * cria a segunda. A evolução, ao contrário, não é sobrescrita — se a sessão já tem uma, a edição é pelo
     * {@code PUT /evolucoes-sessao/{id}}, que confere a versão; aceitar aqui apagaria em silêncio o que outro
     * profissional registrou.
     *
     * <p>A aula ainda não realizada é marcada com a regra de {@code PATCH /aulas/{id}/realizar}: o profissional
     * informado sobrescreve o da aula, sem conferir a capacidade do horário. A aula já realizada não muda de
     * profissional: o profissional dela define o repasse, e só o {@code realizar} pode mexer nele. Nesse caso o
     * {@code profissionalId} vale só para a sessão.
     *
     * @throws ResourceNotFoundException aula inexistente ou de paciente inativo, ou profissional inexistente
     * @throws BusinessException aula de feriado ou profissional inativo
     * @throws ConflictException a sessão da aula já tem evolução e o pedido traz outra, a sessão vinculada está
     *     cancelada, ou outro atendimento da mesma aula foi registrado ao mesmo tempo
     */
    @Transactional
    public AtendimentoResponseDTO registrar(Long aulaId, AtendimentoRequestDTO dto) {
        AtendimentoRequestDTO pedido = dto != null ? dto : AtendimentoRequestDTO.VAZIO;
        Aula aula = aulaService.encontrar(aulaId);
        // A aula de feriado conta como realizada para o repasse e o pacote (#244), mas ninguém compareceu.
        if (aula.isFeriado()) {
            throw new BusinessException("Aula de feriado não aceita atendimento");
        }

        Optional<SessaoPilates> existente = sessaoRepository.findByAulaId(aulaId);
        if (existente.isPresent()) {
            exigirSessaoReaproveitavel(existente.get(), pedido.evolucao() != null);
        }

        Profissional profissional = aulaService.resolverProfissional(pedido.profissionalId());
        if (!aula.isRealizada()) {
            aulaService.marcarRealizada(aula, profissional);
        }

        SessaoPilates sessao = existente.orElseGet(() -> criarSessao(aula, profissional, pedido.tipo()));
        // A sessão vinculada pela V50 pode ter ficado AGENDADA; o atendimento registra que ela aconteceu.
        if (sessao.getStatus() == StatusSessao.AGENDADA) {
            sessao.setStatus(StatusSessao.REALIZADA);
        }

        EvolucaoSessaoResponseDTO evolucao = pedido.evolucao() != null
                ? evolucaoService.criar(requisicaoDeEvolucao(sessao.getId(), pedido.evolucao()))
                : evolucaoDa(sessao);
        // O flush antecipa o incremento das versões, para a resposta já levar a da sessão atualizada.
        sessaoRepository.flush();
        return new AtendimentoResponseDTO(aulaService.resposta(aula), SessaoPilatesResponseDTO.from(sessao), evolucao);
    }

    /** Atendimento registrado para a aula; {@code 404} se a aula não existe ou ainda não tem sessão vinculada. */
    @Transactional(readOnly = true)
    public AtendimentoResponseDTO buscar(Long aulaId) {
        Aula aula = aulaService.encontrar(aulaId);
        SessaoPilates sessao = sessaoRepository
                .findByAulaId(aulaId)
                .orElseThrow(() -> new ResourceNotFoundException("Aula sem atendimento registrado: " + aulaId));
        return new AtendimentoResponseDTO(
                aulaService.resposta(aula), SessaoPilatesResponseDTO.from(sessao), evolucaoDa(sessao));
    }

    private void exigirSessaoReaproveitavel(SessaoPilates sessao, boolean comEvolucao) {
        if (sessao.getStatus() == StatusSessao.CANCELADA) {
            throw new ConflictException("A sessão vinculada à aula está cancelada: " + sessao.getId());
        }
        if (comEvolucao && evolucaoRepository.existsBySessaoId(sessao.getId())) {
            throw new ConflictException(
                    "A sessão da aula já possui evolução registrada; edite-a em PUT /evolucoes-sessao/{id}");
        }
    }

    /**
     * Sessão nova, vinculada à aula, já realizada: paciente, data e horário da aula; profissional informado ou, sem ele,
     * o da aula. O flush aqui faz a UNIQUE de {@code aula_id} recusar o segundo de dois atendimentos simultâneos ainda
     * dentro deste método, onde vira {@code 409}, e não no commit, onde escaparia como {@code 500}.
     */
    private SessaoPilates criarSessao(Aula aula, Profissional profissional, TipoSessao tipo) {
        SessaoPilates sessao = new SessaoPilates();
        sessao.setAula(aula);
        sessao.setPaciente(aula.getPaciente());
        sessao.setProfissional(profissional != null ? profissional : aula.getProfissional());
        sessao.setTipo(tipo != null ? tipo : TipoSessao.PILATES);
        sessao.setStatus(StatusSessao.REALIZADA);
        sessao.setData(aula.getData());
        sessao.setHorario(aula.getHorario());
        try {
            return sessaoRepository.saveAndFlush(sessao);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    "O atendimento da aula foi registrado por outra operação simultânea; recarregue a aula");
        }
    }

    private EvolucaoSessaoRequestDTO requisicaoDeEvolucao(Long sessaoId, AtendimentoEvolucaoDTO evolucao) {
        LocalDateTime registro =
                evolucao.dataHoraRegistro() != null ? evolucao.dataHoraRegistro() : LocalDateTime.now(relogio);
        return new EvolucaoSessaoRequestDTO(
                sessaoId,
                registro,
                evolucao.exerciciosRealizados(),
                evolucao.equipamentosUtilizados(),
                evolucao.cargasMolas(),
                evolucao.dorAntes(),
                evolucao.dorDepois(),
                evolucao.respostaPaciente(),
                evolucao.intercorrencias(),
                evolucao.orientacoes(),
                evolucao.observacoesFisioterapeuta());
    }

    private EvolucaoSessaoResponseDTO evolucaoDa(SessaoPilates sessao) {
        return evolucaoRepository
                .findBySessaoId(sessao.getId())
                .map(EvolucaoSessaoResponseDTO::from)
                .orElse(null);
    }
}
