package com.carlesso.pilatesapi.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "pacientes")
public class Paciente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Locking otimista: ver {@code VersaoGuard} e a migration V40. */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private String nome;

    private String email;

    private String cpf;

    private String telefone;

    private LocalDate dataNascimento;

    @Embedded
    private Endereco endereco;

    @Column(nullable = false)
    private boolean ativo = true;

    /** Nula para quem foi cadastrado antes da V41: ver a migration. */
    private LocalDate dataCadastro;

    /** Preenchida ao inativar e limpa ao reativar; nula também para inativações anteriores à V41. */
    private LocalDate dataInativacao;

    public Paciente() {}

    public Long getVersion() {
        return version;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public void setDataNascimento(LocalDate dataNascimento) {
        this.dataNascimento = dataNascimento;
    }

    public Endereco getEndereco() {
        return endereco;
    }

    public void setEndereco(Endereco endereco) {
        this.endereco = endereco;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public LocalDate getDataCadastro() {
        return dataCadastro;
    }

    public void setDataCadastro(LocalDate dataCadastro) {
        this.dataCadastro = dataCadastro;
    }

    public LocalDate getDataInativacao() {
        return dataInativacao;
    }

    public void setDataInativacao(LocalDate dataInativacao) {
        this.dataInativacao = dataInativacao;
    }
}
