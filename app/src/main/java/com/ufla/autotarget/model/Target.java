package com.ufla.autotarget.model;

import com.ufla.autotarget.engine.GameEngine;

import java.util.Random;

/**
 * Classe abstrata que representa um alvo no jogo.
 * Estende Thread para que cada alvo tenha seu próprio ciclo de vida independente.
 *
 * SINCRONIZAÇÃO: A posição (x, y) é acessada tanto pela thread do alvo (escrita)
 * quanto pela UI Thread no onDraw() (leitura) e pelos projéteis (leitura).
 * Usamos volatile para garantir visibilidade imediata entre threads.
 *
 * POSTINVALIDATE: Após atualizar a posição no move(), a thread chama
 * engine.requestRedraw() que invoca gameView.postInvalidate(). Isso faz
 * com que a UI Thread enfileire uma chamada a onDraw(), redesenhando a tela
 * com a nova posição do alvo — sem que esta thread toque no Canvas.
 */
public abstract class Target extends Thread {

    // Posição do alvo na tela (volatile para visibilidade entre threads)
    protected volatile float x;
    protected volatile float y;

    // Raio do círculo que representa o alvo
    protected float radius;

    // Velocidade base de movimentação
    protected float speed;

    // Direção do movimento (componentes x e y)
    protected float directionX;
    protected float directionY;

    // Indica se o alvo está ativo (vivo) no jogo
    protected volatile boolean active;

    // Limites da tela para rebote
    protected int screenWidth;
    protected int screenHeight;

    // Gerador de números aleatórios para movimentação pseudoaleatória
    protected final Random random;

    // Pontuação que o alvo vale ao ser destruído
    protected int scoreValue;

    // Referência ao motor do jogo para chamar requestRedraw() (postInvalidate)
    // Pode ser null em testes unitários — nesse caso, nenhum redesenho é solicitado
    protected GameEngine engine;

    /**
     * Construtor do alvo.
     * @param x posição inicial X
     * @param y posição inicial Y
     * @param radius raio do alvo
     * @param speed velocidade de movimentação
     * @param screenWidth largura da tela
     * @param screenHeight altura da tela
     */
    public Target(float x, float y, float radius, float speed, int screenWidth, int screenHeight) {
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.speed = speed;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.active = true;
        this.random = new Random();
        this.scoreValue = 1;

        // Direção inicial aleatória normalizada
        float angle = (float) (random.nextFloat() * 2 * Math.PI);
        this.directionX = (float) Math.cos(angle);
        this.directionY = (float) Math.sin(angle);
    }

    /**
     * Loop principal da thread do alvo.
     * Enquanto o alvo estiver ativo, chama move() e solicita redesenho.
     *
     * POSTINVALIDATE: Após cada chamada a move(), esta thread NÃO acessa
     * o Canvas diretamente. Em vez disso, chama engine.requestRedraw()
     * que invoca postInvalidate() na GameView. A UI Thread então
     * processará o pedido e chamará onDraw() com segurança.
     *
     * O sleep(16ms) simula ~60 FPS e libera o processador para outras threads,
     * implementando um sistema de tempo real com taxa de atualização controlada.
     */
    @Override
    public void run() {
        while (active && !isInterrupted()) {
            try {
                // Atualiza posição — implementação polimórfica (move() das subclasses)
                move();

                // Solicita que a UI Thread redesenhe a tela (postInvalidate)
                // A thread secundária NUNCA acessa o Canvas diretamente
                if (engine != null) {
                    engine.requestRedraw();
                }

                Thread.sleep(16); // ~60 FPS — intervalo entre atualizações
            } catch (InterruptedException e) {
                // Thread interrompida externamente — encerrar graciosamente
                active = false;
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Método abstrato de movimentação — implementado pelas subclasses.
     * Demonstra POLIMORFISMO: CommonTarget e FastTarget sobrescrevem
     * com comportamentos diferentes de movimentação.
     *
     * Quando o GameEngine percorre a lista de alvos, cada chamada
     * a target.move() é resolvida em tempo de execução (late binding)
     * para o tipo concreto correto.
     */
    public abstract void move();

    /**
     * Calcula a distância euclidiana entre este alvo e um ponto (px, py).
     * Usado pelos canhões para encontrar o alvo mais próximo.
     *
     * Fórmula: d = sqrt((x2-x1)² + (y2-y1)²)
     *
     * @param px coordenada X do ponto
     * @param py coordenada Y do ponto
     * @return distância euclidiana
     */
    public float getDistanceTo(float px, float py) {
        float dx = this.x - px;
        float dy = this.y - py;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Verifica se um ponto está dentro do raio deste alvo (colisão).
     * @param px coordenada X do ponto
     * @param py coordenada Y do ponto
     * @return true se o ponto colide com o alvo
     */
    public boolean isHit(float px, float py) {
        return getDistanceTo(px, py) <= radius;
    }

    /**
     * Aplica rebote nas bordas da tela para manter o alvo visível.
     * Quando o alvo atinge uma borda, a componente de direção
     * correspondente é invertida, simulando reflexão.
     */
    protected void bounceOffWalls() {
        if (x - radius <= 0) {
            x = radius;
            directionX = Math.abs(directionX);
        } else if (x + radius >= screenWidth) {
            x = screenWidth - radius;
            directionX = -Math.abs(directionX);
        }

        if (y - radius <= 0) {
            y = radius;
            directionY = Math.abs(directionY);
        } else if (y + radius >= screenHeight) {
            y = screenHeight - radius;
            directionY = -Math.abs(directionY);
        }
    }

    // ==================== Getters e Setters ====================

    public float getX() { return x; }
    public float getY() { return y; }
    public float getRadius() { return radius; }
    public float getSpeed() { return speed; }
    public boolean isActive() { return active; }
    public int getScoreValue() { return scoreValue; }

    public void setActive(boolean active) { this.active = active; }
    public void setScreenWidth(int screenWidth) { this.screenWidth = screenWidth; }
    public void setScreenHeight(int screenHeight) { this.screenHeight = screenHeight; }

    /**
     * Define a referência ao GameEngine para solicitar redesenhos via postInvalidate().
     * @param engine o motor do jogo
     */
    public void setEngine(GameEngine engine) { this.engine = engine; }
}
