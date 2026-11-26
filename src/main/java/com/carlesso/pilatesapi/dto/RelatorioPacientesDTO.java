package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Relatório de pacientes (#230): filtros aplicados, totais das linhas retornadas e os pacientes, por nome. */
public record RelatorioPacientesDTO(
        Filtros filtros, Totais totais, List<PacienteItem> pacientes, LocalDateTime geradoEm) {

    public record Filtros(
            SituacaoPacienteRelatorio situacao,
            boolean semPlanoAtivo,
            boolean cadastroIncompleto,
            Integer mesNascimento,
            Long profissionalId,
            String profissionalNome) {}

    /** Contagens sobre as linhas retornadas, depois dos filtros. */
    public record Totais(long total, long ativos, long inativos, long comPendencia) {}

    public record PacienteItem(
            Long id,
            String nome,
            String cpf,
            String telefone,
            String email,
            LocalDate dataNascimento,
            Integer idade,
            boolean ativo,
            LocalDate dataCadastro,
            LocalDate dataInativacao,
            PlanoAtivo planoAtivo,
            ProfissionalResumo profissional,
            ProfissionalResumo profissionalPadrao,
            List<PendenciaCadastroPaciente> pendencias) {}

    public record PlanoAtivo(
            Long id,
            TipoPagamento tipo,
            FrequenciaSemanal frequenciaSemanal,
            List<DayOfWeek> diasSemana,
            LocalTime horario,
            BigDecimal valor) {}

    public record ProfissionalResumo(Long id, String nome) {}
}
