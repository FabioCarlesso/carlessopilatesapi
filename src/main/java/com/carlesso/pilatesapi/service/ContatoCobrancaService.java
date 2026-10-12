package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.ContatoCobrancaRequestDTO;
import com.carlesso.pilatesapi.dto.ContatoCobrancaResponseDTO;
import com.carlesso.pilatesapi.entity.ContatoCobranca;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.ContatoCobrancaRepository;
import com.carlesso.pilatesapi.repository.PagamentoRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Histórico de contatos de cobrança de um pagamento (issue #197). */
@Service
public class ContatoCobrancaService {

    private static final Logger log = LoggerFactory.getLogger(ContatoCobrancaService.class);

    private final ContatoCobrancaRepository repository;
    private final PagamentoRepository pagamentoRepository;
    private final Clock relogio;

    @Autowired
    public ContatoCobrancaService(
            ContatoCobrancaRepository repository,
            PagamentoRepository pagamentoRepository,
            @Value("${app.fuso-horario:America/Sao_Paulo}") String fusoHorario) {
        this(repository, pagamentoRepository, Clock.system(ZoneId.of(fusoHorario)));
    }

    ContatoCobrancaService(
            ContatoCobrancaRepository repository, PagamentoRepository pagamentoRepository, Clock relogio) {
        this.repository = repository;
        this.pagamentoRepository = pagamentoRepository;
        this.relogio = relogio;
    }

    /**
     * Registra um contato. {@code registradoPor} vem do usuário autenticado, nunca do corpo; {@code registradoEm} é o
     * momento atual no fuso do estúdio. O frontend exibe esse horário sem conversão, então gravá-lo no fuso da JVM
     * (UTC num container) mostraria a hora 3h adiantada.
     *
     * <p>Pagamento {@code PAGO} não aceita novos contatos — não há o que cobrar. Os contatos registrados enquanto ele
     * estava em aberto continuam no histórico. O pagamento é lido com lock de escrita: sem ele, uma confirmação
     * simultânea ({@code PATCH /pagamentos/{id}/pagar}) e este registro leriam os dois {@code PENDENTE}.
     */
    @Transactional
    public ContatoCobrancaResponseDTO registrar(Long pagamentoId, ContatoCobrancaRequestDTO dto, String usuario) {
        // O @PastOrPresent do DTO compara com a data da JVM; em UTC, entre 21h e meia-noite ele aceitaria o dia
        // seguinte
        // do estúdio. A régua que vale é a do estúdio.
        if (dto.dataContato().isAfter(LocalDate.now(relogio))) {
            throw new IllegalArgumentException("dataContato não pode ser uma data futura");
        }
        Pagamento pagamento = pagamentoRepository
                .findByIdForUpdate(pagamentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado: " + pagamentoId));
        if (pagamento.getStatus() == StatusPagamento.PAGO) {
            throw new BusinessException("Pagamento já confirmado não aceita novos contatos de cobrança");
        }

        ContatoCobranca contato = new ContatoCobranca();
        contato.setPagamento(pagamento);
        contato.setDataContato(dto.dataContato());
        contato.setObservacao(dto.observacao().trim());
        contato.setRegistradoPor(usuario);
        contato.setRegistradoEm(LocalDateTime.now(relogio));

        ContatoCobranca salvo = repository.save(contato);
        log.info(
                "Contato de cobrança registrado: contatoId={}, pagamentoId={}, dataContato={}",
                salvo.getId(),
                pagamentoId,
                salvo.getDataContato());
        return ContatoCobrancaResponseDTO.from(salvo);
    }

    @Transactional(readOnly = true)
    public List<ContatoCobrancaResponseDTO> listar(Long pagamentoId) {
        if (!pagamentoRepository.existsById(pagamentoId)) {
            throw new ResourceNotFoundException("Pagamento não encontrado: " + pagamentoId);
        }
        return repository.findByPagamentoIdOrderByDataContatoDescRegistradoEmDescIdDesc(pagamentoId).stream()
                .map(ContatoCobrancaResponseDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ContatoCobrancaResponseDTO buscarPorId(Long pagamentoId, Long id) {
        return repository
                .findByIdAndPagamentoId(id, pagamentoId)
                .map(ContatoCobrancaResponseDTO::from)
                .orElseThrow(() -> new ResourceNotFoundException("Contato de cobrança não encontrado: " + id));
    }
}
