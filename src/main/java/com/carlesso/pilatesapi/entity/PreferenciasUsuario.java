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
import jakarta.persistence.Table;

@Entity
@Table(name = "preferencias_usuario")
public class PreferenciasUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
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

    public PreferenciasUsuario() {}

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
}
