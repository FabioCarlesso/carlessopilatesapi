package com.carlesso.pilatesapi.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "anamneses")
public class Anamnese extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Locking otimista: ver {@code VersaoGuard} e a migration V40. */
    @Version
    @Column(nullable = false)
    private Long version;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id", nullable = false, unique = true)
    private Paciente paciente;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String queixaPrincipal;

    @Column(columnDefinition = "TEXT")
    private String historicoDoencas;

    @Column(columnDefinition = "TEXT")
    private String historicoCirurgias;

    @Column(columnDefinition = "TEXT")
    private String historicoLesoes;

    @Column(columnDefinition = "TEXT")
    private String medicamentosUso;

    @Column(columnDefinition = "TEXT")
    private String alergias;

    @Column(length = 50)
    private String nivelAtividadeFisica;

    @Column(columnDefinition = "TEXT")
    private String restricoesMedicas;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String objetivos;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    /** Regiões de dor/queixa (#248): {@code name()} de {@link com.carlesso.pilatesapi.entity.enums.RegiaoDor}. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "regioes_dor")
    private List<String> regioesDor = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String regioesDorComplemento;

    /** Condições de saúde (#248): {@code name()} de {@link com.carlesso.pilatesapi.entity.enums.CondicaoSaude}. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "condicoes_saude")
    private List<String> condicoesSaude = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String condicoesSaudeComplemento;

    /**
     * Nível de atividade física estruturado (#248). Sufixo {@code Opcao} porque {@link #nivelAtividadeFisica}, o texto
     * livre de antes (e da importação do seufisio), continua existindo.
     */
    @Column(length = 40)
    private String nivelAtividadeFisicaOpcao;

    @Column(columnDefinition = "TEXT")
    private String nivelAtividadeFisicaComplemento;

    /**
     * Objetivos estruturados (#248). Sufixo {@code Opcoes} porque {@link #objetivos}, o texto livre obrigatório de
     * antes, continua existindo.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "objetivos_opcoes")
    private List<String> objetivosOpcoes = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String objetivosComplemento;

    public Anamnese() {}

    public Long getVersion() {
        return version;
    }

    public Long getId() {
        return id;
    }

    public Paciente getPaciente() {
        return paciente;
    }

    public void setPaciente(Paciente paciente) {
        this.paciente = paciente;
    }

    public String getQueixaPrincipal() {
        return queixaPrincipal;
    }

    public void setQueixaPrincipal(String queixaPrincipal) {
        this.queixaPrincipal = queixaPrincipal;
    }

    public String getHistoricoDoencas() {
        return historicoDoencas;
    }

    public void setHistoricoDoencas(String historicoDoencas) {
        this.historicoDoencas = historicoDoencas;
    }

    public String getHistoricoCirurgias() {
        return historicoCirurgias;
    }

    public void setHistoricoCirurgias(String historicoCirurgias) {
        this.historicoCirurgias = historicoCirurgias;
    }

    public String getHistoricoLesoes() {
        return historicoLesoes;
    }

    public void setHistoricoLesoes(String historicoLesoes) {
        this.historicoLesoes = historicoLesoes;
    }

    public String getMedicamentosUso() {
        return medicamentosUso;
    }

    public void setMedicamentosUso(String medicamentosUso) {
        this.medicamentosUso = medicamentosUso;
    }

    public String getAlergias() {
        return alergias;
    }

    public void setAlergias(String alergias) {
        this.alergias = alergias;
    }

    public String getNivelAtividadeFisica() {
        return nivelAtividadeFisica;
    }

    public void setNivelAtividadeFisica(String nivelAtividadeFisica) {
        this.nivelAtividadeFisica = nivelAtividadeFisica;
    }

    public String getRestricoesMedicas() {
        return restricoesMedicas;
    }

    public void setRestricoesMedicas(String restricoesMedicas) {
        this.restricoesMedicas = restricoesMedicas;
    }

    public String getObjetivos() {
        return objetivos;
    }

    public void setObjetivos(String objetivos) {
        this.objetivos = objetivos;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public List<String> getRegioesDor() {
        return regioesDor == null ? List.of() : List.copyOf(regioesDor);
    }

    public void setRegioesDor(List<String> regioesDor) {
        this.regioesDor = regioesDor == null ? new ArrayList<>() : new ArrayList<>(regioesDor);
    }

    public String getRegioesDorComplemento() {
        return regioesDorComplemento;
    }

    public void setRegioesDorComplemento(String regioesDorComplemento) {
        this.regioesDorComplemento = regioesDorComplemento;
    }

    public List<String> getCondicoesSaude() {
        return condicoesSaude == null ? List.of() : List.copyOf(condicoesSaude);
    }

    public void setCondicoesSaude(List<String> condicoesSaude) {
        this.condicoesSaude = condicoesSaude == null ? new ArrayList<>() : new ArrayList<>(condicoesSaude);
    }

    public String getCondicoesSaudeComplemento() {
        return condicoesSaudeComplemento;
    }

    public void setCondicoesSaudeComplemento(String condicoesSaudeComplemento) {
        this.condicoesSaudeComplemento = condicoesSaudeComplemento;
    }

    public String getNivelAtividadeFisicaOpcao() {
        return nivelAtividadeFisicaOpcao;
    }

    public void setNivelAtividadeFisicaOpcao(String nivelAtividadeFisicaOpcao) {
        this.nivelAtividadeFisicaOpcao = nivelAtividadeFisicaOpcao;
    }

    public String getNivelAtividadeFisicaComplemento() {
        return nivelAtividadeFisicaComplemento;
    }

    public void setNivelAtividadeFisicaComplemento(String nivelAtividadeFisicaComplemento) {
        this.nivelAtividadeFisicaComplemento = nivelAtividadeFisicaComplemento;
    }

    public List<String> getObjetivosOpcoes() {
        return objetivosOpcoes == null ? List.of() : List.copyOf(objetivosOpcoes);
    }

    public void setObjetivosOpcoes(List<String> objetivosOpcoes) {
        this.objetivosOpcoes = objetivosOpcoes == null ? new ArrayList<>() : new ArrayList<>(objetivosOpcoes);
    }

    public String getObjetivosComplemento() {
        return objetivosComplemento;
    }

    public void setObjetivosComplemento(String objetivosComplemento) {
        this.objetivosComplemento = objetivosComplemento;
    }
}
