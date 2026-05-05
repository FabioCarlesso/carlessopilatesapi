package com.carlesso.pilatesapi.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "evolucoes_sessao")
public class EvolucaoSessao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_id", nullable = false, unique = true)
    private SessaoPilates sessao;

    @Column(nullable = false)
    private LocalDateTime dataHoraRegistro;

    @Column(columnDefinition = "TEXT")
    private String exerciciosRealizados;

    @Column(columnDefinition = "TEXT")
    private String equipamentosUtilizados;

    @Column(columnDefinition = "TEXT")
    private String cargasMolas;

    private Integer dorAntes;

    private Integer dorDepois;

    @Column(columnDefinition = "TEXT")
    private String respostaPaciente;

    @Column(columnDefinition = "TEXT")
    private String intercorrencias;

    @Column(columnDefinition = "TEXT")
    private String orientacoes;

    @Column(columnDefinition = "TEXT")
    private String observacoesFisioterapeuta;

    @Column(nullable = false)
    private LocalDateTime dataCriacao;

    private LocalDateTime dataAtualizacao;

    public EvolucaoSessao() {}

    @PrePersist
    void prePersist() {
        this.dataCriacao = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public SessaoPilates getSessao() { return sessao; }
    public void setSessao(SessaoPilates sessao) { this.sessao = sessao; }

    public LocalDateTime getDataHoraRegistro() { return dataHoraRegistro; }
    public void setDataHoraRegistro(LocalDateTime dataHoraRegistro) { this.dataHoraRegistro = dataHoraRegistro; }

    public String getExerciciosRealizados() { return exerciciosRealizados; }
    public void setExerciciosRealizados(String exerciciosRealizados) { this.exerciciosRealizados = exerciciosRealizados; }

    public String getEquipamentosUtilizados() { return equipamentosUtilizados; }
    public void setEquipamentosUtilizados(String equipamentosUtilizados) { this.equipamentosUtilizados = equipamentosUtilizados; }

    public String getCargasMolas() { return cargasMolas; }
    public void setCargasMolas(String cargasMolas) { this.cargasMolas = cargasMolas; }

    public Integer getDorAntes() { return dorAntes; }
    public void setDorAntes(Integer dorAntes) { this.dorAntes = dorAntes; }

    public Integer getDorDepois() { return dorDepois; }
    public void setDorDepois(Integer dorDepois) { this.dorDepois = dorDepois; }

    public String getRespostaPaciente() { return respostaPaciente; }
    public void setRespostaPaciente(String respostaPaciente) { this.respostaPaciente = respostaPaciente; }

    public String getIntercorrencias() { return intercorrencias; }
    public void setIntercorrencias(String intercorrencias) { this.intercorrencias = intercorrencias; }

    public String getOrientacoes() { return orientacoes; }
    public void setOrientacoes(String orientacoes) { this.orientacoes = orientacoes; }

    public String getObservacoesFisioterapeuta() { return observacoesFisioterapeuta; }
    public void setObservacoesFisioterapeuta(String observacoesFisioterapeuta) { this.observacoesFisioterapeuta = observacoesFisioterapeuta; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    public LocalDateTime getDataAtualizacao() { return dataAtualizacao; }
    public void setDataAtualizacao(LocalDateTime dataAtualizacao) { this.dataAtualizacao = dataAtualizacao; }
}
