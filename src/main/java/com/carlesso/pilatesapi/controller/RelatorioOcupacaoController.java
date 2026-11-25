package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.RelatorioOcupacaoDTO;
import com.carlesso.pilatesapi.service.RelatorioOcupacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Relatórios", description = "Relatórios financeiros e fiscais")
@RestController
@RequestMapping("/api/relatorios/ocupacao")
public class RelatorioOcupacaoController {

    private final RelatorioOcupacaoService service;

    public RelatorioOcupacaoController(RelatorioOcupacaoService service) {
        this.service = service;
    }

    @Operation(
            summary = "Ocupação da grade semanal",
            description =
                    """
                    Aulas da semana (segunda a domingo) agrupadas por data, horário e profissional, com a ocupação \
                    (alunos distintos), as vagas (`capacidadePorHorario` − ocupação), os alunos de cada célula e os \
                    inscritos da lista de espera cuja faixa contém o horário. Células sem profissional ou sem \
                    horário não contam vaga; fora da disponibilidade cadastrada do profissional, vagas = 0.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Grade gerada com sucesso"),
        @ApiResponse(responseCode = "400", description = "Data ou id em formato inválido")
    })
    @GetMapping
    public ResponseEntity<RelatorioOcupacaoDTO> gerar(
            @Parameter(description = "Qualquer data da semana (`yyyy-MM-dd`); padrão: a semana de hoje")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate semana,
            @Parameter(description = "Só as células deste profissional") @RequestParam(required = false)
                    Long profissionalId) {
        return ResponseEntity.ok(service.gerar(semana, profissionalId));
    }
}
