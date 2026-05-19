package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.PreferenciasUsuarioRequestDTO;
import com.carlesso.pilatesapi.dto.PreferenciasUsuarioResponseDTO;
import com.carlesso.pilatesapi.entity.PreferenciasUsuario;
import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.entity.enums.IdiomaPreferencia;
import com.carlesso.pilatesapi.entity.enums.TemaPreferencia;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.PreferenciasUsuarioRepository;
import com.carlesso.pilatesapi.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class PreferenciasUsuarioService {

    public static final IdiomaPreferencia IDIOMA_PADRAO = IdiomaPreferencia.PT_BR;
    public static final TemaPreferencia TEMA_PADRAO = TemaPreferencia.CLARO;
    public static final boolean NOTIFICACOES_EMAIL_PADRAO = true;
    public static final boolean NOTIFICACOES_PUSH_PADRAO = false;

    private final PreferenciasUsuarioRepository repository;
    private final UserRepository userRepository;

    public PreferenciasUsuarioService(PreferenciasUsuarioRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PreferenciasUsuarioResponseDTO buscarPorEmail(String email) {
        User user = encontrarUsuario(email);
        return repository.findByUserId(user.getId())
                .map(PreferenciasUsuarioResponseDTO::from)
                .orElseGet(() -> padraoResponse());
    }

    @Transactional
    public PreferenciasUsuarioResponseDTO atualizarPorEmail(String email, PreferenciasUsuarioRequestDTO dto) {
        User user = encontrarUsuario(email);
        PreferenciasUsuario preferencias = repository.findByUserId(user.getId())
                .orElseGet(() -> novaComPadroes(user));

        preferencias.setIdioma(dto.idioma());
        preferencias.setTema(dto.tema());
        preferencias.setNotificacoesEmail(Boolean.TRUE.equals(dto.notificacoesEmail()));
        preferencias.setNotificacoesPush(Boolean.TRUE.equals(dto.notificacoesPush()));

        return PreferenciasUsuarioResponseDTO.from(repository.save(preferencias));
    }

    private User encontrarUsuario(String email) {
        return userRepository.findByEmail(email.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    private PreferenciasUsuario novaComPadroes(User user) {
        PreferenciasUsuario preferencias = new PreferenciasUsuario();
        preferencias.setUser(user);
        preferencias.setIdioma(IDIOMA_PADRAO);
        preferencias.setTema(TEMA_PADRAO);
        preferencias.setNotificacoesEmail(NOTIFICACOES_EMAIL_PADRAO);
        preferencias.setNotificacoesPush(NOTIFICACOES_PUSH_PADRAO);
        return preferencias;
    }

    private PreferenciasUsuarioResponseDTO padraoResponse() {
        return new PreferenciasUsuarioResponseDTO(
                IDIOMA_PADRAO,
                TEMA_PADRAO,
                NOTIFICACOES_EMAIL_PADRAO,
                NOTIFICACOES_PUSH_PADRAO
        );
    }
}
