package com.carlesso.pilatesapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Quem recebe a NFSE quando não é o próprio paciente: o responsável por alunos menores ou a empresa que paga as
 * aulas. Um tomador atende vários pacientes, e cada pagamento deles continua sendo uma nota (#229, migration V47).
 */
@Entity
@Table(name = "tomadores")
public class Tomador extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Locking otimista: ver {@code VersaoGuard}. */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private String nome;

    /** Só dígitos: 11 (CPF) ou 14 (CNPJ). */
    @Column(name = "cpf_cnpj", nullable = false, length = 14)
    private String cpfCnpj;

    private String email;

    @Column(length = 30)
    private String telefone;

    @Column(length = 500)
    private String observacoes;

    @Column(nullable = false)
    private boolean ativo = true;

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCpfCnpj() {
        return cpfCnpj;
    }

    public void setCpfCnpj(String cpfCnpj) {
        this.cpfCnpj = cpfCnpj;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
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
}
