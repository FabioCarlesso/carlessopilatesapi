package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.entity.Anamnese;
import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import com.carlesso.pilatesapi.entity.EvolucaoSessao;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.PlanoTratamento;
import com.carlesso.pilatesapi.entity.Reavaliacao;
import com.carlesso.pilatesapi.entity.SessaoPilates;
import com.carlesso.pilatesapi.entity.TesteFuncionalRealizado;
import com.carlesso.pilatesapi.entity.enums.CampoOpcoesAvaliacao;
import com.carlesso.pilatesapi.entity.enums.TipoSessao;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProntuarioPdfExporterServiceTest {

    @Test
    void opcoesEstruturadas_saemComOsRotulosDoCatalogoEOComplemento() {
        assertThat(ProntuarioPdfExporterService.opcoes(
                        CampoOpcoesAvaliacao.REGIOES_DOR, List.of("CERVICAL", "OMBRO_DIREITO"), " Pior à noite "))
                .isEqualTo("Cervical, Ombro direito. Pior à noite");
        assertThat(ProntuarioPdfExporterService.opcao(CampoOpcoesAvaliacao.EQUILIBRIO, "ALTERACAO_LEVE", null))
                .isEqualTo("Alteração leve");
        assertThat(ProntuarioPdfExporterService.testesFuncionais(
                        List.of(new TesteFuncionalRealizado("SCHOBER", "POSITIVO")), null))
                .isEqualTo("Schober (positivo)");
        // Valor que saiu do catálogo continua legível; sem nada, o campo não sai.
        assertThat(ProntuarioPdfExporterService.opcoes(CampoOpcoesAvaliacao.REGIOES_DOR, List.of("ANTIGA"), null))
                .isEqualTo("ANTIGA");
        assertThat(ProntuarioPdfExporterService.opcoes(CampoOpcoesAvaliacao.REGIOES_DOR, List.of(), " "))
                .isNull();
    }

    private static final LocalDateTime GERADO_EM = LocalDateTime.of(2026, 11, 27, 10, 30);

    private final ProntuarioPdfExporterService exporter = new ProntuarioPdfExporterService();

    @Test
    void exportarProntuario_comPeriodo_trazIdentificacaoSecoesEEvolucoesAssinadas() throws Exception {
        var prontuario = new ProntuarioPdfExporterService.Prontuario(
                paciente(),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                anamnese(),
                List.of(avaliacao()),
                List.of(plano()),
                List.of(reavaliacao()),
                List.of(
                        evolucao(LocalDate.of(2026, 3, 10), "Paula Mendes", "350544-F", "Ponte e alongamento"),
                        evolucao(LocalDate.of(2026, 3, 17), "Paula Mendes", "350544-F", "Mobilidade de quadril")),
                GERADO_EM);

        String texto = texto(exporter.exportarProntuario(prontuario));

        assertThat(texto)
                .contains("Carlesso Pilates")
                .contains("Gerado em 27/11/2026 10:30")
                .contains("Período: 01/01/2026 a 30/06/2026")
                .contains("Ana Silva")
                .contains("15/04/1985")
                .contains("123.456.789-00")
                .contains("(11) 99999-0000")
                .contains("ana@email.com")
                .contains("Dor lombar ao sentar")
                .contains("Diagnóstico fisioterapêutico: Lombalgia mecânica")
                .contains("Fortalecer core")
                .contains("Reavaliação de 01/05/2026")
                .contains("Ponte e alongamento")
                .contains("Fisioterapeuta: Paula Mendes — CREFITO nº 350544-F")
                .contains("Página 1 de");
        // Ordem cronológica: a evolução de 10/03 vem antes da de 17/03.
        assertThat(texto.indexOf("Ponte e alongamento")).isLessThan(texto.indexOf("Mobilidade de quadril"));
    }

    @Test
    void exportarProntuario_semPeriodo_descreveProntuarioCompleto() throws Exception {
        var prontuario = new ProntuarioPdfExporterService.Prontuario(
                paciente(),
                null,
                null,
                anamnese(),
                List.of(avaliacao()),
                List.of(),
                List.of(),
                List.of(evolucao(LocalDate.of(2025, 8, 1), "Paula Mendes", "350544-F", "Primeira aula")),
                GERADO_EM);

        String texto = texto(exporter.exportarProntuario(prontuario));

        assertThat(texto).contains("Período: prontuário completo").contains("Primeira aula");
    }

    @Test
    void exportarProntuario_evolucaoSemCrefito_mostraNomeECrefitoNaoInformado() throws Exception {
        var prontuario = new ProntuarioPdfExporterService.Prontuario(
                paciente(),
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        evolucao(LocalDate.of(2025, 2, 1), "Claudia Carlesso", null, "Registro antigo"),
                        evolucao(LocalDate.of(2025, 2, 8), null, null, "Sessão sem profissional")),
                GERADO_EM);

        String texto = texto(exporter.exportarProntuario(prontuario));

        assertThat(texto)
                .contains("Fisioterapeuta: Claudia Carlesso — CREFITO não informado")
                .contains("Fisioterapeuta: não informado — CREFITO não informado");
    }

    @Test
    void exportarProntuario_pacienteSemAnamneseNemAvaliacao_geraComAvisos() throws Exception {
        Paciente semDados = new Paciente();
        semDados.setNome("Bruno Souza");
        var prontuario = new ProntuarioPdfExporterService.Prontuario(
                semDados, null, null, null, List.of(), List.of(), List.of(), List.of(), GERADO_EM);

        String texto = texto(exporter.exportarProntuario(prontuario));

        assertThat(texto)
                .contains("Bruno Souza")
                .contains("Paciente sem anamnese registrada.")
                .contains("Nenhuma avaliação fisioterapêutica vigente no período.")
                .contains("Nenhum plano de tratamento vigente no período.")
                .contains("Nenhuma reavaliação no período.")
                .contains("Nenhuma evolução registrada no período.");
    }

    @Test
    void exportarProntuario_variasPaginas_numeraComOTotal() throws Exception {
        List<EvolucaoSessao> evolucoes = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            evolucoes.add(evolucao(LocalDate.of(2026, 1, 1).plusDays(i), "Paula Mendes", "350544-F", "Exercício " + i));
        }
        var prontuario = new ProntuarioPdfExporterService.Prontuario(
                paciente(), null, null, anamnese(), List.of(), List.of(), List.of(), evolucoes, GERADO_EM);

        byte[] pdf = exporter.exportarProntuario(prontuario);

        PdfReader reader = new PdfReader(pdf);
        try {
            int paginas = reader.getNumberOfPages();
            assertThat(paginas).isGreaterThan(1);
            String ultima = new PdfTextExtractor(reader).getTextFromPage(paginas);
            assertThat(ultima).contains("Página " + paginas + " de " + paginas);
        } finally {
            reader.close();
        }
    }

    @Test
    void exportarEvolucao_trazPacienteEEvolucaoAssinada() throws Exception {
        String texto = texto(exporter.exportarEvolucao(
                paciente(),
                evolucao(LocalDate.of(2026, 3, 10), "Paula Mendes", "350544-F", "Ponte e alongamento"),
                GERADO_EM));

        assertThat(texto)
                .contains("Evolução de sessão")
                .contains("Ana Silva")
                .contains("Sessão de 10/03/2026 às 09:00 — Pilates")
                .contains("Registrada em: 10/03/2026 09:50")
                .contains("Fisioterapeuta: Paula Mendes — CREFITO nº 350544-F")
                .contains("Página 1 de 1");
    }

    @Test
    void descreverPeriodo_cobreOsLadosAbertos() {
        assertThat(ProntuarioPdfExporterService.descreverPeriodo(LocalDate.of(2026, 1, 1), null))
                .isEqualTo("a partir de 01/01/2026");
        assertThat(ProntuarioPdfExporterService.descreverPeriodo(null, LocalDate.of(2026, 6, 30)))
                .isEqualTo("até 30/06/2026");
    }

    private Paciente paciente() {
        Paciente paciente = new Paciente();
        paciente.setNome("Ana Silva");
        paciente.setDataNascimento(LocalDate.of(1985, 4, 15));
        paciente.setCpf("123.456.789-00");
        paciente.setTelefone("(11) 99999-0000");
        paciente.setEmail("ana@email.com");
        return paciente;
    }

    private Anamnese anamnese() {
        Anamnese anamnese = new Anamnese();
        anamnese.setQueixaPrincipal("Dor lombar ao sentar");
        anamnese.setObjetivos("Voltar a correr");
        return anamnese;
    }

    private AvaliacaoFisioterapeutica avaliacao() {
        AvaliacaoFisioterapeutica avaliacao = new AvaliacaoFisioterapeutica();
        avaliacao.setDataAvaliacao(LocalDate.of(2025, 12, 1));
        avaliacao.setQueixaFuncional("Dificuldade para agachar");
        avaliacao.setEscalaDor(6);
        avaliacao.setDiagnosticoFisioterapeutico("Lombalgia mecânica");
        return avaliacao;
    }

    private PlanoTratamento plano() {
        PlanoTratamento plano = new PlanoTratamento();
        plano.setDataInicio(LocalDate.of(2025, 12, 1));
        plano.setObjetivosTratamento("Fortalecer core");
        return plano;
    }

    private Reavaliacao reavaliacao() {
        Reavaliacao reavaliacao = new Reavaliacao();
        reavaliacao.setDataReavaliacao(LocalDate.of(2026, 5, 1));
        reavaliacao.setEvolucaoDor("Dor caiu de 6 para 2");
        return reavaliacao;
    }

    private EvolucaoSessao evolucao(LocalDate data, String profissional, String registro, String exercicios) {
        SessaoPilates sessao = new SessaoPilates();
        sessao.setData(data);
        sessao.setHorario(LocalTime.of(9, 0));
        sessao.setTipo(TipoSessao.PILATES);
        EvolucaoSessao evolucao = new EvolucaoSessao();
        evolucao.setSessao(sessao);
        evolucao.setProfissionalNome(profissional);
        evolucao.setProfissionalNumeroRegistro(registro);
        evolucao.setDataHoraRegistro(data.atTime(9, 50));
        evolucao.setExerciciosRealizados(exercicios);
        return evolucao;
    }

    private String texto(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder texto = new StringBuilder();
            for (int pagina = 1; pagina <= reader.getNumberOfPages(); pagina++) {
                texto.append(extractor.getTextFromPage(pagina)).append('\n');
            }
            return texto.toString();
        } finally {
            reader.close();
        }
    }
}
