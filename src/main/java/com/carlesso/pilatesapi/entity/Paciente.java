package com.carlesso.pilatesapi.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "pacientes")
public class Paciente extends EntidadeAuditavel {

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

    /** O paciente pediu CPF/CNPJ na NFSE: ver a migration V47 e o relatório de NFSE. */
    @Column(name = "cpf_na_nota", nullable = false)
    private boolean cpfNaNota = false;

    /** CPF/CNPJ do tomador alternativo da nota (responsável ou empresa), só dígitos; nulo = o próprio paciente. */
    @Column(name = "nota_cpf_cnpj", length = 14)
    private String notaCpfCnpj;

    /** Nome do tomador alternativo; só faz sentido com {@link #notaCpfCnpj}. */
    @Column(name = "nota_nome")
    private String notaNome;

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

    public boolean isCpfNaNota() {
        return cpfNaNota;
    }

    public void setCpfNaNota(boolean cpfNaNota) {
        this.cpfNaNota = cpfNaNota;
    }

    public String getNotaCpfCnpj() {
        return notaCpfCnpj;
    }

    public void setNotaCpfCnpj(String notaCpfCnpj) {
        this.notaCpfCnpj = notaCpfCnpj;
    }

    public String getNotaNome() {
        return notaNome;
    }

    public void setNotaNome(String notaNome) {
        this.notaNome = notaNome;
    }
}
