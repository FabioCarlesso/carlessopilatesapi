package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.AvaliacaoPosturalFotoResponseDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoPosturalRequestDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoPosturalResponseDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoPosturalUpdateDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoPosturalVinculoDTO;
import com.carlesso.pilatesapi.service.AvaliacaoPosturalService;
import com.carlesso.pilatesapi.storage.FotoArmazenada;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(
        name = "Análises Posturais",
        description = "Simetrógrafo virtual: marcação de pontos anatômicos e métricas calculadas pelo sistema")
@RestController
@RequestMapping("/avaliacoes-posturais")
public class AvaliacaoPosturalController {

    private final AvaliacaoPosturalService service;

    public AvaliacaoPosturalController(AvaliacaoPosturalService service) {
        this.service = service;
    }

    @Operation(
            summary = "Criar análise postural",
            description =
                    "Cria a análise em RASCUNHO para o paciente: solta, ou vinculada a uma avaliação fisioterapêutica ou a uma reavaliação dele. Cada avaliação e cada reavaliação admite no máximo uma análise ativa por vista; a análise solta não tem esse limite.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Análise criada com sucesso"),
        @ApiResponse(
                responseCode = "400",
                description = "Dados inválidos, campos obrigatórios ausentes ou avaliação e reavaliação juntas"),
        @ApiResponse(
                responseCode = "404",
                description = "Paciente, avaliação fisioterapêutica ou reavaliação não encontrada"),
        @ApiResponse(
                responseCode = "409",
                description = "Já existe análise ativa dessa vista na avaliação ou reavaliação"),
        @ApiResponse(responseCode = "422", description = "Paciente inativo, ou avaliação/reavaliação de outro paciente")
    })
    @PostMapping
    public ResponseEntity<AvaliacaoPosturalResponseDTO> criar(
            @RequestBody @Valid AvaliacaoPosturalRequestDTO dto, UriComponentsBuilder uriBuilder) {
        AvaliacaoPosturalResponseDTO response = service.criar(dto);
        var uri = uriBuilder
                .path("/avaliacoes-posturais/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @Operation(
            summary = "Buscar análise postural por ID",
            description = "Retorna a análise com as métricas calculadas.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Análise encontrada"),
        @ApiResponse(responseCode = "404", description = "Análise não encontrada ou cancelada")
    })
    @GetMapping("/{id}")
    public ResponseEntity<AvaliacaoPosturalResponseDTO> buscarPorId(
            @Parameter(description = "ID da análise postural", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @Operation(summary = "Listar análises posturais ativas da avaliação fisioterapêutica")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Análises encontradas"),
        @ApiResponse(responseCode = "404", description = "Avaliação fisioterapêutica não encontrada")
    })
    @GetMapping("/avaliacao-fisioterapeutica/{avaliacaoId}")
    public ResponseEntity<List<AvaliacaoPosturalResponseDTO>> listarPorAvaliacaoFisioterapeutica(
            @Parameter(description = "ID da avaliação fisioterapêutica", required = true) @PathVariable
                    Long avaliacaoId) {
        return ResponseEntity.ok(service.listarPorAvaliacaoFisioterapeutica(avaliacaoId));
    }

    @Operation(
            summary = "Listar análises posturais ativas do paciente",
            description =
                    "Todas as análises ativas do paciente — soltas, de avaliações e de reavaliações —, da mais recente à mais antiga (data de criação).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Análises encontradas"),
        @ApiResponse(responseCode = "404", description = "Paciente não encontrado")
    })
    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<List<AvaliacaoPosturalResponseDTO>> listarPorPaciente(
            @Parameter(description = "ID do paciente", required = true) @PathVariable Long pacienteId) {
        return ResponseEntity.ok(service.listarPorPaciente(pacienteId));
    }

    @Operation(
            summary = "Vincular, trocar ou remover o vínculo da análise postural",
            description =
                    "Substitui o vínculo da análise: avaliação fisioterapêutica OU reavaliação do mesmo paciente; os dois nulos deixam a análise solta. Aceito também em análise concluída — a foto, os pontos e as métricas não mudam.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Vínculo atualizado"),
        @ApiResponse(responseCode = "400", description = "Avaliação e reavaliação informadas juntas"),
        @ApiResponse(
                responseCode = "404",
                description = "Análise não encontrada ou cancelada, ou avaliação/reavaliação inexistente"),
        @ApiResponse(responseCode = "409", description = "O destino já tem análise ativa da mesma vista"),
        @ApiResponse(responseCode = "422", description = "Avaliação ou reavaliação de outro paciente")
    })
    @PatchMapping("/{id}/vinculo")
    public ResponseEntity<AvaliacaoPosturalResponseDTO> alterarVinculo(
            @Parameter(description = "ID da análise postural", required = true) @PathVariable Long id,
            @RequestBody AvaliacaoPosturalVinculoDTO dto) {
        return ResponseEntity.ok(service.alterarVinculo(id, dto));
    }

    @Operation(
            summary = "Atualizar marcação da análise postural",
            description =
                    "Atualiza landmarks, linha de prumo, calibração, proporção da imagem e observações de uma análise em RASCUNHO. As métricas são recalculadas a cada salvamento e nunca são aceitas no corpo.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Análise atualizada com métricas recalculadas"),
        @ApiResponse(responseCode = "400", description = "Coordenada fora de [0,1] ou dados inválidos"),
        @ApiResponse(responseCode = "404", description = "Análise não encontrada ou cancelada"),
        @ApiResponse(
                responseCode = "422",
                description = "Análise concluída (imutável), ponto de outra vista ou ponto duplicado")
    })
    @PutMapping("/{id}")
    public ResponseEntity<AvaliacaoPosturalResponseDTO> atualizar(
            @Parameter(description = "ID da análise postural", required = true) @PathVariable Long id,
            @RequestBody @Valid AvaliacaoPosturalUpdateDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    @Operation(
            summary = "Concluir análise postural",
            description =
                    "Fecha a análise. Exige todos os pontos obrigatórios da vista marcados e a foto enviada; depois disso a análise fica imutável.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Análise concluída"),
        @ApiResponse(responseCode = "404", description = "Análise não encontrada ou cancelada"),
        @ApiResponse(responseCode = "422", description = "Pontos obrigatórios incompletos, sem foto ou já concluída")
    })
    @PatchMapping("/{id}/concluir")
    public ResponseEntity<AvaliacaoPosturalResponseDTO> concluir(
            @Parameter(description = "ID da análise postural", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(service.concluir(id));
    }

    @Operation(
            summary = "Enviar ou substituir a foto da análise postural",
            description =
                    "Recebe multipart/form-data com o campo 'foto' (JPEG ou PNG, máx. 2 MB e 10000 px por lado, formato validado pelo conteúdo). Só análises em RASCUNHO aceitam foto; um novo envio substitui a anterior.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Foto gravada, metadados retornados"),
        @ApiResponse(responseCode = "400", description = "Arquivo ausente, corrompido ou que não é JPEG/PNG"),
        @ApiResponse(responseCode = "404", description = "Análise não encontrada ou cancelada"),
        @ApiResponse(responseCode = "413", description = "Foto acima do tamanho máximo de 2 MB"),
        @ApiResponse(responseCode = "422", description = "Análise concluída não admite substituição da foto")
    })
    @PutMapping(value = "/{id}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AvaliacaoPosturalFotoResponseDTO> enviarFoto(
            @Parameter(description = "ID da análise postural", required = true) @PathVariable Long id,
            @RequestPart("foto") MultipartFile foto) {
        return ResponseEntity.ok(service.enviarFoto(id, foto));
    }

    @Operation(
            summary = "Recuperar a foto da análise postural",
            description = "Devolve o binário da foto com o Content-Type do upload, para exibição inline.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Foto retornada"),
        @ApiResponse(responseCode = "404", description = "Análise não encontrada, cancelada ou sem foto")
    })
    @GetMapping("/{id}/foto")
    public ResponseEntity<byte[]> baixarFoto(
            @Parameter(description = "ID da análise postural", required = true) @PathVariable Long id) {
        FotoArmazenada foto = service.buscarFoto(id);
        String extensao = MediaType.IMAGE_PNG_VALUE.equals(foto.contentType()) ? "png" : "jpg";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(foto.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"avaliacao-postural-" + id + "." + extensao + "\"")
                .body(foto.conteudo());
    }

    @Operation(
            summary = "Cancelar análise postural",
            description =
                    "Exclusão lógica: a análise sai das listagens e libera a vista para uma nova análise, preservando o histórico clínico.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Análise cancelada"),
        @ApiResponse(responseCode = "404", description = "Análise não encontrada ou já cancelada")
    })
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<AvaliacaoPosturalResponseDTO> cancelar(
            @Parameter(description = "ID da análise postural", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(service.cancelar(id));
    }
}
