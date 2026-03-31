package com.ufla.autotarget.model;

import com.ufla.autotarget.engine.GameEngine;
import com.ufla.autotarget.exception.JogoException;

import java.util.List;

/**
 * Classe Canhão — opera como uma thread independente.
 * Cada canhão detecta automaticamente o alvo mais próximo na lista
 * compartilhada e dispara projéteis periodicamente.
 *
 * SINCRONIZAÇÃO: O acesso à lista de alvos (recurso compartilhado)
 * é feito através do GameEngine, que protege a região crítica
 * com blocos synchronized. Isso evita que o canhão leia a lista
 * enquanto outra thread a modifica (condição de corrida).
 *
 * POSTINVALIDATE: Após disparar um projétil ou atualizar o ângulo de mira,
 * a thread chama engine.requestRedraw() que invoca postInvalidate()
 * na GameView. A UI Thread então redesenha a tela no próximo ciclo.
 * O canhão NUNCA acessa o Canvas diretamente.
 */
public class Cannon extends Thread {

    // Posição fixa do canhão na tela
    private final float x;
    private final float y;

    // Ângulo atual de mira (em radianos, volatile para visibilidade na UI Thread)
    private volatile float angle;

    // Indica se o canhão está ativo
    private volatile boolean active;

    // Intervalo entre disparos em milissegundos
    private int fireRate;

    // Tamanho visual do canhão (para desenho do triângulo)
    private final float size;

    // Referência ao motor do jogo para acessar listas sincronizadas
    private final GameEngine engine;

    // Taxa base de disparo (sem penalidade)
    private static final int BASE_FIRE_RATE = 1000; // 1 disparo por segundo

    /**
     * @param x posição X do canhão
     * @param y posição Y do canhão
     * @param engine referência ao GameEngine para acessar alvos e adicionar projéteis
     */
    public Cannon(float x, float y, GameEngine engine) {
        this.x = x;
        this.y = y;
        this.angle = 0;
        this.active = true;
        this.fireRate = BASE_FIRE_RATE;
        this.size = 40f;
        this.engine = engine;
    }

    /**
     * Loop principal da thread do canhão.
     * Periodicamente busca o alvo mais próximo e dispara um projétil.
     *
     * SINCRONIZAÇÃO: findNearestTarget() acessa a lista de alvos
     * através do GameEngine com synchronized internamente.
     *
     * POSTINVALIDATE: Após atualizar o ângulo de mira e disparar,
     * chama engine.requestRedraw() para que a UI Thread redesenhe
     * o canhão com a nova orientação. Esta thread NUNCA toca no Canvas.
     */
    @Override
    public void run() {
        while (active && !isInterrupted()) {
            try {
                Target nearest = findNearestTarget();
                if (nearest != null && nearest.isActive()) {
                    fire(nearest);
                }

                // Solicita redesenho da tela via postInvalidate()
                // para refletir a mudança de ângulo e novo projétil
                engine.requestRedraw();

                Thread.sleep(fireRate);
            } catch (InterruptedException e) {
                active = false;
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Busca o alvo ativo mais próximo do canhão.
     *
     * REGIÃO CRÍTICA: O acesso à lista de alvos é sincronizado
     * pelo GameEngine (getTargetsSafe retorna uma cópia segura).
     * Enquanto a cópia é feita, nenhuma outra thread pode modificar
     * a lista original (adicionar ou remover alvos).
     *
     * @return o alvo mais próximo, ou null se não há alvos ativos
     */
    private Target findNearestTarget() {
        // Obtém uma cópia segura da lista de alvos (sincronizado internamente)
        List<Target> targets = engine.getTargetsSafe();

        Target nearest = null;
        float minDistance = Float.MAX_VALUE;

        for (Target target : targets) {
            if (target.isActive()) {
                float distance = target.getDistanceTo(x, y);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = target;
                }
            }
        }

        // Atualiza o ângulo de mira em direção ao alvo mais próximo
        if (nearest != null) {
            try {
                // TRATAMENTO DE EXCEÇÕES: Protege contra divisão por zero
                // no cálculo do atan2 (embora Math.atan2 já lide com isso)
                angle = (float) Math.atan2(nearest.getY() - y, nearest.getX() - x);
            } catch (Exception e) {
                // Fallback seguro: mantém ângulo anterior
                e.printStackTrace();
            }
        }

        return nearest;
    }

    /**
     * Cria e dispara um projétil em direção ao alvo.
     *
     * SINCRONIZAÇÃO: addProjectile() do GameEngine é sincronizado,
     * garantindo adição atômica à lista de projéteis.
     * Múltiplos canhões podem disparar simultaneamente sem conflito.
     *
     * TRATAMENTO DE EXCEÇÕES: try-catch para capturar JogoException
     * ao adicionar o projétil, evitando crash da thread do canhão.
     *
     * @param target o alvo para mirar
     */
    private void fire(Target target) {
        try {
            float dx = target.getX() - x;
            float dy = target.getY() - y;
            float magnitude = (float) Math.sqrt(dx * dx + dy * dy);

            // TRATAMENTO: Evita divisão por zero se o alvo está na mesma posição
            if (magnitude == 0) return;

            // Normaliza a direção do projétil
            float dirX = dx / magnitude;
            float dirY = dy / magnitude;

            Projectile projectile = new Projectile(x, y, dirX, dirY, 12f, engine);

            engine.addProjectile(projectile);
            projectile.start();

            // Registra o disparo no log visual do HUD
            String tipoAlvo = (target instanceof FastTarget) ? "Rápido" : "Comum";
            engine.addActionLog("Canhão disparou → Alvo " + tipoAlvo);
        } catch (JogoException e) {
            // TRATAMENTO DE EXCEÇÕES: Captura erro ao adicionar projétil
            // (ex: limite excedido, recurso indisponível)
            // A thread do canhão continua funcionando mesmo com erro no disparo
            e.printStackTrace();
        } catch (ArithmeticException e) {
            // TRATAMENTO: Captura possíveis exceções matemáticas
            e.printStackTrace();
        }
    }

    /**
     * Aplica penalidade de taxa de disparo.
     * Canhões além do limite base (5) sofrem penalidade proporcional
     * no intervalo entre disparos (fireRate aumenta).
     *
     * @param penaltyMultiplier multiplicador de penalidade (1.0 = sem penalidade)
     */
    public void applyFireRatePenalty(float penaltyMultiplier) {
        this.fireRate = (int) (BASE_FIRE_RATE * penaltyMultiplier);
    }

    // ==================== Getters ====================

    public float getCannonX() { return x; }
    public float getCannonY() { return y; }
    public float getAngle() { return angle; }
    public float getSize() { return size; }
    public boolean isActive() { return active; }

    public void setActive(boolean active) { this.active = active; }
}
