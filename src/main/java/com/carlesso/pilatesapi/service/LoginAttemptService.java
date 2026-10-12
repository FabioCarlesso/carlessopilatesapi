package com.carlesso.pilatesapi.service;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Rate limit em memória dos endpoints públicos de autenticação, em dois baldes
 * independentes: um por chave (e-mail) e outro por origem (IP). O balde por origem
 * pega quem varia o e-mail a cada tentativa (credential stuffing); por isso tem
 * janela e limite próprios, mais folgados, para não punir vários usuários legítimos
 * atrás do mesmo NAT.
 *
 * <p>As tentativas são <em>reservadas</em> antes do trabalho caro (bcrypt, envio de
 * e-mail) com {@code tryAcquire*}, que checa e conta numa só operação atômica; quem
 * chama devolve a reserva com {@code release*}/{@link #registerSuccess} quando a
 * tentativa não deve contar.
 *
 * <p>O estado vive no heap desta instância: zera a cada restart e não é
 * compartilhado entre réplicas.
 */
@Service
public class LoginAttemptService {

    public static final int MAX_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final JanelaDeslizante porEmail;
    private final JanelaDeslizante porIp;

    @Autowired
    public LoginAttemptService(
            @Value("${app.rate-limit.ip.max-tentativas:20}") int maxAttemptsPorIp,
            @Value("${app.rate-limit.ip.janela-minutos:15}") long janelaPorIpMinutos) {
        this(maxAttemptsPorIp, janelaPorIpMinutos, Clock.systemUTC());
    }

    LoginAttemptService(int maxAttemptsPorIp, long janelaPorIpMinutos, Clock clock) {
        if (maxAttemptsPorIp < 1) {
            throw new IllegalArgumentException(
                    "app.rate-limit.ip.max-tentativas deve ser maior que zero: " + maxAttemptsPorIp);
        }
        if (janelaPorIpMinutos < 1) {
            throw new IllegalArgumentException(
                    "app.rate-limit.ip.janela-minutos deve ser maior que zero: " + janelaPorIpMinutos);
        }
        this.porEmail = new JanelaDeslizante(MAX_ATTEMPTS, WINDOW, clock);
        this.porIp = new JanelaDeslizante(maxAttemptsPorIp, Duration.ofMinutes(janelaPorIpMinutos), clock);
    }

    /** Reserva uma tentativa para a chave; {@code false} se ela já está bloqueada. */
    public boolean tryAcquire(String key) {
        return porEmail.tentarRegistrar(key);
    }

    /** Sucesso zera o histórico da chave. */
    public void registerSuccess(String key) {
        porEmail.remover(key);
    }

    public boolean isBlocked(String key) {
        return porEmail.atingiuLimite(key);
    }

    /** Reserva uma tentativa para a origem; {@code false} se ela já está bloqueada. */
    public boolean tryAcquireIp(String key) {
        return porIp.tentarRegistrar(key);
    }

    /**
     * Devolve só a reserva desta tentativa — nunca zera o balde: se um login bem-sucedido
     * apagasse o histórico do IP, o atacante intercalaria logins na própria conta para nunca
     * atingir o limite.
     */
    public void releaseIp(String key) {
        porIp.desfazer(key);
    }

    public boolean isIpBlocked(String key) {
        return porIp.atingiuLimite(key);
    }

    public Duration window() {
        return porEmail.janela();
    }

    public int maxAttemptsPorIp() {
        return porIp.limite();
    }

    public Duration windowPorIp() {
        return porIp.janela();
    }

    /** Chamado periodicamente pelo {@code LoginAttemptCleanupScheduler}. */
    public void limparEntradasExpiradas() {
        porEmail.limparExpirados();
        porIp.limparExpirados();
    }

    boolean contemChave(String key) {
        return porEmail.contem(key);
    }

    boolean contemChaveIp(String key) {
        return porIp.contem(key);
    }

    /** Zera os dois baldes; uso restrito a testes, que compartilham o bean entre si. */
    void limparTudo() {
        porEmail.limpar();
        porIp.limpar();
    }
}
