package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.AulasPorProfissionalDTO;
import com.carlesso.pilatesapi.dto.DashboardIndicadoresDTO;
import com.carlesso.pilatesapi.dto.DashboardResumoDTO;
import com.carlesso.pilatesapi.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "Resumo para o painel inicial do estúdio")
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    // Papéis que veem a receita do mês no resumo; os demais da equipe recebem receitaMesAtual nula.
    private static final Set<String> PAPEIS_COM_RECEITA = Set.of("ROLE_ADMIN", "ROLE_FINANCEIRO");

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @Operation(
            summary = "Resumo do dashboard",
            description =
                    "Retorna contadores e totais consolidados de pacientes, profissionais, pagamentos e aulas para o painel"
                            + " inicial. `pagamentos.receitaMesAtual` só vem preenchida para ADMIN e FINANCEIRO; para os"
                            + " demais papéis da equipe é `null`.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resumo retornado com sucesso"),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido")
    })
    @GetMapping("/resumo")
    public ResponseEntity<DashboardResumoDTO> resumo(Authentication authentication) {
        return ResponseEntity.ok(service.obterResumo(veReceita(authentication)));
    }

    private static boolean veReceita(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(PAPEIS_COM_RECEITA::contains);
    }

    @Operation(
            summary = "Indicadores históricos do dashboard",
            description =
                    "Séries mensais de receita confirmada, aulas (realizadas e não realizadas) e pacientes (novos e"
                            + " inativados) dos últimos `meses` meses, terminando no mês atual. Todos os meses vêm na"
                            + " resposta, com zero quando não houve movimento. A série de pacientes só conta quem tem"
                            + " data de cadastro/inativação, registrada a partir da migration V41: pacientes anteriores"
                            + " a ela não aparecem em nenhum mês. Restrito a ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Indicadores retornados com sucesso"),
        @ApiResponse(responseCode = "400", description = "Parâmetro meses fora de 1..24"),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
        @ApiResponse(responseCode = "403", description = "Usuário sem perfil ADMIN")
    })
    @GetMapping("/indicadores")
    public ResponseEntity<DashboardIndicadoresDTO> indicadores(
            @Parameter(description = "Quantidade de meses da série, incluindo o atual (1 a 24)")
                    @RequestParam(defaultValue = "" + DashboardService.MESES_PADRAO)
                    int meses) {
        return ResponseEntity.ok(service.obterIndicadores(meses));
    }

    @Operation(
            summary = "Aulas realizadas por profissional",
            description = "Ranking de aulas realizadas no período (datas inclusivas), do maior total para o menor."
                    + " Profissionais inativos entram se tiveram aulas; aulas sem profissional ficam de fora."
                    + " Sem `inicio` e `fim`, usa o mês atual. Restrito a ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ranking retornado com sucesso"),
        @ApiResponse(responseCode = "400", description = "Período incompleto, invertido ou acima de 366 dias"),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
        @ApiResponse(responseCode = "403", description = "Usuário sem perfil ADMIN")
    })
    @GetMapping("/aulas-por-profissional")
    public ResponseEntity<List<AulasPorProfissionalDTO>> aulasPorProfissional(
            @Parameter(description = "Data inicial do período (inclusive)") @RequestParam(required = false)
                    LocalDate inicio,
            @Parameter(description = "Data final do período (inclusive)") @RequestParam(required = false)
                    LocalDate fim) {
        return ResponseEntity.ok(service.obterAulasPorProfissional(inicio, fim));
    }
}
