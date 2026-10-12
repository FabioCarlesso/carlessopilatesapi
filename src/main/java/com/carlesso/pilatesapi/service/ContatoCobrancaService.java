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
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Histórico de contatos de cobrança de um pagamento (issue #197). */
@Service
public class ContatoCobrancaService {

    private static final Logger log = LoggerFactory.getLogger(ContatoCobrancaService.class);

    private final ContatoCobrancaRepository repository;
    private final PagamentoRepository pagamentoRepository;

    public ContatoCobrancaService(ContatoCobrancaRepository repository, PagamentoRepository pagamentoRepository) {
        this.repository = repository;
        this.pagamentoRepository = pagamentoRepository;
    }

    /**
     * Registra um contato. {@code registradoPor} vem do usuário autenticado, nunca do corpo; {@code registradoEm} é
     * definido no {@code @PrePersist} da entidade.
     *
     * <p>Pagamento {@code PAGO} não aceita novos contatos — não há o que cobrar. Os contatos registrados enquanto ele
     * estava em aberto continuam no histórico.
     */
    @Transactional
    public ContatoCobrancaResponseDTO registrar(Long pagamentoId, ContatoCobrancaRequestDTO dto, String usuario) {
        Pagamento pagamento = encontrarPagamento(pagamentoId);
        if (pagamento.getStatus() == StatusPagamento.PAGO) {
            throw new BusinessException("Pagamento já confirmado não aceita novos contatos de cobrança");
        }

        ContatoCobranca contato = new ContatoCobranca();
        contato.setPagamento(pagamento);
        contato.setDataContato(dto.dataContato());
        contato.setObservacao(dto.observacao().trim());
        contato.setRegistradoPor(usuario);

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
        encontrarPagamento(pagamentoId);
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

    private Pagamento encontrarPagamento(Long pagamentoId) {
        return pagamentoRepository
                .findById(pagamentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado: " + pagamentoId));
    }
}
