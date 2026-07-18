package com.carlesso.pilatesapi.entity;

import com.carlesso.pilatesapi.entity.enums.StatusAvaliacaoPostural;
import com.carlesso.pilatesapi.entity.enums.VistaPostural;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "avaliacoes_posturais")
public class AvaliacaoPostural {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avaliacao_fisioterapeutica_id", nullable = false)
    private AvaliacaoFisioterapeutica avaliacaoFisioterapeutica;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VistaPostural vista;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusAvaliacaoPostural status = StatusAvaliacaoPostural.RASCUNHO;

    /** Pontos anatômicos em coordenadas normalizadas (0 a 1, relativas à imagem), persistidos como JSONB. */
    @JdbcTypeCode(SqlTypes.JSON)
    private String landmarks;

    // Nome explícito: a naming strategy padrão converteria "linhaPrumoX" para "linha_prumox"
    @Column(name = "linha_prumo_x", precision = 6, scale = 5)
    private BigDecimal linhaPrumoX;

    @Column(precision = 10, scale = 4)
    private BigDecimal calibracaoCmPorUnidade;

    /**
     * Razão largura/altura da foto, usada para converter as coordenadas normalizadas
     * em ângulos fiéis. Quando nula, os ângulos são calculados sobre as coordenadas
     * normalizadas puras (equivalente a uma imagem quadrada).
     */
    @Column(precision = 6, scale = 4)
    private BigDecimal proporcaoImagem;

    /**
     * Content type da foto enviada; serve como marcador de "foto presente" para a API.
     * Os bytes (coluna {@code foto}) são mapeados na issue de upload da foto.
     */
    @Column(length = 100)
    private String fotoContentType;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(nullable = false)
    private LocalDateTime dataCriacao;

    private LocalDateTime dataAtualizacao;

    public AvaliacaoPostural() {}

    @PrePersist
    void prePersist() {
        this.dataCriacao = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public AvaliacaoFisioterapeutica getAvaliacaoFisioterapeutica() {
        return avaliacaoFisioterapeutica;
    }

    public void setAvaliacaoFisioterapeutica(AvaliacaoFisioterapeutica avaliacaoFisioterapeutica) {
        this.avaliacaoFisioterapeutica = avaliacaoFisioterapeutica;
    }

    public VistaPostural getVista() {
        return vista;
    }

    public void setVista(VistaPostural vista) {
        this.vista = vista;
    }

    public StatusAvaliacaoPostural getStatus() {
        return status;
    }

    public void setStatus(StatusAvaliacaoPostural status) {
        this.status = status;
    }

    public String getLandmarks() {
        return landmarks;
    }

    public void setLandmarks(String landmarks) {
        this.landmarks = landmarks;
    }

    public BigDecimal getLinhaPrumoX() {
        return linhaPrumoX;
    }

    public void setLinhaPrumoX(BigDecimal linhaPrumoX) {
        this.linhaPrumoX = linhaPrumoX;
    }

    public BigDecimal getCalibracaoCmPorUnidade() {
        return calibracaoCmPorUnidade;
    }

    public void setCalibracaoCmPorUnidade(BigDecimal calibracaoCmPorUnidade) {
        this.calibracaoCmPorUnidade = calibracaoCmPorUnidade;
    }

    public BigDecimal getProporcaoImagem() {
        return proporcaoImagem;
    }

    public void setProporcaoImagem(BigDecimal proporcaoImagem) {
        this.proporcaoImagem = proporcaoImagem;
    }

    public String getFotoContentType() {
        return fotoContentType;
    }

    public void setFotoContentType(String fotoContentType) {
        this.fotoContentType = fotoContentType;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
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
