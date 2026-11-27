package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import com.carlesso.pilatesapi.entity.EvolucaoSessao;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.AnamneseRepository;
import com.carlesso.pilatesapi.repository.AvaliacaoFisioterapeuticaRepository;
import com.carlesso.pilatesapi.repository.EvolucaoSessaoRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PlanoTratamentoRepository;
import com.carlesso.pilatesapi.repository.ReavaliacaoRepository;
import com.carlesso.pilatesapi.security.UsuarioAuditorAware;
import com.carlesso.pilatesapi.util.LogMasker;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exportação do prontuário em PDF (#250): recorta o conteúdo clínico do paciente pelo período e entrega ao
 * {@link ProntuarioPdfExporterService}.
 *
 * <p>Recorte pelo período (limites inclusivos; lado ausente = aberto):
 *
 * <ul>
 *   <li><b>Evoluções</b>: pela data da <i>sessão</i>, e não por {@code dataHoraRegistro} — é a sessão que posiciona o
 *       atendimento na linha do tempo (mesma ordem da tela), e a evolução pode ser registrada dias depois.
 *   <li><b>Reavaliações</b>: pela {@code dataReavaliacao}.
 *   <li><b>Anamnese</b>: sempre entra — é uma por paciente e é a base de todo o prontuário, sem data de vigência.
 *   <li><b>Avaliação fisioterapêutica</b> (com o diagnóstico): as vigentes no período, isto é, a última feita antes do
 *       início (que ainda valia quando o período começou) e as feitas dentro dele. Sem período, todas.
 *   <li><b>Plano de tratamento</b>: os não excluídos cuja vigência ({@code dataInicio} até {@code dataFimPrevista},
 *       aberta quando nula) cruza o período.
 * </ul>
 *
 * <p>Não há tabela de auditoria de ações (a autoria das entidades é só criação/alteração), então a exportação fica
 * registrada em log {@code INFO} com paciente, período e usuário — o prontuário é sigiloso e quem o tirou do sistema
 * precisa ser rastreável.
 */
@Service
public class ProntuarioService {

    private static final Logger log = LoggerFactory.getLogger(ProntuarioService.class);

    /** Sentinelas do lado aberto do período: o JPQL fica sem {@code IS NULL OR}, que o Postgres não tipa bem. */
    static final LocalDate INICIO_ABERTO = LocalDate.of(1900, 1, 1);

    static final LocalDate FIM_ABERTO = LocalDate.of(9999, 12, 31);

    private final PacienteRepository pacienteRepository;
    private final AnamneseRepository anamneseRepository;
    private final AvaliacaoFisioterapeuticaRepository avaliacaoRepository;
    private final PlanoTratamentoRepository planoRepository;
    private final ReavaliacaoRepository reavaliacaoRepository;
    private final EvolucaoSessaoRepository evolucaoRepository;
    private final ProntuarioPdfExporterService exporter;
    private final AuditorAware<String> auditor;
    private final Clock relogio;

    /** A data de geração sai no fuso do estúdio, não no da JVM (o container roda em UTC), como nos relatórios. */
    @Autowired
    public ProntuarioService(
            PacienteRepository pacienteRepository,
            AnamneseRepository anamneseRepository,
            AvaliacaoFisioterapeuticaRepository avaliacaoRepository,
            PlanoTratamentoRepository planoRepository,
            ReavaliacaoRepository reavaliacaoRepository,
            EvolucaoSessaoRepository evolucaoRepository,
            ProntuarioPdfExporterService exporter,
            AuditorAware<String> auditor,
            @Value("${app.fuso-horario:America/Sao_Paulo}") String fusoHorario) {
        this(
                pacienteRepository,
                anamneseRepository,
                avaliacaoRepository,
                planoRepository,
                reavaliacaoRepository,
                evolucaoRepository,
                exporter,
                auditor,
                Clock.system(ZoneId.of(fusoHorario)));
    }

    ProntuarioService(
            PacienteRepository pacienteRepository,
            AnamneseRepository anamneseRepository,
            AvaliacaoFisioterapeuticaRepository avaliacaoRepository,
            PlanoTratamentoRepository planoRepository,
            ReavaliacaoRepository reavaliacaoRepository,
            EvolucaoSessaoRepository evolucaoRepository,
            ProntuarioPdfExporterService exporter,
            AuditorAware<String> auditor,
            Clock relogio) {
        this.auditor = auditor;
        this.relogio = relogio;
        this.pacienteRepository = pacienteRepository;
        this.anamneseRepository = anamneseRepository;
        this.avaliacaoRepository = avaliacaoRepository;
        this.planoRepository = planoRepository;
        this.reavaliacaoRepository = reavaliacaoRepository;
        this.evolucaoRepository = evolucaoRepository;
        this.exporter = exporter;
    }

    /** PDF pronto e o nome do arquivo que vai no {@code Content-Disposition}. */
    public record ArquivoPdf(byte[] conteudo, String nomeArquivo) {}

    /**
     * Gera o PDF dentro da transação de leitura: o exporter lê associações LAZY das entidades.
     *
     * @throws IllegalArgumentException {@code inicio} depois de {@code fim} (400)
     * @throws ResourceNotFoundException paciente inexistente (404)
     */
    @Transactional(readOnly = true)
    public ArquivoPdf exportarProntuario(Long pacienteId, LocalDate inicio, LocalDate fim) {
        if (inicio != null && fim != null && inicio.isAfter(fim)) {
            throw new IllegalArgumentException("Período inicial não pode ser maior que o período final");
        }
        Paciente paciente = pacienteRepository
                .findById(pacienteId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado: " + pacienteId));
        LocalDate de = inicio == null ? INICIO_ABERTO : inicio;
        LocalDate ate = fim == null ? FIM_ABERTO : fim;

        List<EvolucaoSessao> evolucoes = evolucaoRepository.findProntuario(pacienteId, de, ate);
        var prontuario = new ProntuarioPdfExporterService.Prontuario(
                paciente,
                inicio,
                fim,
                anamneseRepository.findByPacienteId(pacienteId).orElse(null),
                avaliacoesVigentes(avaliacaoRepository.findByPacienteAte(pacienteId, ate), inicio),
                planoRepository.findVigentesNoPeriodo(pacienteId, de, ate),
                reavaliacaoRepository.findByPacienteNoPeriodo(pacienteId, de, ate),
                evolucoes,
                LocalDateTime.now(relogio));
        byte[] pdf = exporter.exportarProntuario(prontuario);

        log.info(
                "Prontuário exportado: pacienteId={}, inicio={}, fim={}, evolucoes={}, por={}",
                pacienteId,
                inicio,
                fim,
                evolucoes.size(),
                usuarioAtual());
        return new ArquivoPdf(pdf, nomeArquivoProntuario(paciente.getNome(), inicio, fim));
    }

    /** @throws ResourceNotFoundException evolução inexistente (404) */
    @Transactional(readOnly = true)
    public ArquivoPdf exportarEvolucao(Long evolucaoId) {
        EvolucaoSessao evolucao = evolucaoRepository
                .findByIdComSessao(evolucaoId)
                .orElseThrow(() -> new ResourceNotFoundException("Evolução não encontrada: " + evolucaoId));
        Paciente paciente = evolucao.getSessao().getPaciente();
        byte[] pdf = exporter.exportarEvolucao(paciente, evolucao, LocalDateTime.now(relogio));

        log.info(
                "Evolução exportada: evolucaoId={}, pacienteId={}, por={}",
                evolucaoId,
                paciente.getId(),
                usuarioAtual());
        return new ArquivoPdf(
                pdf,
                "evolucao-" + slug(paciente.getNome()) + "-"
                        + evolucao.getSessao().getData() + ".pdf");
    }

    /**
     * A última avaliação anterior ao início ainda valia quando o período começou; as feitas dentro dele também
     * entram. Recebe as avaliações até o fim do período, em ordem cronológica.
     */
    static List<AvaliacaoFisioterapeutica> avaliacoesVigentes(
            List<AvaliacaoFisioterapeutica> ateOFim, LocalDate inicio) {
        if (inicio == null) {
            return ateOFim;
        }
        List<AvaliacaoFisioterapeutica> vigentes = new ArrayList<>();
        AvaliacaoFisioterapeutica anteriorAoInicio = null;
        for (AvaliacaoFisioterapeutica avaliacao : ateOFim) {
            if (avaliacao.getDataAvaliacao().isBefore(inicio)) {
                anteriorAoInicio = avaliacao;
            } else {
                vigentes.add(avaliacao);
            }
        }
        if (anteriorAoInicio != null) {
            vigentes.add(0, anteriorAoInicio);
        }
        return vigentes;
    }

    /**
     * {@code prontuario-<slug>-<inicio>-a-<fim>.pdf}; sem período, {@code -completo}; lado ausente vira {@code inicio}
     * ou {@code hoje} — contrato com o frontend (#250).
     */
    static String nomeArquivoProntuario(String nomePaciente, LocalDate inicio, LocalDate fim) {
        String periodo = inicio == null && fim == null
                ? "completo"
                : (inicio == null ? "inicio" : inicio.toString()) + "-a-" + (fim == null ? "hoje" : fim.toString());
        return "prontuario-" + slug(nomePaciente) + "-" + periodo + ".pdf";
    }

    /** Minúsculas sem acento, com hífen no lugar de tudo que não é letra ou dígito: "Ana Silva" → "ana-silva". */
    static String slug(String texto) {
        String semAcento = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug =
                semAcento.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "paciente" : slug;
    }

    private String usuarioAtual() {
        return LogMasker.email(auditor.getCurrentAuditor().orElse(UsuarioAuditorAware.SISTEMA));
    }
}
