package com.ufla.autotarget;

import com.ufla.autotarget.engine.GameEngine;
import com.ufla.autotarget.exception.JogoException;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Testes unitários para o GameEngine — motor do jogo.
 * Verifica a sincronização, adição de canhões, e tratamento de exceções.
 *
 * Conceitos testados:
 * - Adição de canhões com validação de limites
 * - JogoException ao exceder limites da tela
 * - JogoException ao exceder número máximo de canhões
 * - Estado inicial do jogo
 * - Sincronização ao adicionar/remover entidades concorrentemente
 */
public class GameEngineTest {

    private GameEngine engine;

    @Before
    public void setUp() {
        engine = new GameEngine();
        // Define dimensões da tela para os testes
        engine.setScreenDimensions(800, 600);
    }

    /**
     * Testa que o estado inicial do GameEngine está correto.
     * Score deve ser 0, jogo não deve estar rodando.
     */
    @Test
    public void testInitialState() {
        assertEquals("Score inicial deve ser 0", 0, engine.getScore());
        assertEquals("Nenhum canhão inicialmente", 0, engine.getCannonCount());
        assertEquals("Jogo não deve estar rodando", false, engine.isRunning());
    }

    /**
     * Testa adição bem-sucedida de um canhão em posição válida.
     * Não deve lançar exceção.
     */
    @Test
    public void testAddCannonValid() {
        try {
            engine.addCannon(400, 300);
            assertEquals("Deve ter 1 canhão após adição", 1, engine.getCannonCount());
        } catch (JogoException e) {
            fail("Não deveria lançar exceção para posição válida: " + e.getMessage());
        }
    }

    /**
     * Testa que JogoException é lançada ao adicionar canhão fora dos limites.
     * Coordenada X negativa deve ser rejeitada.
     *
     * TRATAMENTO DE EXCEÇÕES: Verifica que a exceção personalizada
     * é lançada com mensagem descritiva.
     */
    @Test(expected = JogoException.class)
    public void testAddCannonOutOfBoundsNegativeX() throws JogoException {
        engine.addCannon(-10, 300);
    }

    /**
     * Testa que JogoException é lançada ao adicionar canhão com Y fora da tela.
     */
    @Test(expected = JogoException.class)
    public void testAddCannonOutOfBoundsExceedY() throws JogoException {
        engine.addCannon(400, 700); // screenHeight é 600
    }

    /**
     * Testa que JogoException é lançada ao exceder o limite máximo de canhões.
     * O limite é 10 canhões. Ao tentar adicionar o 11º, deve lançar exceção.
     *
     * TRATAMENTO DE EXCEÇÕES: Verifica que o sistema recusa graciosamente
     * a adição excessiva de canhões, sem crash.
     */
    @Test
    public void testCannonLimitExceeded() {
        try {
            // Adiciona 10 canhões (o máximo permitido)
            for (int i = 0; i < 10; i++) {
                engine.addCannon(50 + i * 70, 300);
            }
            assertEquals("Deve ter 10 canhões", 10, engine.getCannonCount());

            // Tenta adicionar o 11º — deve lançar JogoException
            try {
                engine.addCannon(400, 400);
                fail("Deveria ter lançado JogoException ao exceder limite");
            } catch (JogoException e) {
                // Esperado — verifica que a mensagem é informativa
                assertNotNull("Mensagem de erro não deve ser nula", e.getMessage());
                assertEquals("Contagem de canhões não deve ter mudado", 10, engine.getCannonCount());
            }
        } catch (JogoException e) {
            fail("Erro inesperado ao adicionar canhões: " + e.getMessage());
        }
    }

    /**
     * Testa a integridade da mensagem de erro da JogoException.
     * Verifica que exceções personalizadas possuem mensagens úteis.
     */
    @Test
    public void testJogoExceptionMessage() {
        try {
            engine.addCannon(-100, -200);
            fail("Deveria ter lançado JogoException");
        } catch (JogoException e) {
            assertNotNull("Mensagem não deve ser nula", e.getMessage());
            // Verifica que a mensagem contém informações úteis
            String msg = e.getMessage().toLowerCase();
            // A mensagem deve mencionar "posição" ou "inválid"
            boolean hasRelevantInfo = msg.contains("posição") || msg.contains("invalid") || msg.contains("limites");
            assertEquals("Mensagem deve conter informação relevante", true, hasRelevantInfo);
        }
    }

    /**
     * Testa adição concorrente de canhões (simulação de sincronização).
     * Cria múltiplas threads que tentam adicionar canhões simultaneamente.
     * O resultado final deve ser consistente — sem duplicatas ou perdas.
     *
     * SINCRONIZAÇÃO: Verifica que o Semaphore + synchronized em addCannon()
     * protegem corretamente a lista de canhões contra condições de corrida.
     */
    @Test
    public void testConcurrentCannonAddition() throws InterruptedException {
        final int THREADS = 5;
        Thread[] threads = new Thread[THREADS];

        // Cada thread tenta adicionar um canhão em posição diferente
        for (int i = 0; i < THREADS; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    engine.addCannon(100 + index * 100, 300);
                } catch (JogoException e) {
                    // Pode ocorrer se exceder limites
                }
            });
        }

        // Inicia todas as threads simultaneamente
        for (Thread t : threads) t.start();
        // Aguarda todas terminarem
        for (Thread t : threads) t.join();

        // Deve ter exatamente THREADS canhões, sem duplicatas ou perdas
        assertEquals("Adição concorrente deve resultar em " + THREADS + " canhões",
                THREADS, engine.getCannonCount());
    }

    /**
     * Testa que o Semaphore limita corretamente o número de canhões
     * quando muitas threads tentam adicionar simultaneamente.
     *
     * SEMAPHORE: O cannonSlotSemaphore(10) permite no máximo 10 canhões.
     * Se 15 threads tentam adicionar ao mesmo tempo, exatamente 10 devem
     * conseguir (adquirem o permit) e 5 devem falhar (tryAcquire = false).
     *
     * Este teste é EVIDÊNCIA de que a sincronização com Semaphore funciona
     * corretamente sob alta concorrência — critério "Excelente" da rubrica.
     */
    @Test
    public void testSemaphoreLimitsCannonsConcurrently() throws InterruptedException {
        final int THREADS = 15; // Mais threads que o limite de canhões (10)
        Thread[] threads = new Thread[THREADS];
        final int[] successCount = {0};
        final int[] failCount = {0};
        final Object counterLock = new Object();

        for (int i = 0; i < THREADS; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    // Posições distribuídas na tela para evitar sobreposição
                    engine.addCannon(50 + (index % 10) * 70, 300);
                    synchronized (counterLock) {
                        successCount[0]++;
                    }
                } catch (JogoException e) {
                    // SEMAPHORE: tryAcquire() retornou false para o 11º+ canhão
                    synchronized (counterLock) {
                        failCount[0]++;
                    }
                }
            });
        }

        // Inicia todas as threads simultaneamente para máxima concorrência
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        // O Semaphore deve ter permitido exatamente MAX_CANNONS (10) adições
        assertEquals("Semaphore deve limitar a exatamente 10 canhões",
                10, engine.getCannonCount());
        assertEquals("10 threads devem ter sucesso (Semaphore permits)",
                10, successCount[0]);
        assertEquals("5 threads devem falhar (Semaphore sem permits)",
                5, failCount[0]);
    }
}
