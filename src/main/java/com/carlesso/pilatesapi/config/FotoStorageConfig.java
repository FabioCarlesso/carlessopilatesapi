package com.carlesso.pilatesapi.config;

import com.carlesso.pilatesapi.repository.AvaliacaoPosturalFotoRepository;
import com.carlesso.pilatesapi.repository.AvaliacaoPosturalRepository;
import com.carlesso.pilatesapi.storage.FotoStorage;
import com.carlesso.pilatesapi.storage.PostgresFotoStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FotoStorageConfig {

    @Bean
    @ConditionalOnProperty(name = "app.foto.storage", havingValue = "postgres", matchIfMissing = true)
    public FotoStorage postgresFotoStorage(
            AvaliacaoPosturalFotoRepository fotoRepository, AvaliacaoPosturalRepository avaliacaoPosturalRepository) {
        return new PostgresFotoStorage(fotoRepository, avaliacaoPosturalRepository);
    }
}
