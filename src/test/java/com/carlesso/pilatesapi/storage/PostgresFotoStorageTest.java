package com.carlesso.pilatesapi.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import com.carlesso.pilatesapi.entity.AvaliacaoPostural;
import com.carlesso.pilatesapi.entity.AvaliacaoPosturalFoto;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.enums.VistaPostural;
import com.carlesso.pilatesapi.repository.AvaliacaoPosturalFotoRepository;
import com.carlesso.pilatesapi.repository.AvaliacaoPosturalRepository;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@PostgresDataJpaTest
class PostgresFotoStorageTest extends PostgresTestcontainerSupport {

    @Autowired
    private AvaliacaoPosturalFotoRepository fotoRepository;

    @Autowired
    private AvaliacaoPosturalRepository avaliacaoPosturalRepository;

    @Autowired
    private TestEntityManager entityManager;

    private PostgresFotoStorage storage;

    private AvaliacaoPostural analise;

    @BeforeEach
    void setUp() {
        storage = new PostgresFotoStorage(fotoRepository, avaliacaoPosturalRepository);

        Paciente paciente = new Paciente();
        paciente.setNome("Ana Souza");
        paciente = entityManager.persist(paciente);

        AvaliacaoFisioterapeutica avaliacao = new AvaliacaoFisioterapeutica();
        avaliacao.setPaciente(paciente);
        avaliacao.setDataAvaliacao(LocalDate.of(2026, 7, 1));
        avaliacao.setQueixaFuncional("Dor ao agachar");
        avaliacao.setEscalaDor(6);
        avaliacao.setDiagnosticoFisioterapeutico("Disfunção lombopélvica");
        avaliacao = entityManager.persist(avaliacao);

        analise = new AvaliacaoPostural();
        analise.setAvaliacaoFisioterapeutica(avaliacao);
        analise.setVista(VistaPostural.FRENTE);
        analise = entityManager.persist(analise);
        entityManager.flush();
    }

    @Test
    void salvarERecuperar_devePersistirEReleroByteaSemPerda() {
        byte[] conteudo = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x10, 0x20, 0x30};

        FotoArmazenada salva = storage.salvar(analise.getId(), conteudo, "image/jpeg", 1080, 1440);
        entityManager.flush();
        entityManager.clear();

        assertThat(salva.tamanhoBytes()).isEqualTo(conteudo.length);
        assertThat(salva.dataCriacao()).isNotNull();

        FotoArmazenada relida = storage.recuperar(analise.getId()).orElseThrow();
        assertThat(relida.conteudo()).isEqualTo(conteudo);
        assertThat(relida.contentType()).isEqualTo("image/jpeg");
        assertThat(relida.tamanhoBytes()).isEqualTo(conteudo.length);
        assertThat(relida.larguraPx()).isEqualTo(1080);
        assertThat(relida.alturaPx()).isEqualTo(1440);
    }

    @Test
    void salvar_comFotoExistente_deveSubstituirSemCriarSegundaLinha() {
        storage.salvar(analise.getId(), new byte[] {1, 2, 3}, "image/jpeg", 1080, 1440);
        entityManager.flush();

        storage.salvar(analise.getId(), new byte[] {4, 5}, "image/png", 800, 600);
        entityManager.flush();
        entityManager.clear();

        assertThat(fotoRepository.count()).isEqualTo(1);
        FotoArmazenada relida = storage.recuperar(analise.getId()).orElseThrow();
        assertThat(relida.conteudo()).isEqualTo(new byte[] {4, 5});
        assertThat(relida.contentType()).isEqualTo("image/png");
        assertThat(relida.tamanhoBytes()).isEqualTo(2);
    }

    @Test
    void recuperar_semFoto_deveDevolverVazio() {
        assertThat(storage.recuperar(analise.getId())).isEmpty();
    }

    @Test
    void constraintUnica_impedeSegundaFotoParaAMesmaAnalise() {
        storage.salvar(analise.getId(), new byte[] {1}, "image/jpeg", 100, 100);
        entityManager.flush();

        AvaliacaoPosturalFoto duplicada = new AvaliacaoPosturalFoto();
        duplicada.setAvaliacaoPostural(analise);
        duplicada.setConteudo(new byte[] {2});
        duplicada.setContentType("image/png");
        duplicada.setTamanhoBytes(1);
        duplicada.setLarguraPx(10);
        duplicada.setAlturaPx(10);
        duplicada.setDataCriacao(LocalDateTime.now());

        assertThatThrownBy(() -> fotoRepository.saveAndFlush(duplicada))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
