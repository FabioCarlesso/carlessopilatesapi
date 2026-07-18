package com.carlesso.pilatesapi.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Foto da análise postural em tabela própria: mantém o binário fora de
 * {@code avaliacoes_posturais} para que listagens e buscas da análise nunca
 * carreguem o bytea. A relação é mapeada apenas deste lado (dono da FK única);
 * a entidade principal expõe {@code fotoContentType} como marcador de presença.
 */
@Entity
@Table(name = "avaliacoes_posturais_fotos")
public class AvaliacaoPosturalFoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "avaliacao_postural_id", nullable = false, unique = true)
    private AvaliacaoPostural avaliacaoPostural;

    @Column(nullable = false)
    private byte[] conteudo;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "tamanho_bytes", nullable = false)
    private int tamanhoBytes;

    @Column(name = "largura_px", nullable = false)
    private int larguraPx;

    @Column(name = "altura_px", nullable = false)
    private int alturaPx;

    @Column(nullable = false)
    private LocalDateTime dataCriacao;

    public AvaliacaoPosturalFoto() {}

    public Long getId() {
        return id;
    }

    public AvaliacaoPostural getAvaliacaoPostural() {
        return avaliacaoPostural;
    }

    public void setAvaliacaoPostural(AvaliacaoPostural avaliacaoPostural) {
        this.avaliacaoPostural = avaliacaoPostural;
    }

    public byte[] getConteudo() {
        return conteudo;
    }

    public void setConteudo(byte[] conteudo) {
        this.conteudo = conteudo;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public int getTamanhoBytes() {
        return tamanhoBytes;
    }

    public void setTamanhoBytes(int tamanhoBytes) {
        this.tamanhoBytes = tamanhoBytes;
    }

    public int getLarguraPx() {
        return larguraPx;
    }

    public void setLarguraPx(int larguraPx) {
        this.larguraPx = larguraPx;
    }

    public int getAlturaPx() {
        return alturaPx;
    }

    public void setAlturaPx(int alturaPx) {
        this.alturaPx = alturaPx;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(LocalDateTime dataCriacao) {
        this.dataCriacao = dataCriacao;
    }
}
