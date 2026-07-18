package com.carlesso.pilatesapi.storage;

import com.carlesso.pilatesapi.entity.AvaliacaoPosturalFoto;
import com.carlesso.pilatesapi.repository.AvaliacaoPosturalFotoRepository;
import com.carlesso.pilatesapi.repository.AvaliacaoPosturalRepository;
import java.time.LocalDateTime;
import java.util.Optional;

public class PostgresFotoStorage implements FotoStorage {

    private final AvaliacaoPosturalFotoRepository fotoRepository;
    private final AvaliacaoPosturalRepository avaliacaoPosturalRepository;

    public PostgresFotoStorage(
            AvaliacaoPosturalFotoRepository fotoRepository, AvaliacaoPosturalRepository avaliacaoPosturalRepository) {
        this.fotoRepository = fotoRepository;
        this.avaliacaoPosturalRepository = avaliacaoPosturalRepository;
    }

    @Override
    public FotoArmazenada salvar(
            Long avaliacaoPosturalId, byte[] conteudo, String contentType, int larguraPx, int alturaPx) {
        AvaliacaoPosturalFoto foto = fotoRepository
                .findByAvaliacaoPosturalId(avaliacaoPosturalId)
                .orElseGet(() -> {
                    AvaliacaoPosturalFoto nova = new AvaliacaoPosturalFoto();
                    nova.setAvaliacaoPostural(avaliacaoPosturalRepository.getReferenceById(avaliacaoPosturalId));
                    return nova;
                });

        foto.setConteudo(conteudo);
        foto.setContentType(contentType);
        foto.setTamanhoBytes(conteudo.length);
        foto.setLarguraPx(larguraPx);
        foto.setAlturaPx(alturaPx);
        // Explícita (sem @PrePersist) para valer também na substituição da foto e
        // já estar preenchida na resposta antes do flush.
        foto.setDataCriacao(LocalDateTime.now());

        return daEntidade(fotoRepository.save(foto));
    }

    @Override
    public Optional<FotoArmazenada> recuperar(Long avaliacaoPosturalId) {
        return fotoRepository.findByAvaliacaoPosturalId(avaliacaoPosturalId).map(PostgresFotoStorage::daEntidade);
    }

    private static FotoArmazenada daEntidade(AvaliacaoPosturalFoto foto) {
        return new FotoArmazenada(
                foto.getConteudo(),
                foto.getContentType(),
                foto.getTamanhoBytes(),
                foto.getLarguraPx(),
                foto.getAlturaPx(),
                foto.getDataCriacao());
    }
}
