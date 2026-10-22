package com.carlesso.pilatesapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Autoria e timestamps de criação/última alteração, preenchidos pelo Spring Data Auditing (#109).
 *
 * <p>Os nomes {@code dataCriacao}/{@code dataAtualizacao} são contrato dos DTOs consumidos pelo frontend e não devem
 * ser renomeados. {@code dataAtualizacao} e {@code alteradoPor} ficam nulos até a primeira alteração
 * ({@code modifyOnCreate = false} em {@code JpaAuditingConfig}). O autor é o e-mail do usuário autenticado, ou
 * {@code system} para processos sem usuário (schedulers).
 *
 * <p>UPDATEs em massa ({@code @Modifying}) não passam pelo listener: quem os usa precisa gravar data e autor
 * explicitamente.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class EntidadeAuditavel {

    @CreatedDate
    @Column(name = "data_criacao", updatable = false)
    private LocalDateTime dataCriacao;

    @LastModifiedDate
    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @CreatedBy
    @Column(name = "criado_por", updatable = false)
    private String criadoPor;

    @LastModifiedBy
    @Column(name = "alterado_por")
    private String alteradoPor;

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(LocalDateTime dataCriacao) {
        this.dataCriacao = dataCriacao;
    }

    public LocalDateTime getDataAtualizacao() {
        return dataAtualizacao;
    }

    public void setDataAtualizacao(LocalDateTime dataAtualizacao) {
        this.dataAtualizacao = dataAtualizacao;
    }

    public String getCriadoPor() {
        return criadoPor;
    }

    public void setCriadoPor(String criadoPor) {
        this.criadoPor = criadoPor;
    }

    public String getAlteradoPor() {
        return alteradoPor;
    }

    public void setAlteradoPor(String alteradoPor) {
        this.alteradoPor = alteradoPor;
    }
}
