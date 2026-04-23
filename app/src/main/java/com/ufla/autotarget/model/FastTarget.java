package com.ufla.autotarget.model;

/**
 * Alvo rápido — movimentação acelerada e errática.
 * HERANÇA: Estende Target (que estende Thread).
 * POLIMORFISMO: Sobrescreve move() com comportamento distinto do CommonTarget.
 *
 * O alvo rápido se move com o dobro da velocidade base e muda
 * de direção com muito mais frequência, tornando-o mais difícil de atingir.
 * Vale mais pontos por ser mais desafiador.
 */
public class FastTarget extends Target {

    // Contador para mudanças de direção frequentes
    private int moveCounter;

    // Multiplicador de velocidade em relação ao alvo comum
    private static final float SPEED_MULTIPLIER = 2.0f;

    public FastTarget(float x, float y, float radius, float speed,
                      int screenWidth, int screenHeight) {
        super(x, y, radius, speed * SPEED_MULTIPLIER, screenWidth, screenHeight);
        this.moveCounter = 0;
        this.scoreValue = 3; // Alvo rápido vale 3 pontos (mais difícil)
    }

    /**
     * POLIMORFISMO: Implementação de move() para alvo rápido.
     * Movimentação errática com mudanças de direção frequentes (~0.5 segundo).
     * Usa velocidade dobrada e perturbações maiores no ângulo, criando
     * um padrão de zigue-zague imprevisível.
     */
    @Override
    public void move() {
        moveCounter++;

        // A cada ~30 frames (~0.5 segundo), muda bruscamente de direção
        // Movimentação deliberadamente errática e difícil de prever
        if (moveCounter % 30 == 0) {
            float perturbation = (random.nextFloat() - 0.5f) * 1.5f;
            directionX += perturbation;
            directionY += (random.nextFloat() - 0.5f) * 1.5f;

            // Normaliza para manter velocidade consistente mesmo com mudanças bruscas
            float magnitude = (float) Math.sqrt(directionX * directionX + directionY * directionY);
            if (magnitude > 0) {
                directionX /= magnitude;
                directionY /= magnitude;
            }
        }

        // Atualiza posição com velocidade multiplicada
        x += directionX * speed;
        y += directionY * speed;

        // Rebote nas bordas
        bounceOffWalls();
    }

    /** Cor vermelha — identidade visual do alvo rápido (perigo). */
    @Override
    public int getColor() { return 0xFFEF5350; }

    /** Glow vermelho com alpha reduzido. */
    @Override
    public int getGlowColor() { return 0x40EF5350; }

    /** Nome descritivo para logs e HUD. */
    @Override
    public String getTypeName() { return "Rápido"; }
}
