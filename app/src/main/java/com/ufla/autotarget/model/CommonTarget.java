package com.ufla.autotarget.model;

/**
 * Alvo comum — movimentação lenta e previsível.
 * HERANÇA: Estende Target (que estende Thread).
 * POLIMORFISMO: Sobrescreve move() com comportamento de movimentação suave.
 *
 * O alvo comum se move em linha reta com rebote nas bordas,
 * mudando ligeiramente de direção a cada intervalo para criar
 * um padrão pseudoaleatório.
 */
public class CommonTarget extends Target {

    // Contador para mudanças de direção periódicas
    private int moveCounter;

    public CommonTarget(float x, float y, float radius, float speed,
                        int screenWidth, int screenHeight) {
        super(x, y, radius, speed, screenWidth, screenHeight);
        this.moveCounter = 0;
        this.scoreValue = 1; // Alvo comum vale 1 ponto
    }

    /**
     * POLIMORFISMO: Implementação de move() para alvo comum.
     * Movimentação linear com mudanças de direção suaves a cada ~2 segundos.
     * Velocidade constante e rebote simples nas bordas da tela.
     */
    @Override
    public void move() {
        moveCounter++;

        // A cada ~120 frames (~2 segundos), muda ligeiramente a direção
        // Simula movimentação pseudoaleatória mantendo previsibilidade
        if (moveCounter % 120 == 0) {
            float perturbation = (random.nextFloat() - 0.5f) * 0.5f;
            directionX += perturbation;
            directionY += perturbation;

            // Normaliza o vetor de direção para manter velocidade constante
            float magnitude = (float) Math.sqrt(directionX * directionX + directionY * directionY);
            if (magnitude > 0) {
                directionX /= magnitude;
                directionY /= magnitude;
            }
        }

        // Atualiza posição com a velocidade base
        x += directionX * speed;
        y += directionY * speed;

        // Rebote nas bordas
        bounceOffWalls();
    }

    /** Cor ciano — identidade visual do alvo comum. */
    @Override
    public int getColor() { return 0xFF4FC3F7; }

    /** Glow ciano com alpha reduzido. */
    @Override
    public int getGlowColor() { return 0x404FC3F7; }

    /** Nome descritivo para logs e HUD. */
    @Override
    public String getTypeName() { return "Comum"; }
}
