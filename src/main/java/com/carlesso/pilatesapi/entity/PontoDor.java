package com.carlesso.pilatesapi.entity;

import com.carlesso.pilatesapi.entity.enums.VistaCorporal;
import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Ponto de dor marcado sobre o mapa corporal (#249).
 *
 * <p>Pertence a exatamente um registro clínico — {@link #avaliacaoFisioterapeutica} ou {@link #reavaliacao} —, regra
 * garantida pelo {@code CHECK ck_pontos_dor_um_registro} da V53. O relacionamento é mapeado só deste lado: as
 * entidades de avaliação e reavaliação não conhecem os pontos, que são lidos e substituídos por rotas próprias.
 *
 * <p>Não tem {@code @Version}: o ponto nunca é editado, o conjunto inteiro é substituído (ver
 * {@code PontoDorService}).
 */
@Entity
@Table(name = "pontos_dor")
public class PontoDor extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avaliacao_fisioterapeutica_id")
    private AvaliacaoFisioterapeutica avaliacaoFisioterapeutica;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reavaliacao_id")
    private Reavaliacao reavaliacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VistaCorporal vista;

    /** Coordenada horizontal normalizada (0 a 1) relativa ao {@code viewBox} do desenho. */
    @Column(nullable = false, precision = 7, scale = 6)
    private BigDecimal x;

    /** Coordenada vertical normalizada (0 a 1) relativa ao {@code viewBox} do desenho. */
    @Column(nullable = false, precision = 7, scale = 6)
    private BigDecimal y;

    /** Versão do desenho em que o ponto foi marcado, para o FE abrir o registro antigo sobre o desenho certo. */
    @Column(name = "desenho_versao", nullable = false)
    private Integer desenhoVersao;

    private Integer intensidade;

    @Column(length = 200)
    private String observacao;

    public PontoDor() {}

    public Long getId() {
        return id;
    }

    public Paciente getPaciente() {
        return paciente;
    }

    public void setPaciente(Paciente paciente) {
        this.paciente = paciente;
    }

    public AvaliacaoFisioterapeutica getAvaliacaoFisioterapeutica() {
        return avaliacaoFisioterapeutica;
    }

    public void setAvaliacaoFisioterapeutica(AvaliacaoFisioterapeutica avaliacaoFisioterapeutica) {
        this.avaliacaoFisioterapeutica = avaliacaoFisioterapeutica;
    }

    public Reavaliacao getReavaliacao() {
        return reavaliacao;
    }

    public void setReavaliacao(Reavaliacao reavaliacao) {
        this.reavaliacao = reavaliacao;
    }

    public VistaCorporal getVista() {
        return vista;
    }

    public void setVista(VistaCorporal vista) {
        this.vista = vista;
    }

    public BigDecimal getX() {
        return x;
    }

    public void setX(BigDecimal x) {
        this.x = x;
    }

    public BigDecimal getY() {
        return y;
    }

    public void setY(BigDecimal y) {
        this.y = y;
    }

    public Integer getDesenhoVersao() {
        return desenhoVersao;
    }

    public void setDesenhoVersao(Integer desenhoVersao) {
        this.desenhoVersao = desenhoVersao;
    }

    public Integer getIntensidade() {
        return intensidade;
    }

    public void setIntensidade(Integer intensidade) {
        this.intensidade = intensidade;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }
}
