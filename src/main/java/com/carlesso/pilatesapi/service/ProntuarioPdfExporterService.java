package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.entity.Anamnese;
import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import com.carlesso.pilatesapi.entity.Endereco;
import com.carlesso.pilatesapi.entity.EvolucaoSessao;
import com.carlesso.pilatesapi.entity.ExameFisicoOpcoes;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.PlanoTratamento;
import com.carlesso.pilatesapi.entity.Reavaliacao;
import com.carlesso.pilatesapi.entity.SessaoPilates;
import com.carlesso.pilatesapi.entity.TesteFuncionalRealizado;
import com.carlesso.pilatesapi.entity.enums.CampoOpcoesAvaliacao;
import com.carlesso.pilatesapi.entity.enums.OpcaoAvaliacao;
import com.carlesso.pilatesapi.entity.enums.TipoSessao;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * PDF (OpenPDF) do prontuário do paciente e da evolução avulsa (#250), no formato que a fiscalização do CREFITO pede
 * pela Resolução COFFITO 414/2012: identificação do paciente, avaliação, diagnóstico, plano terapêutico e evoluções,
 * cada evolução seguida do nome completo e do número no CREFITO de quem a registrou.
 *
 * <p>Recebe entidades já carregadas, e não DTOs: o PDF lê quase todos os campos clínicos, e um DTO por tela só
 * duplicaria cada entidade. Quem chama ({@link ProntuarioService}) gera o PDF dentro da transação de leitura.
 *
 * <p>Cabeçalho (estúdio e data de geração) e rodapé ("Página X de Y") são desenhados por página num
 * {@link PdfPageEventHelper}: o total de páginas só existe ao fechar o documento, por isso entra num
 * {@link PdfTemplate} preenchido no fim.
 */
@Service
public class ProntuarioPdfExporterService {

    /** Não há propriedade com os dados do estúdio (nem na NFSE, emitida fora do sistema): o nome fica fixo. */
    static final String ESTUDIO = "Carlesso Pilates";

    static final String CREFITO_NAO_INFORMADO = "CREFITO não informado";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final Color TEXTO = new Color(33, 37, 41);
    private static final Color SECUNDARIO = new Color(108, 117, 125);

    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, TEXTO);
    private static final Font SECAO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, TEXTO);
    private static final Font SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, TEXTO);
    private static final Font NEGRITO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, TEXTO);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXTO);
    private static final Font ASSINATURA = FontFactory.getFont(FontFactory.HELVETICA_BOLDOBLIQUE, 9, TEXTO);
    private static final Font NOTA = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, SECUNDARIO);

    /**
     * Conteúdo do prontuário já recortado pelo período. {@code inicio}/{@code fim} nulos = lado aberto; {@code anamnese}
     * nula = paciente sem anamnese. As listas vêm em ordem cronológica.
     */
    public record Prontuario(
            Paciente paciente,
            LocalDate inicio,
            LocalDate fim,
            Anamnese anamnese,
            List<AvaliacaoFisioterapeutica> avaliacoes,
            List<PlanoTratamento> planos,
            List<Reavaliacao> reavaliacoes,
            List<EvolucaoSessao> evolucoes,
            LocalDateTime geradoEm) {}

    public byte[] exportarProntuario(Prontuario prontuario) {
        return gerar("Prontuário do paciente", prontuario.geradoEm(), document -> {
            titulo(document, "Prontuário fisioterapêutico");
            document.add(campo("Período: ", descreverPeriodo(prontuario.inicio(), prontuario.fim())));
            identificacao(document, prontuario.paciente());

            secao(document, "Anamnese");
            if (prontuario.anamnese() == null) {
                document.add(nota("Paciente sem anamnese registrada."));
            } else {
                anamnese(document, prontuario.anamnese());
            }

            secao(document, "Avaliação fisioterapêutica e diagnóstico");
            if (prontuario.avaliacoes().isEmpty()) {
                document.add(nota("Nenhuma avaliação fisioterapêutica vigente no período."));
            }
            for (AvaliacaoFisioterapeutica avaliacao : prontuario.avaliacoes()) {
                avaliacao(document, avaliacao);
            }

            secao(document, "Plano de tratamento");
            if (prontuario.planos().isEmpty()) {
                document.add(nota("Nenhum plano de tratamento vigente no período."));
            }
            for (PlanoTratamento plano : prontuario.planos()) {
                plano(document, plano);
            }

            secao(document, "Reavaliações");
            if (prontuario.reavaliacoes().isEmpty()) {
                document.add(nota("Nenhuma reavaliação no período."));
            }
            for (Reavaliacao reavaliacao : prontuario.reavaliacoes()) {
                reavaliacao(document, reavaliacao);
            }

            secao(document, "Evoluções");
            if (prontuario.evolucoes().isEmpty()) {
                document.add(nota("Nenhuma evolução registrada no período."));
            }
            for (EvolucaoSessao evolucao : prontuario.evolucoes()) {
                evolucao(document, evolucao);
            }
        });
    }

    /** Uma evolução só, com a identificação do paciente: o que a fiscalização pede quando confere um atendimento. */
    public byte[] exportarEvolucao(Paciente paciente, EvolucaoSessao evolucao, LocalDateTime geradoEm) {
        return gerar("Evolução de sessão", geradoEm, document -> {
            titulo(document, "Evolução de sessão");
            identificacao(document, paciente);
            secao(document, "Evolução");
            evolucao(document, evolucao);
        });
    }

    @FunctionalInterface
    private interface Conteudo {
        void escrever(Document document) throws DocumentException;
    }

    private byte[] gerar(String tituloCabecalho, LocalDateTime geradoEm, Conteudo conteudo) {
        // Margens superior/inferior maiores que as dos relatórios: abrem espaço para cabeçalho e rodapé por página.
        Document document = new Document(PageSize.A4, 42, 42, 64, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new CabecalhoRodape(tituloCabecalho, geradoEm));
            document.open();
            conteudo.escrever(document);
        } catch (DocumentException e) {
            throw new IllegalStateException("Falha ao gerar PDF do prontuário", e);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
        return out.toByteArray();
    }

    private void titulo(Document document, String texto) throws DocumentException {
        Paragraph titulo = new Paragraph(texto, TITULO);
        titulo.setSpacingAfter(8f);
        document.add(titulo);
    }

    private void secao(Document document, String texto) throws DocumentException {
        Paragraph secao = new Paragraph(texto, SECAO);
        secao.setSpacingBefore(14f);
        secao.setSpacingAfter(2f);
        document.add(secao);
        LineSeparator linha = new LineSeparator(0.5f, 100f, SECUNDARIO, Element.ALIGN_LEFT, -2f);
        document.add(new Chunk(linha));
    }

    private void subtitulo(Document document, String texto) throws DocumentException {
        Paragraph subtitulo = new Paragraph(texto, SUBTITULO);
        subtitulo.setSpacingBefore(8f);
        subtitulo.setSpacingAfter(2f);
        document.add(subtitulo);
    }

    private void identificacao(Document document, Paciente paciente) throws DocumentException {
        secao(document, "Identificação do paciente");
        document.add(campo("Nome: ", paciente.getNome()));
        document.add(campo("Data de nascimento: ", data(paciente.getDataNascimento())));
        document.add(campo("CPF: ", paciente.getCpf()));
        document.add(campo("Telefone: ", paciente.getTelefone()));
        document.add(campo("E-mail: ", paciente.getEmail()));
        String endereco = endereco(paciente.getEndereco());
        if (endereco != null) {
            document.add(campo("Endereço: ", endereco));
        }
    }

    private void anamnese(Document document, Anamnese anamnese) throws DocumentException {
        if (anamnese.getDataCriacao() != null) {
            document.add(campo("Registrada em: ", anamnese.getDataCriacao().format(DATE)));
        }
        campos(
                document,
                "Queixa principal: ",
                anamnese.getQueixaPrincipal(),
                "Histórico de doenças: ",
                anamnese.getHistoricoDoencas(),
                "Histórico de cirurgias: ",
                anamnese.getHistoricoCirurgias(),
                "Histórico de lesões: ",
                anamnese.getHistoricoLesoes(),
                "Medicamentos em uso: ",
                anamnese.getMedicamentosUso(),
                "Alergias: ",
                anamnese.getAlergias(),
                "Nível de atividade física: ",
                anamnese.getNivelAtividadeFisica(),
                "Restrições médicas: ",
                anamnese.getRestricoesMedicas(),
                "Objetivos: ",
                anamnese.getObjetivos(),
                "Observações: ",
                anamnese.getObservacoes());
        campos(
                document,
                rotuloOpcoes(CampoOpcoesAvaliacao.REGIOES_DOR),
                opcoes(CampoOpcoesAvaliacao.REGIOES_DOR, anamnese.getRegioesDor(), anamnese.getRegioesDorComplemento()),
                rotuloOpcoes(CampoOpcoesAvaliacao.CONDICOES_SAUDE),
                opcoes(
                        CampoOpcoesAvaliacao.CONDICOES_SAUDE,
                        anamnese.getCondicoesSaude(),
                        anamnese.getCondicoesSaudeComplemento()),
                rotuloOpcoes(CampoOpcoesAvaliacao.NIVEL_ATIVIDADE_FISICA),
                opcao(
                        CampoOpcoesAvaliacao.NIVEL_ATIVIDADE_FISICA,
                        anamnese.getNivelAtividadeFisicaOpcao(),
                        anamnese.getNivelAtividadeFisicaComplemento()),
                rotuloOpcoes(CampoOpcoesAvaliacao.OBJETIVOS),
                opcoes(
                        CampoOpcoesAvaliacao.OBJETIVOS,
                        anamnese.getObjetivosOpcoes(),
                        anamnese.getObjetivosComplemento()));
    }

    private void avaliacao(Document document, AvaliacaoFisioterapeutica avaliacao) throws DocumentException {
        subtitulo(document, "Avaliação de " + data(avaliacao.getDataAvaliacao()));
        document.add(campo("Diagnóstico fisioterapêutico: ", avaliacao.getDiagnosticoFisioterapeutico()));
        campos(
                document,
                "Queixa funcional: ",
                avaliacao.getQueixaFuncional(),
                "Escala de dor (0–10): ",
                numero(avaliacao.getEscalaDor()),
                "Avaliação postural: ",
                avaliacao.getAvaliacaoPostural(),
                "Mobilidade articular: ",
                avaliacao.getMobilidadeArticular(),
                "Força muscular: ",
                avaliacao.getForcaMuscular(),
                "Flexibilidade: ",
                avaliacao.getFlexibilidade(),
                "Equilíbrio: ",
                avaliacao.getEquilibrio(),
                "Coordenação motora: ",
                avaliacao.getCoordenacaoMotora(),
                "Padrão respiratório: ",
                avaliacao.getPadraoRespiratorio(),
                "Testes funcionais: ",
                avaliacao.getTestesFuncionaisRealizados(),
                "Observações gerais: ",
                avaliacao.getObservacoesGerais());
        exameFisico(document, avaliacao.getExameFisicoOpcoes());
    }

    private void plano(Document document, PlanoTratamento plano) throws DocumentException {
        String vigencia = "Plano de " + data(plano.getDataInicio())
                + (plano.getDataFimPrevista() == null ? "" : " a " + data(plano.getDataFimPrevista()) + " (previsto)");
        subtitulo(document, vigencia);
        campos(
                document,
                "Objetivos do tratamento: ",
                plano.getObjetivosTratamento(),
                "Intervenções planejadas: ",
                plano.getIntervencoesPlanejadas(),
                "Sessões previstas: ",
                numero(plano.getNumeroSessoesPrevistas()),
                "Frequência: ",
                plano.getFrequenciaSessoes(),
                "Responsável: ",
                plano.getResponsavelTratamento(),
                "Observações: ",
                plano.getObservacoes());
    }

    private void reavaliacao(Document document, Reavaliacao reavaliacao) throws DocumentException {
        subtitulo(document, "Reavaliação de " + data(reavaliacao.getDataReavaliacao()));
        campos(
                document,
                "Comparativo com a avaliação anterior: ",
                reavaliacao.getComparativoAvaliacaoAnterior(),
                "Evolução da dor: ",
                reavaliacao.getEvolucaoDor(),
                "Evolução da força: ",
                reavaliacao.getEvolucaoForca(),
                "Evolução da mobilidade: ",
                reavaliacao.getEvolucaoMobilidade(),
                "Evolução funcional: ",
                reavaliacao.getEvolucaoFuncional(),
                "Objetivos alcançados: ",
                reavaliacao.getObjetivosAlcancados(),
                "Pontos de atenção: ",
                reavaliacao.getPontosAtencao(),
                "Ajustes recomendados: ",
                reavaliacao.getAjustesRecomendados(),
                "Observações gerais: ",
                reavaliacao.getObservacoesGerais());
        campos(
                document,
                rotuloOpcoes(CampoOpcoesAvaliacao.REGIOES_DOR),
                opcoes(
                        CampoOpcoesAvaliacao.REGIOES_DOR,
                        reavaliacao.getRegioesDor(),
                        reavaliacao.getRegioesDorComplemento()));
        exameFisico(document, reavaliacao.getExameFisicoOpcoes());
    }

    /**
     * Campos estruturados do exame físico (#248), comuns à avaliação e à reavaliação. Saem com os rótulos do catálogo,
     * para a fiscalização ler "Hiperlordose lombar" e não {@code HIPERLORDOSE_LOMBAR}; campo vazio não sai.
     */
    private void exameFisico(Document document, ExameFisicoOpcoes exame) throws DocumentException {
        if (exame == null) {
            return;
        }
        campos(
                document,
                rotuloOpcoes(CampoOpcoesAvaliacao.ACHADOS_POSTURAIS),
                opcoes(
                        CampoOpcoesAvaliacao.ACHADOS_POSTURAIS,
                        exame.getAchadosPosturais(),
                        exame.getAchadosPosturaisComplemento()),
                rotuloOpcoes(CampoOpcoesAvaliacao.PADRAO_RESPIRATORIO),
                opcao(
                        CampoOpcoesAvaliacao.PADRAO_RESPIRATORIO,
                        exame.getPadraoRespiratorioOpcao(),
                        exame.getPadraoRespiratorioComplemento()),
                rotuloOpcoes(CampoOpcoesAvaliacao.EQUILIBRIO),
                opcao(CampoOpcoesAvaliacao.EQUILIBRIO, exame.getEquilibrioOpcao(), exame.getEquilibrioComplemento()),
                rotuloOpcoes(CampoOpcoesAvaliacao.COORDENACAO_MOTORA),
                opcao(
                        CampoOpcoesAvaliacao.COORDENACAO_MOTORA,
                        exame.getCoordenacaoMotoraOpcao(),
                        exame.getCoordenacaoMotoraComplemento()),
                rotuloOpcoes(CampoOpcoesAvaliacao.TESTES_FUNCIONAIS),
                testesFuncionais(exame.getTestesFuncionais(), exame.getTestesFuncionaisComplemento()));
    }

    static String rotuloOpcoes(CampoOpcoesAvaliacao campo) {
        return campo.getRotulo() + " (opções): ";
    }

    /** Rótulos das opções, na ordem gravada, seguidos do complemento livre; {@code null} se não há nada. */
    static String opcoes(CampoOpcoesAvaliacao campo, List<String> valores, String complemento) {
        String selecionadas = valores == null
                ? ""
                : valores.stream().map(valor -> rotuloDaOpcao(campo, valor)).collect(Collectors.joining(", "));
        return comComplemento(selecionadas, complemento);
    }

    static String opcao(CampoOpcoesAvaliacao campo, String valor, String complemento) {
        return comComplemento(valor == null || valor.isBlank() ? "" : rotuloDaOpcao(campo, valor), complemento);
    }

    static String testesFuncionais(List<TesteFuncionalRealizado> testes, String complemento) {
        String realizados = testes == null
                ? ""
                : testes.stream()
                        .map(t -> rotuloDaOpcao(CampoOpcoesAvaliacao.TESTES_FUNCIONAIS, t.teste()) + " ("
                                + ("POSITIVO".equals(t.resultado()) ? "positivo" : "negativo") + ")")
                        .collect(Collectors.joining(", "));
        return comComplemento(realizados, complemento);
    }

    /** Valor que saiu do catálogo continua legível: sem rótulo conhecido, sai o próprio valor gravado. */
    private static String rotuloDaOpcao(CampoOpcoesAvaliacao campo, String valor) {
        return campo.getOpcoes().stream()
                .filter(o -> o.name().equals(valor))
                .map(OpcaoAvaliacao::getRotulo)
                .findFirst()
                .orElse(valor);
    }

    private static String comComplemento(String texto, String complemento) {
        boolean temComplemento = complemento != null && !complemento.isBlank();
        if (texto.isEmpty()) {
            return temComplemento ? complemento.trim() : null;
        }
        return temComplemento ? texto + ". " + complemento.trim() : texto;
    }

    private void evolucao(Document document, EvolucaoSessao evolucao) throws DocumentException {
        SessaoPilates sessao = evolucao.getSessao();
        subtitulo(document, descreverSessao(sessao));
        if (evolucao.getDataHoraRegistro() != null) {
            document.add(campo("Registrada em: ", evolucao.getDataHoraRegistro().format(DATE_TIME)));
        }
        campos(
                document,
                "Exercícios realizados: ",
                evolucao.getExerciciosRealizados(),
                "Equipamentos utilizados: ",
                evolucao.getEquipamentosUtilizados(),
                "Cargas/molas: ",
                evolucao.getCargasMolas(),
                "Dor antes (0–10): ",
                numero(evolucao.getDorAntes()),
                "Dor depois (0–10): ",
                numero(evolucao.getDorDepois()),
                "Resposta do paciente: ",
                evolucao.getRespostaPaciente(),
                "Intercorrências: ",
                evolucao.getIntercorrencias(),
                "Orientações: ",
                evolucao.getOrientacoes(),
                "Observações do fisioterapeuta: ",
                evolucao.getObservacoesFisioterapeuta());
        // COFFITO 414/2012: no prontuário eletrônico, nome completo e CREFITO logo após cada registro.
        Paragraph assinatura = new Paragraph(assinatura(evolucao), ASSINATURA);
        assinatura.setSpacingBefore(3f);
        document.add(assinatura);
    }

    /**
     * Linha de identificação do fisioterapeuta, a partir do snapshot gravado na evolução — e não do cadastro atual:
     * vale quem atendeu e o número que tinha no dia. Evolução anterior à exigência do CREFITO sai com
     * {@value #CREFITO_NAO_INFORMADO}, sem quebrar o documento.
     */
    static String assinatura(EvolucaoSessao evolucao) {
        String nome = vazio(evolucao.getProfissionalNome()) ? "não informado" : evolucao.getProfissionalNome();
        String registro = vazio(evolucao.getProfissionalNumeroRegistro())
                ? CREFITO_NAO_INFORMADO
                : "CREFITO nº " + evolucao.getProfissionalNumeroRegistro();
        return "Fisioterapeuta: " + nome + " — " + registro;
    }

    static String descreverPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio == null && fim == null) {
            return "prontuário completo";
        }
        if (inicio == null) {
            return "até " + data(fim);
        }
        if (fim == null) {
            return "a partir de " + data(inicio);
        }
        return data(inicio) + " a " + data(fim);
    }

    private static String descreverSessao(SessaoPilates sessao) {
        if (sessao == null) {
            return "Sessão";
        }
        StringBuilder texto = new StringBuilder("Sessão de ").append(data(sessao.getData()));
        if (sessao.getHorario() != null) {
            texto.append(" às ").append(sessao.getHorario().format(HORA));
        }
        if (sessao.getTipo() != null) {
            texto.append(" — ").append(sessao.getTipo() == TipoSessao.PILATES ? "Pilates" : "Fisioterapia");
        }
        return texto.toString();
    }

    /** Pares rótulo/valor; os vazios ficam de fora para o prontuário mostrar só o que foi registrado. */
    private void campos(Document document, String... rotulosEValores) throws DocumentException {
        for (int i = 0; i + 1 < rotulosEValores.length; i += 2) {
            if (!vazio(rotulosEValores[i + 1])) {
                document.add(campo(rotulosEValores[i], rotulosEValores[i + 1]));
            }
        }
    }

    private Paragraph campo(String rotulo, String valor) {
        Phrase phrase = new Phrase();
        phrase.add(new Chunk(rotulo, NEGRITO));
        phrase.add(new Chunk(vazio(valor) ? "-" : valor, NORMAL));
        Paragraph paragrafo = new Paragraph(phrase);
        paragrafo.setSpacingAfter(1.5f);
        return paragrafo;
    }

    private Paragraph nota(String texto) {
        return new Paragraph(texto, NOTA);
    }

    private static String endereco(Endereco endereco) {
        if (endereco == null) {
            return null;
        }
        String logradouro = Stream.of(endereco.getLogradouro(), endereco.getNumero())
                .filter(parte -> !vazio(parte))
                .collect(Collectors.joining(", "));
        String cidade = Stream.of(endereco.getCidade(), endereco.getUf())
                .filter(parte -> !vazio(parte))
                .collect(Collectors.joining("/"));
        List<String> partes = new ArrayList<>();
        Stream.of(
                        logradouro,
                        endereco.getBairro(),
                        cidade,
                        vazio(endereco.getCep()) ? null : "CEP " + endereco.getCep())
                .filter(parte -> !vazio(parte))
                .forEach(partes::add);
        return partes.isEmpty() ? null : String.join(" — ", partes);
    }

    private static String data(LocalDate data) {
        return data == null ? null : data.format(DATE);
    }

    private static String numero(Integer numero) {
        return numero == null ? null : numero.toString();
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    /** Estúdio e data de geração no topo, aviso de sigilo e "Página X de Y" no pé de toda página. */
    private static final class CabecalhoRodape extends PdfPageEventHelper {

        private static final float TAMANHO = 8f;

        private final String titulo;
        private final LocalDateTime geradoEm;
        private PdfTemplate totalPaginas;
        private BaseFont fonte;

        private CabecalhoRodape(String titulo, LocalDateTime geradoEm) {
            this.titulo = titulo;
            this.geradoEm = geradoEm;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            try {
                fonte = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            } catch (DocumentException | IOException e) {
                throw new IllegalStateException("Fonte padrão do PDF indisponível", e);
            }
            totalPaginas = writer.getDirectContent().createTemplate(40, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            Rectangle pagina = document.getPageSize();
            float esquerda = document.left();
            float direita = document.right();
            Font fonteCabecalho = FontFactory.getFont(FontFactory.HELVETICA, TAMANHO, SECUNDARIO);

            float topo = pagina.getTop() - 34;
            ColumnText.showTextAligned(
                    canvas,
                    Element.ALIGN_LEFT,
                    new Phrase(ESTUDIO + " — " + titulo, fonteCabecalho),
                    esquerda,
                    topo,
                    0);
            ColumnText.showTextAligned(
                    canvas,
                    Element.ALIGN_RIGHT,
                    new Phrase("Gerado em " + geradoEm.format(DATE_TIME), fonteCabecalho),
                    direita,
                    topo,
                    0);

            float base = pagina.getBottom() + 28;
            ColumnText.showTextAligned(
                    canvas,
                    Element.ALIGN_LEFT,
                    new Phrase("Documento sigiloso — Resolução COFFITO nº 414/2012", fonteCabecalho),
                    esquerda,
                    base,
                    0);

            String texto = "Página " + writer.getPageNumber() + " de ";
            float larguraTexto = fonte.getWidthPoint(texto, TAMANHO);
            float larguraTotal = fonte.getWidthPoint("000", TAMANHO);
            float x = direita - larguraTexto - larguraTotal;
            canvas.saveState();
            canvas.beginText();
            canvas.setFontAndSize(fonte, TAMANHO);
            canvas.setColorFill(SECUNDARIO);
            canvas.setTextMatrix(x, base);
            canvas.showText(texto);
            canvas.endText();
            canvas.addTemplate(totalPaginas, x + larguraTexto, base);
            canvas.restoreState();
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            // No fechamento o writer já avançou para a página seguinte à última.
            totalPaginas.beginText();
            totalPaginas.setFontAndSize(fonte, TAMANHO);
            totalPaginas.setColorFill(SECUNDARIO);
            totalPaginas.setTextMatrix(0, 0);
            totalPaginas.showText(String.valueOf(writer.getPageNumber() - 1));
            totalPaginas.endText();
        }
    }
}
