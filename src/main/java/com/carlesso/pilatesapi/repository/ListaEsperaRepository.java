package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.ListaEspera;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListaEsperaRepository extends JpaRepository<ListaEspera, Long> {

    /**
     * Fila ativa, na ordem de chegada, com os filtros opcionais aplicados no banco.
     *
     * <p>A faixa horária casa por <strong>interseção</strong>, e não por igualdade: quem se inscreveu para 07:30–09:30
     * precisa aparecer na busca por 08:00–09:00, senão a recepção só encontraria quem digitou exatamente a mesma
     * faixa. As pontas são abertas ({@code horaFim > :horaInicio AND horaInicio < :horaFim}) — uma inscrição que
     * termina 08:00 não disputa a vaga que começa 08:00.
     *
     * <p>O recorte por {@code pac.ativo} segue as consultas de {@code AulaRepository}: paciente inativado é ex-aluno e
     * não deve ser chamado para uma vaga (issues #25 e #152).
     *
     * <p>O {@code CAST} nos testes de nulo das horas não é decorativo: sem ele o driver manda um parâmetro sem tipo em
     * {@code ? IS NULL} e o Postgres recusa a consulta com {@code could not determine data type of parameter}. Os
     * filtros de enum e de id escapam disso porque o Hibernate já os tipa.
     */
    @Query(
            """
            SELECT l FROM ListaEspera l
            JOIN FETCH l.paciente pac
            WHERE l.ativo = true
              AND pac.ativo = true
              AND (:diaSemana IS NULL OR l.diaSemana = :diaSemana)
              AND (:pacienteId IS NULL OR pac.id = :pacienteId)
              AND (CAST(:horaInicio AS LocalTime) IS NULL OR l.horaFim > :horaInicio)
              AND (CAST(:horaFim AS LocalTime) IS NULL OR l.horaInicio < :horaFim)
            ORDER BY l.dataEntrada, l.id
            """)
    List<ListaEspera> buscarAtivas(
            @Param("diaSemana") DayOfWeek diaSemana,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFim") LocalTime horaFim,
            @Param("pacienteId") Long pacienteId);

    /**
     * Entrada ainda na fila.
     *
     * <p>Diferente de {@link #buscarAtivas}, não recorta por {@code paciente.ativo}: inativar o paciente some com a
     * entrada da listagem, e recusar o {@code DELETE} depois disso deixaria a linha viva sem nenhuma rota capaz de
     * encerrá-la.
     */
    @Query("SELECT l FROM ListaEspera l JOIN FETCH l.paciente WHERE l.id = :id AND l.ativo = true")
    Optional<ListaEspera> findAtivaById(@Param("id") Long id);

    /** Duplicidade é a mesma faixa exata, não a interseção: 08:00–09:00 e 08:30–09:30 são interesses distintos. */
    @Query(
            """
            SELECT COUNT(l) > 0 FROM ListaEspera l
            WHERE l.ativo = true
              AND l.paciente.id = :pacienteId
              AND l.diaSemana = :diaSemana
              AND l.horaInicio = :horaInicio
              AND l.horaFim = :horaFim
            """)
    boolean existeAtivaParaPacienteNaFaixa(
            @Param("pacienteId") Long pacienteId,
            @Param("diaSemana") DayOfWeek diaSemana,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFim") LocalTime horaFim);
}
