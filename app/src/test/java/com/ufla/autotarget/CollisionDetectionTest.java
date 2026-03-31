package com.ufla.autotarget;

import com.ufla.autotarget.model.CommonTarget;
import com.ufla.autotarget.model.FastTarget;
import com.ufla.autotarget.model.Projectile;
import com.ufla.autotarget.model.Target;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Testes unitários para detecção de colisão entre projéteis e alvos.
 * Verifica o método checkCollision() da classe Projectile e isHit() da classe Target.
 *
 * A detecção de colisão é baseada na distância entre os centros:
 * Se distância <= raio_projétil + raio_alvo, há colisão.
 *
 * Conceitos testados:
 * - Colisão direta (projétil dentro do alvo)
 * - Sem colisão (projétil longe do alvo)
 * - Colisão na borda (caso limite — tangente)
 * - Alvo inativo não gera colisão
 * - POLIMORFISMO: Testa colisão com CommonTarget e FastTarget
 */
public class CollisionDetectionTest {

    /**
     * Testa colisão direta — projétil está no centro do alvo.
     * Distância = 0, que é menor que raio_alvo + raio_proj.
     * Resultado esperado: colisão detectada (true).
     */
    @Test
    public void testDirectHitCollision() {
        Target target = new CommonTarget(100, 100, 30, 1, 800, 600);
        // Projétil na exata posição do alvo
        Projectile projectile = new Projectile(100, 100, 1, 0, 10, null);

        assertTrue("Projétil no centro do alvo deve colidir", projectile.checkCollision(target));
    }

    /**
     * Testa ausência de colisão — projétil está longe do alvo.
     * Distância entre (100,100) e (500,500) ≈ 565.7
     * Raios: 30 + 6 = 36. Como 565.7 > 36, não há colisão.
     * Resultado esperado: sem colisão (false).
     */
    @Test
    public void testNoCollisionFarAway() {
        Target target = new CommonTarget(100, 100, 30, 1, 800, 600);
        // Projétil longe do alvo
        Projectile projectile = new Projectile(500, 500, 1, 0, 10, null);

        assertFalse("Projétil longe do alvo não deve colidir", projectile.checkCollision(target));
    }

    /**
     * Testa colisão na borda — projétil está exatamente no limite do raio.
     * Alvo em (100,100) com raio 30, projétil em (136, 100) com raio 6.
     * Distância = 36 = 30 + 6. Está no limite, deve colidir.
     */
    @Test
    public void testEdgeCollision() {
        Target target = new CommonTarget(100, 100, 30, 1, 800, 600);
        // Projétil exatamente na borda (distância = raio_alvo + raio_proj = 36)
        Projectile projectile = new Projectile(136, 100, 1, 0, 10, null);

        assertTrue("Projétil na borda do alvo deve colidir", projectile.checkCollision(target));
    }

    /**
     * Testa que projétil logo fora da borda NÃO colide.
     * Alvo raio 30, projétil raio 6. Distância necessária > 36.
     * Projétil em (137, 100), distância = 37 > 36.
     */
    @Test
    public void testJustOutsideCollision() {
        Target target = new CommonTarget(100, 100, 30, 1, 800, 600);
        // Projétil logo fora do raio de colisão
        Projectile projectile = new Projectile(137, 100, 1, 0, 10, null);

        assertFalse("Projétil logo fora da borda não deve colidir", projectile.checkCollision(target));
    }

    /**
     * Testa que alvo inativo não gera colisão, mesmo com sobreposição.
     * Isso evita que projéteis "matem" alvos já destruídos.
     */
    @Test
    public void testInactiveTargetNoCollision() {
        Target target = new CommonTarget(100, 100, 30, 1, 800, 600);
        target.setActive(false); // Desativa o alvo

        Projectile projectile = new Projectile(100, 100, 1, 0, 10, null);

        assertFalse("Alvo inativo não deve gerar colisão", projectile.checkCollision(target));
    }

    /**
     * Testa colisão com FastTarget (POLIMORFISMO).
     * Garante que a detecção funciona independente da subclasse.
     */
    @Test
    public void testCollisionWithFastTarget() {
        Target fastTarget = new FastTarget(200, 200, 25, 2, 800, 600);
        Projectile projectile = new Projectile(210, 200, 1, 0, 10, null);

        // Distância = 10, soma dos raios = 25 + 6 = 31. 10 < 31 → colisão
        assertTrue("Deve detectar colisão com FastTarget (polimorfismo)", projectile.checkCollision(fastTarget));
    }

    /**
     * Testa o método isHit() da classe Target.
     * Verifica se um ponto está dentro do raio do alvo.
     */
    @Test
    public void testTargetIsHit() {
        Target target = new CommonTarget(150, 150, 20, 1, 800, 600);

        assertTrue("Ponto dentro do raio deve ser hit", target.isHit(155, 150));
        assertFalse("Ponto fora do raio não deve ser hit", target.isHit(300, 300));
    }
}
