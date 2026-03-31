package com.ufla.autotarget.model;

import com.ufla.autotarget.engine.GameEngine;

import java.util.List;

/**
 * Classe Projétil — opera como uma thread independente.
 * Move-se em linha reta a partir do canhão que o disparou
 * e verifica colisão com os alvos a cada frame.
 *
 * SINCRONIZAÇÃO: A verificação de colisão acessa a lista de alvos
 * (recurso compartilhado) através do GameEngine. O método
 * checkProjectileCollision() usa synchronized(targetLock) para
 * garantir que apenas UM projétil por vez verifica colisão com
 * um alvo — região crítica que previne condições de corrida.
 *
 * POSTINVALIDATE: Após cada atualização de posição, a thread chama
 * engine.requestRedraw() que invoca gameView.postInvalidate().
 * A UI Thread então redesenha a tela com a nova posição do projétil.
 * Esta thread NUNCA acessa o Canvas diretamente.
 */
public class Projectile extends Thread {

    // Posição atual do projétil (volatile para visibilidade na UI Thread)
    private volatile float x;
    private volatile float y;

    // Direção normalizada do movimento (imutável após criação)
    private final float directionX;
    private final float directionY;

    // Velocidade de deslocamento por frame
    private final float speed;

    // Indica se o projétil ainda está ativo (em voo)
    private volatile boolean active;

    // Raio do projétil para exibição visual e detecção de colisão
    private final float radius;

    // Referência ao motor do jogo para verificar colisões e solicitar redesenho
    private final GameEngine engine;

    // Limites da tela — projétil é desativado ao sair
    private int screenWidth = 2000;
    private int screenHeight = 3000;

    /**
     * @param x posição inicial X (posição do canhão que disparou)
     * @param y posição inicial Y (posição do canhão que disparou)
     * @param directionX componente X da direção normalizada
     * @param directionY componente Y da direção normalizada
     * @param speed velocidade do projétil
     * @param engine referência ao GameEngine para colisões e postInvalidate
     */
    public Projectile(float x, float y, float directionX, float directionY,
                      float speed, GameEngine engine) {
        this.x = x;
        this.y = y;
        this.directionX = directionX;
        this.directionY = directionY;
        this.speed = speed;
        this.active = true;
        this.radius = 6f;
        this.engine = engine;
    }

    /**
     * Loop principal da thread do projétil.
     * Move-se em linha reta e verifica colisão a cada frame.
     *
     * POSTINVALIDATE: Após mover e verificar colisões, chama
     * engine.requestRedraw() → gameView.postInvalidate().
     * A UI Thread redesenha a tela com a posição atualizada.
     * Esta thread NUNCA acessa o Canvas/View diretamente.
     *
     * SINCRONIZAÇÃO: checkCollisions() delega ao GameEngine que
     * usa synchronized(targetLock) — REGIÃO CRÍTICA que garante
     * que apenas um projétil por vez modifica o estado dos alvos.
     */
    @Override
    public void run() {
        while (active && !isInterrupted()) {
            try {
                // Move o projétil na direção definida no momento do disparo
                x += directionX * speed;
                y += directionY * speed;

                // Verifica se saiu dos limites da tela → desativa
                if (x < -50 || x > screenWidth + 50 || y < -50 || y > screenHeight + 50) {
                    active = false;
                    break;
                }

                // Verifica colisão com alvos (REGIÃO CRÍTICA via GameEngine)
                checkCollisions();

                // Solicita redesenho da tela via postInvalidate()
                // para mostrar a nova posição do projétil
                if (engine != null) {
                    engine.requestRedraw();
                }

                Thread.sleep(16); // ~60 FPS
            } catch (InterruptedException e) {
                active = false;
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Delega a verificação de colisão ao GameEngine.
     *
     * REGIÃO CRÍTICA: O GameEngine usa synchronized(targetLock) em
     * checkProjectileCollision() para garantir que apenas UM projétil
     * por vez possa verificar/modificar o estado dos alvos.
     *
     * Sem esta proteção, dois projéteis poderiam simultaneamente:
     * 1. Ler que o mesmo alvo está ativo (ambos passam no if)
     * 2. Desativar o alvo e incrementar o score (score contado 2x)
     *
     * O synchronized garante EXCLUSÃO MÚTUA nesta operação.
     */
    private void checkCollisions() {
        if (engine != null) {
            engine.checkProjectileCollision(this);
        }
    }

    /**
     * Verifica se este projétil colide com um alvo específico.
     * Usa a distância euclidiana entre os centros, comparando
     * com a soma dos raios (circle-circle collision).
     *
     * Fórmula: colisão se distância(centros) <= raio_proj + raio_alvo
     *
     * @param target alvo a verificar
     * @return true se há colisão
     */
    public boolean checkCollision(Target target) {
        if (target == null || !target.isActive()) return false;

        try {
            float dx = this.x - target.getX();
            float dy = this.y - target.getY();
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            // Colisão ocorre quando a distância é menor ou igual à soma dos raios
            return distance <= (this.radius + target.getRadius());
        } catch (Exception e) {
            // TRATAMENTO DE EXCEÇÕES: Protege contra erros matemáticos inesperados
            e.printStackTrace();
            return false;
        }
    }

    // ==================== Getters ====================

    public float getProjectileX() { return x; }
    public float getProjectileY() { return y; }
    public float getRadius() { return radius; }
    public boolean isActive() { return active; }

    public void setActive(boolean active) { this.active = active; }
    public void setScreenWidth(int w) { this.screenWidth = w; }
    public void setScreenHeight(int h) { this.screenHeight = h; }
}
