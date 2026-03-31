package com.ufla.autotarget;

import com.ufla.autotarget.model.CommonTarget;
import com.ufla.autotarget.model.FastTarget;
import com.ufla.autotarget.model.Target;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Testes unitários para o cálculo de distância euclidiana entre alvos e pontos.
 * Verifica o método getDistanceTo() da classe Target, que é fundamental para
 * o canhão encontrar o alvo mais próximo.
 *
 * Conceitos testados:
 * - Cálculo correto da distância euclidiana (d = sqrt(dx² + dy²))
 * - Distância zero (ponto no centro do alvo)
 * - Distância com coordenadas negativas e positivas
 * - POLIMORFISMO: Testa em ambas as subclasses (CommonTarget e FastTarget)
 */
public class DistanceCalculationTest {

    private static final float DELTA = 0.01f; // Tolerância para comparação de floats

    /**
     * Testa distância entre um alvo na origem e um ponto em (3, 4).
     * Resultado esperado: sqrt(3² + 4²) = sqrt(9 + 16) = sqrt(25) = 5.0
     * É o caso clássico do triângulo retângulo 3-4-5.
     */
    @Test
    public void testDistanceFromOriginTo3_4() {
        Target target = new CommonTarget(0, 0, 20, 1, 800, 600);
        float distance = target.getDistanceTo(3, 4);
        assertEquals("Distância de (0,0) a (3,4) deve ser 5.0", 5.0f, distance, DELTA);
    }

    /**
     * Testa distância zero — quando o ponto está exatamente na posição do alvo.
     * Resultado esperado: 0.0
     */
    @Test
    public void testDistanceToSamePoint() {
        Target target = new CommonTarget(100, 200, 20, 1, 800, 600);
        float distance = target.getDistanceTo(100, 200);
        assertEquals("Distância para o mesmo ponto deve ser 0", 0.0f, distance, DELTA);
    }

    /**
     * Testa distância com coordenadas maiores.
     * Alvo em (100, 100) e ponto em (400, 500).
     * dx = 300, dy = 400 → sqrt(90000 + 160000) = sqrt(250000) = 500.0
     */
    @Test
    public void testDistanceLargeCoordinates() {
        Target target = new CommonTarget(100, 100, 30, 2, 800, 600);
        float distance = target.getDistanceTo(400, 500);
        assertEquals("Distância de (100,100) a (400,500) deve ser 500.0", 500.0f, distance, DELTA);
    }

    /**
     * Testa que getDistanceTo funciona corretamente com FastTarget (POLIMORFISMO).
     * Garante que a herança não afeta o cálculo de distância.
     */
    @Test
    public void testDistancePolymorphismFastTarget() {
        Target fastTarget = new FastTarget(50, 50, 15, 3, 800, 600);
        float distance = fastTarget.getDistanceTo(50, 100);
        assertEquals("Distância vertical de 50 pixels", 50.0f, distance, DELTA);
    }

    /**
     * Testa distância horizontal pura (dy = 0).
     * Alvo em (0, 0), ponto em (100, 0). Resultado: 100.0
     */
    @Test
    public void testHorizontalDistance() {
        Target target = new CommonTarget(0, 0, 20, 1, 800, 600);
        float distance = target.getDistanceTo(100, 0);
        assertEquals("Distância horizontal pura deve ser 100.0", 100.0f, distance, DELTA);
    }
}
