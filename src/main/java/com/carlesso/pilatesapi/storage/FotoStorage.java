package com.carlesso.pilatesapi.storage;

import java.util.Optional;

/**
 * Acesso ao binário da foto da análise postural, no mesmo padrão do
 * {@code EmailSender}: o MVP usa PostgreSQL ({@link PostgresFotoStorage});
 * a evolução prevista em docs/simetrografo-virtual.md é uma implementação em
 * object storage (Cloudflare R2) sem alterar o serviço.
 */
public interface FotoStorage {

    /** Grava (ou substitui) a foto da análise e devolve os metadados persistidos. */
    FotoArmazenada salvar(Long avaliacaoPosturalId, byte[] conteudo, String contentType, int larguraPx, int alturaPx);

    Optional<FotoArmazenada> recuperar(Long avaliacaoPosturalId);
}
