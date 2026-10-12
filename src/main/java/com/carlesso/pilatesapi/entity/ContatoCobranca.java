package com.carlesso.pilatesapi.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Contato de cobrança feito pelo estúdio sobre um pagamento em aberto: ligação, mensagem ou conversa presencial.
 *
 * <p>É um log de inclusão — não existe edição nem exclusão; uma correção entra como novo contato. {@code dataContato}
 * vem do cliente (o contato pode ter acontecido antes de ser registrado), mas {@code registradoPor} e
 * {@code registradoEm} são do servidor: o primeiro é preenchido pelo serviço a partir do usuário autenticado e o
 * segundo no {@code @PrePersist}. Ver issue #197.
 */
@Entity
@Table(name = "contatos_cobranca")
public class ContatoCobranca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pagamento_id", nullable = false)
    private Pagamento pagamento;

    @Column(name = "data_contato", nullable = false)
    private LocalDate dataContato;

    @Column(nullable = false, length = 500)
    private String observacao;

    @Column(name = "registrado_por", nullable = false)
    private String registradoPor;

    @Column(name = "registrado_em", nullable = false)
    private LocalDateTime registradoEm;

    public ContatoCobranca() {}

    @PrePersist
    void prePersist() {
        if (this.registradoEm == null) {
            this.registradoEm = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Pagamento getPagamento() {
        return pagamento;
    }

    public void setPagamento(Pagamento pagamento) {
        this.pagamento = pagamento;
    }

    public LocalDate getDataContato() {
        return dataContato;
    }

    public void setDataContato(LocalDate dataContato) {
        this.dataContato = dataContato;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public String getRegistradoPor() {
        return registradoPor;
    }

    public void setRegistradoPor(String registradoPor) {
        this.registradoPor = registradoPor;
    }

    public LocalDateTime getRegistradoEm() {
        return registradoEm;
    }

    public void setRegistradoEm(LocalDateTime registradoEm) {
        this.registradoEm = registradoEm;
    }
}
