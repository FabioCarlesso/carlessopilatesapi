package com.carlesso.pilatesapi.entity;

import com.carlesso.pilatesapi.entity.enums.IdiomaPreferencia;
import com.carlesso.pilatesapi.entity.enums.TemaPreferencia;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "preferencias_usuario")
public class PreferenciasUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdiomaPreferencia idioma;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TemaPreferencia tema;

    @Column(name = "notificacoes_email", nullable = false)
    private boolean notificacoesEmail;

    @Column(name = "notificacoes_push", nullable = false)
    private boolean notificacoesPush;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    public PreferenciasUsuario() {}

    @PrePersist
    void prePersist() {
        this.dataCriacao = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.dataAtualizacao = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public IdiomaPreferencia getIdioma() {
        return idioma;
    }

    public void setIdioma(IdiomaPreferencia idioma) {
        this.idioma = idioma;
    }

    public TemaPreferencia getTema() {
        return tema;
    }

    public void setTema(TemaPreferencia tema) {
        this.tema = tema;
    }

    public boolean isNotificacoesEmail() {
        return notificacoesEmail;
    }

    public void setNotificacoesEmail(boolean notificacoesEmail) {
        this.notificacoesEmail = notificacoesEmail;
    }

    public boolean isNotificacoesPush() {
        return notificacoesPush;
    }

    public void setNotificacoesPush(boolean notificacoesPush) {
        this.notificacoesPush = notificacoesPush;
    }

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
}
