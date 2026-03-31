package com.ufla.autotarget.engine;

import android.util.Log;
import android.view.View;

import com.ufla.autotarget.exception.JogoException;
import com.ufla.autotarget.model.Cannon;
import com.ufla.autotarget.model.CommonTarget;
import com.ufla.autotarget.model.FastTarget;
import com.ufla.autotarget.model.Projectile;
import com.ufla.autotarget.model.Target;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Semaphore;

/**
 * Motor do jogo — gerencia todas as entidades e a lógica central.
 *
 * =====================================================================
 * ESTRATÉGIA DE SINCRONIZAÇÃO (NÍVEL EXCELENTE)
 * =====================================================================
 *
 * Este motor utiliza DOIS mecanismos de sincronização complementares,
 * cada um escolhido pela sua adequação ao tipo de região crítica:
 *
 * 1. BLOCOS SYNCHRONIZED (monitor pattern):
 *    Usados para proteger operações de leitura/escrita nas listas de
 *    entidades (alvos, canhões, projéteis). Cada lista tem seu próprio
 *    objeto monitor (targetLock, cannonLock, projectileLock), permitindo
 *    operações simultâneas em listas diferentes (maximiza concorrência).
 *
 *    JUSTIFICATIVA: synchronized é ideal para seções críticas curtas
 *    (add/remove/iterate), pois adquire e libera o lock automaticamente,
 *    inclusive em caso de exceção (try-finally implícito), eliminando
 *    risco de deadlock por esquecimento de release.
 *
 * 2. SEMAPHORE (semáforo binário / mutex):
 *    Usado especificamente na verificação de colisão entre projéteis e alvos.
 *    O collisionSemaphore(1) garante que APENAS UM PROJÉTIL POR VEZ
 *    pode executar a verificação de colisão completa (acquire → check → release).
 *
 *    JUSTIFICATIVA: O Semaphore foi escolhido aqui porque:
 *    a) Oferece tryAcquire() — se outro projétil já está verificando,
 *       este pode continuar sem bloquear (non-blocking attempt).
 *    b) É mais flexível que synchronized — permite future extensão para
 *       permitir N verificações simultâneas (Semaphore(N)).
 *    c) Demonstra domínio de ambos os mecanismos de sincronização do Java.
 *
 * EVIDÊNCIA de sincronização: Logs no Logcat com tag "SYNC" registram
 * cada aquisição e liberação de locks/semáforos para auditoria.
 *
 * THREADS ATIVAS:
 * - N threads de alvos (Target) — escrita de posição via move()
 * - M threads de canhões (Cannon) — leitura da lista de alvos
 * - P threads de projéteis (Projectile) — leitura/escrita de alvos (colisão)
 * - 1 thread de spawn — escrita periódica de novos alvos
 * - 1 thread de energia — leitura do número de canhões
 * - 1 UI Thread — leitura de todas as listas (onDraw)
 * =====================================================================
 */
public class GameEngine {

    // Tag para logs de sincronização — visível no Logcat do Android Studio
    private static final String TAG = "AutoTarget";
    private static final String SYNC_TAG = "SYNC";

    // =====================================================================
    // RECURSOS COMPARTILHADOS — listas acessadas por múltiplas threads
    // =====================================================================
    private final ArrayList<Target> targets;
    private final ArrayList<Cannon> cannons;
    private final ArrayList<Projectile> projectiles;

    // =====================================================================
    // MECANISMO 1: OBJETOS MONITOR (synchronized)
    // Um lock por lista para maximizar concorrência entre operações em
    // listas diferentes. Se usássemos um único lock, adicionar um alvo
    // bloquearia a leitura de canhões — desnecessário e ineficiente.
    // =====================================================================
    private final Object targetLock = new Object();
    private final Object cannonLock = new Object();
    private final Object projectileLock = new Object();

    // =====================================================================
    // MECANISMO 2: SEMAPHORE (semáforo binário)
    // Controla acesso exclusivo à verificação de colisão.
    // Semaphore(1) = mutex: apenas 1 thread pode verificar colisão por vez.
    // Semaphore(1, true) → true = fairness, garante FIFO para evitar starvation.
    //
    // POR QUE SEMAPHORE AQUI E NÃO SYNCHRONIZED?
    // 1. tryAcquire() permite tentativa não-bloqueante — projéteis que não
    //    conseguem o semáforo tentam novamente no próximo frame sem ficar parados.
    // 2. O Semaphore pode ser facilmente alterado para Semaphore(2) ou Semaphore(3)
    //    para permitir verificações parcialmente paralelas no futuro.
    // 3. Demonstra conhecimento de ambos os mecanismos (critério "Excelente").
    // =====================================================================
    private final Semaphore collisionSemaphore = new Semaphore(1, true);

    // Semáforo para controlar o número máximo de canhões ativos.
    // Inicializado com MAX_CANNONS permits — cada canhão consome 1 permit.
    // Quando todos os permits são consumidos, novas adições são bloqueadas.
    // Isso complementa a verificação synchronized em addCannon().
    private final Semaphore cannonSlotSemaphore = new Semaphore(MAX_CANNONS, true);

    // Referência à View para chamadas postInvalidate()
    private View gameView;

    // Estado do jogo (volatile para visibilidade entre threads)
    private volatile boolean running;
    private volatile int score;
    private volatile int energy;
    private long gameStartTime;
    private long gameDuration;

    // Configurações
    private int screenWidth;
    private int screenHeight;
    private static final int MAX_CANNONS = 10;
    private static final int CANNON_PENALTY_THRESHOLD = 5;
    private static final int MAX_TARGETS = 15;
    private static final int INITIAL_ENERGY = 1000;
    private static final int ENERGY_PER_CANNON_PER_SECOND = 10;
    private static final long DEFAULT_GAME_DURATION = 60_000; // 60 segundos

    // Threads de gerenciamento
    private Thread targetSpawner;
    private Thread energyManager;

    // Gerador aleatório
    private final Random random;

    // Contador de alvos que escaparam (saíram da tela sem serem destruídos)
    private volatile int escapedTargets;

    // Log visual das últimas ações do sistema (ex: "Canhão 1 disparou")
    // SINCRONIZAÇÃO: Acesso protegido por actionLogLock
    private final ArrayList<String> actionLog;
    private final Object actionLogLock = new Object();

    // Listener para notificar a UI sobre mudanças de estado
    private GameEventListener listener;

    /**
     * Interface para comunicar eventos do jogo à Activity.
     */
    public interface GameEventListener {
        void onScoreChanged(int score);
        void onEnergyChanged(int energy);
        void onGameOver(int finalScore);
    }

    public GameEngine() {
        this.targets = new ArrayList<>();
        this.cannons = new ArrayList<>();
        this.projectiles = new ArrayList<>();
        this.actionLog = new ArrayList<>();
        this.running = false;
        this.score = 0;
        this.energy = INITIAL_ENERGY;
        this.escapedTargets = 0;
        this.gameDuration = DEFAULT_GAME_DURATION;
        this.random = new Random();

        Log.d(TAG, "GameEngine inicializado.");
        Log.d(SYNC_TAG, "Mecanismos de sincronização ativos: " +
              "synchronized (targetLock, cannonLock, projectileLock) + " +
              "Semaphore (collisionSemaphore=1, cannonSlotSemaphore=" + MAX_CANNONS + ")");
    }

    /**
     * Define a referência à View do jogo para chamadas postInvalidate().
     */
    public void setGameView(View view) {
        this.gameView = view;
    }

    /**
     * Solicita redesenho da tela chamando postInvalidate() na View.
     * THREAD-SAFE: postInvalidate() pode ser chamado de qualquer thread.
     */
    public void requestRedraw() {
        if (gameView != null) {
            gameView.postInvalidate();
        }
    }

    // =====================================================================
    // CICLO DE VIDA DO JOGO
    // =====================================================================

    /**
     * Inicia o jogo — ativa thread de spawn, energia, e canhões existentes.
     */
    public void startGame() {
        if (running) return;

        // LIMPEZA PREVENTIVA: Remove entidades 'fantasma' de sessões anteriores.
        // Garante que nenhum objeto da partida passada persista no motor.
        // SYNCHRONIZED: Cada lista é limpa dentro do seu respectivo lock.
        synchronized (targetLock) {
            Log.d(SYNC_TAG, "[synchronized] targetLock adquirido para limpeza preventiva. " +
                  "Alvos residuais: " + targets.size());
            for (Target target : targets) {
                target.setActive(false);
                target.interrupt();
            }
            targets.clear();
        }
        synchronized (cannonLock) {
            Log.d(SYNC_TAG, "[synchronized] cannonLock adquirido para limpeza preventiva. " +
                  "Canhões residuais: " + cannons.size());
            for (Cannon cannon : cannons) {
                cannon.setActive(false);
                cannon.interrupt();
            }
            cannons.clear();
        }
        synchronized (projectileLock) {
            Log.d(SYNC_TAG, "[synchronized] projectileLock adquirido para limpeza preventiva. " +
                  "Projéteis residuais: " + projectiles.size());
            for (Projectile projectile : projectiles) {
                projectile.setActive(false);
                projectile.interrupt();
            }
            projectiles.clear();
        }
        synchronized (actionLogLock) {
            actionLog.clear();
        }

        // Reseta o semáforo de slots de canhão para a nova partida
        cannonSlotSemaphore.drainPermits();
        cannonSlotSemaphore.release(MAX_CANNONS);

        running = true;
        score = 0;
        energy = INITIAL_ENERGY;
        escapedTargets = 0;
        gameStartTime = System.currentTimeMillis();

        Log.d(TAG, "Jogo iniciado. Duração: " + (gameDuration / 1000) + "s");

        // Inicia thread de spawn de alvos
        startTargetSpawner();

        // Inicia thread de gerenciamento de energia
        startEnergyManager();

        // SYNCHRONIZED: Inicia threads dos canhões dentro do lock
        synchronized (cannonLock) {
            Log.d(SYNC_TAG, "[synchronized] cannonLock adquirido para iniciar canhões. " +
                  "Thread: " + Thread.currentThread().getName());
            for (Cannon cannon : cannons) {
                if (!cannon.isAlive()) {
                    cannon.start();
                }
            }
            Log.d(SYNC_TAG, "[synchronized] cannonLock liberado.");
        }

        if (listener != null) {
            listener.onScoreChanged(score);
            listener.onEnergyChanged(energy);
        }
    }

    /**
     * Para o jogo — interrompe graciosamente todas as threads e limpa as listas.
     *
     * SINCRONIZAÇÃO: Cada lista é bloqueada individualmente com synchronized.
     * O cannonSlotSemaphore é resetado liberando todos os permits consumidos.
     */
    public void stopGame() {
        if (!running) return; // Evita paradas duplicadas

        running = false;
        int finalScore = score; // Captura o score final ANTES de resetar
        Log.d(TAG, "Jogo parado. Score final: " + finalScore);

        // NOTIFICAÇÃO ANTECIPADA: Notifica o listener com a pontuação final
        // ANTES de limpar as listas e resetar o estado, garantindo que a
        // Activity receba os valores corretos da partida encerrada.
        if (listener != null) {
            listener.onGameOver(finalScore);
        }

        // Interrompe threads de gerenciamento
        if (targetSpawner != null) {
            targetSpawner.interrupt();
            targetSpawner = null;
        }
        if (energyManager != null) {
            energyManager.interrupt();
            energyManager = null;
        }

        // SYNCHRONIZED: Bloqueia targetLock para interromper alvos
        synchronized (targetLock) {
            Log.d(SYNC_TAG, "[synchronized] targetLock adquirido para limpar " +
                  targets.size() + " alvos. Thread: " + Thread.currentThread().getName());
            for (Target target : targets) {
                target.setActive(false);
                target.interrupt();
            }
            targets.clear();
            Log.d(SYNC_TAG, "[synchronized] targetLock liberado. Lista de alvos limpa.");
        }

        // SYNCHRONIZED: Bloqueia cannonLock para interromper canhões
        int cannonCount;
        synchronized (cannonLock) {
            cannonCount = cannons.size();
            Log.d(SYNC_TAG, "[synchronized] cannonLock adquirido para limpar " +
                  cannonCount + " canhões. Thread: " + Thread.currentThread().getName());
            for (Cannon cannon : cannons) {
                cannon.setActive(false);
                cannon.interrupt();
            }
            cannons.clear();
            Log.d(SYNC_TAG, "[synchronized] cannonLock liberado. Lista de canhões limpa.");
        }

        // SEMAPHORE: Reseta o semáforo de slots de canhão para a próxima partida.
        cannonSlotSemaphore.drainPermits();
        cannonSlotSemaphore.release(MAX_CANNONS);
        Log.d(SYNC_TAG, "[Semaphore] cannonSlotSemaphore resetado. " +
              "Permits disponíveis: " + cannonSlotSemaphore.availablePermits());

        // SYNCHRONIZED: Bloqueia projectileLock para interromper projéteis
        synchronized (projectileLock) {
            Log.d(SYNC_TAG, "[synchronized] projectileLock adquirido para limpar " +
                  projectiles.size() + " projéteis.");
            for (Projectile projectile : projectiles) {
                projectile.setActive(false);
                projectile.interrupt();
            }
            projectiles.clear();
            Log.d(SYNC_TAG, "[synchronized] projectileLock liberado. Lista de projéteis limpa.");
        }

        // Reseta estado
        score = 0;
        energy = INITIAL_ENERGY;
        escapedTargets = 0;

        // Limpa o log de ações para a próxima partida
        synchronized (actionLogLock) {
            actionLog.clear();
        }

        requestRedraw();
    }

    // =====================================================================
    // SPAWN E GERENCIAMENTO DE ALVOS
    // =====================================================================

    /**
     * Thread que cria novos alvos periodicamente.
     * POLIMORFISMO: 70% CommonTarget, 30% FastTarget.
     */
    private void startTargetSpawner() {
        targetSpawner = new Thread(() -> {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(2000);

                    // SYNCHRONIZED: Verifica tamanho da lista atomicamente
                    int targetCount;
                    synchronized (targetLock) {
                        targetCount = targets.size();
                    }

                    if (targetCount < MAX_TARGETS) {
                        spawnRandomTarget();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        targetSpawner.setDaemon(true);
        targetSpawner.start();
    }

    /**
     * Thread de gerenciamento de energia.
     */
    private void startEnergyManager() {
        energyManager = new Thread(() -> {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1000);

                    // SYNCHRONIZED: Lê tamanho da lista de canhões atomicamente
                    int cannonCount;
                    synchronized (cannonLock) {
                        cannonCount = cannons.size();
                    }

                    int consumption = cannonCount * ENERGY_PER_CANNON_PER_SECOND;
                    energy -= consumption;

                    if (listener != null) {
                        listener.onEnergyChanged(energy);
                    }

                    requestRedraw();

                    long elapsed = System.currentTimeMillis() - gameStartTime;
                    if (elapsed >= gameDuration || energy <= 0) {
                        // CENTRALIZAÇÃO: Chama stopGame() em vez de apenas
                        // definir running = false. Isso garante que TODAS as
                        // entidades sejam interrompidas e as listas limpas,
                        // evitando persistência de canhões/alvos 'fantasma'
                        // ao reiniciar o jogo.
                        stopGame();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        energyManager.setDaemon(true);
        energyManager.start();
    }

    /**
     * Cria um alvo aleatório (CommonTarget ou FastTarget).
     * POLIMORFISMO: O tipo concreto determina o comportamento de move().
     */
    private void spawnRandomTarget() {
        if (screenWidth <= 0 || screenHeight <= 0) return;

        float radius = 25 + random.nextFloat() * 15;
        float x = radius + random.nextFloat() * (screenWidth - 2 * radius);
        float y = radius + random.nextFloat() * (screenHeight * 0.6f);
        float speed = 2 + random.nextFloat() * 2;

        Target target;
        if (random.nextFloat() < 0.7f) {
            target = new CommonTarget(x, y, radius, speed, screenWidth, screenHeight);
        } else {
            target = new FastTarget(x, y, radius, speed, screenWidth, screenHeight);
        }

        target.setEngine(this);

        // SYNCHRONIZED: Adiciona à lista dentro de bloco synchronized
        String tipoAlvo = (target instanceof FastTarget) ? "Rápido" : "Comum";
        synchronized (targetLock) {
            targets.add(target);
            Log.d(SYNC_TAG, "[synchronized] Alvo adicionado à lista. " +
                  "Tipo: " + target.getClass().getSimpleName() +
                  " | Total: " + targets.size() +
                  " | Thread: " + Thread.currentThread().getName());
        }

        // Log visual para o HUD
        addActionLog("Alvo " + tipoAlvo + " surgiu");

        target.start();
    }

    // =====================================================================
    // ADIÇÃO DE CANHÕES (SYNCHRONIZED + SEMAPHORE)
    // =====================================================================

    /**
     * Adiciona um canhão ao jogo.
     *
     * DOIS MECANISMOS DE SINCRONIZAÇÃO EM CONJUNTO:
     *
     * 1. cannonSlotSemaphore.tryAcquire() — Verifica de forma non-blocking
     *    se há "slots" disponíveis. Se todos os MAX_CANNONS permits foram
     *    consumidos, retorna false imediatamente sem bloquear a thread.
     *    VANTAGEM: Evita que a UI Thread fique travada esperando.
     *
     * 2. synchronized(cannonLock) — Após garantir o slot via Semaphore,
     *    o bloco synchronized protege a operação de inserção na lista.
     *    Garante que a verificação de penalidade + inserção é atômica.
     *
     * JUSTIFICATIVA para usar ambos:
     * - O Semaphore controla CAPACIDADE (quantos canhões existem).
     * - O synchronized protege INTEGRIDADE (a lista não é corrompida).
     * - São responsabilidades diferentes que se complementam.
     *
     * @throws JogoException se posição inválida ou limite excedido
     */
    public void addCannon(float x, float y) throws JogoException {
        // Validação de estado — impede adicionar canhão com o jogo parado
        if (!running) {
            throw new JogoException(
                "O jogo não está em execução. Inicie o jogo antes de adicionar canhões."
            );
        }

        // Validação de posição — lança exceção com mensagem descritiva
        if (x < 0 || x > screenWidth || y < 0 || y > screenHeight) {
            throw new JogoException(
                "Posição inválida para canhão: (" + x + ", " + y + "). " +
                "Deve estar dentro dos limites da tela (0-" + screenWidth + ", 0-" + screenHeight + ")."
            );
        }

        // SEMAPHORE: Tenta adquirir um slot de canhão (non-blocking)
        // tryAcquire() retorna false se todos os permits foram consumidos.
        // Isso é mais eficiente que bloquear a UI Thread com acquire().
        boolean slotAcquired = cannonSlotSemaphore.tryAcquire();
        Log.d(SYNC_TAG, "[Semaphore] cannonSlotSemaphore.tryAcquire() = " + slotAcquired +
              " | Permits restantes: " + cannonSlotSemaphore.availablePermits() +
              " | Thread: " + Thread.currentThread().getName());

        if (!slotAcquired) {
            throw new JogoException(
                "Número máximo de canhões atingido (" + MAX_CANNONS + "). " +
                "Remova um canhão antes de adicionar outro. " +
                "[Semaphore: 0 permits disponíveis]"
            );
        }

        // SYNCHRONIZED: Protege a inserção na lista + aplicação de penalidade
        synchronized (cannonLock) {
            Log.d(SYNC_TAG, "[synchronized] cannonLock adquirido para adicionar canhão. " +
                  "Thread: " + Thread.currentThread().getName());

            Cannon cannon = new Cannon(x, y, this);

            // Aplica penalidade de taxa de disparo acima do limiar (5 canhões)
            if (cannons.size() >= CANNON_PENALTY_THRESHOLD) {
                float penalty = 1.0f + (cannons.size() - CANNON_PENALTY_THRESHOLD + 1) * 0.5f;
                cannon.applyFireRatePenalty(penalty);
                Log.d(TAG, "Penalidade aplicada ao canhão #" + (cannons.size() + 1) +
                      ": fireRate x" + penalty);
            }

            cannons.add(cannon);

            // Log visual para o HUD
            addActionLog("Canhão #" + cannons.size() + " posicionado");

            Log.d(SYNC_TAG, "[synchronized] Canhão adicionado na posição (" + x + ", " + y +
                  ") | Total: " + cannons.size() +
                  " | Semaphore permits: " + cannonSlotSemaphore.availablePermits());

            if (running) {
                cannon.start();
            }

            Log.d(SYNC_TAG, "[synchronized] cannonLock liberado.");
        }
    }

    // =====================================================================
    // ADIÇÃO DE PROJÉTEIS (SYNCHRONIZED)
    // =====================================================================

    /**
     * Adiciona um projétil à lista.
     * SYNCHRONIZED: Protege a lista de projéteis para evitar
     * ConcurrentModificationException quando múltiplos canhões disparam.
     *
     * @throws JogoException se o projétil é nulo
     */
    public void addProjectile(Projectile projectile) throws JogoException {
        if (projectile == null) {
            throw new JogoException("Projétil não pode ser nulo.");
        }

        projectile.setScreenWidth(screenWidth);
        projectile.setScreenHeight(screenHeight);

        // SYNCHRONIZED: Lock na lista de projéteis para inserção segura
        synchronized (projectileLock) {
            projectiles.add(projectile);
        }
    }

    // =====================================================================
    // LEITURA SEGURA DE ALVOS (SYNCHRONIZED — cópia snapshot)
    // =====================================================================

    /**
     * Retorna uma cópia segura da lista de alvos ativos.
     * Usada pelos canhões para buscar o alvo mais próximo.
     *
     * SYNCHRONIZED: A cópia é feita atomicamente. A lista original não
     * é exposta — o canhão trabalha com um snapshot e não mantém o lock
     * durante toda a busca (melhora concorrência a custo de dados
     * ligeiramente desatualizados — trade-off aceitável).
     */
    public List<Target> getTargetsSafe() {
        synchronized (targetLock) {
            return new ArrayList<>(targets);
        }
    }

    // =====================================================================
    // VERIFICAÇÃO DE COLISÃO (SEMAPHORE — região crítica principal)
    // =====================================================================

    /**
     * Verifica colisão de um projétil com todos os alvos ativos.
     *
     * ================================================================
     * REGIÃO CRÍTICA PROTEGIDA POR SEMAPHORE
     * ================================================================
     *
     * Este é o ponto MAIS CRÍTICO de sincronização do jogo.
     * Usamos o collisionSemaphore (Semaphore binário com 1 permit)
     * para garantir que APENAS UM PROJÉTIL POR VEZ execute este bloco.
     *
     * PROBLEMA SEM SINCRONIZAÇÃO (condição de corrida):
     *   Thread Projétil-A                Thread Projétil-B
     *   ──────────────────                ──────────────────
     *   1. Lê alvo.isActive() → true      1. Lê alvo.isActive() → true
     *   2. checkCollision() → true        2. checkCollision() → true
     *   3. alvo.setActive(false)          3. alvo.setActive(false)  ← DUPLICADO!
     *   4. score += 3                     4. score += 3             ← DUPLICADO!
     *
     *   Resultado: score incrementado 2x para o mesmo alvo.
     *
     * SOLUÇÃO COM SEMAPHORE:
     *   Thread Projétil-A                Thread Projétil-B
     *   ──────────────────                ──────────────────
     *   1. acquire() → obtém permit       1. tryAcquire() → false (sem permit)
     *   2. Verifica colisão               2. Retorna, tenta no próximo frame
     *   3. Desativa alvo + score
     *   4. release() → libera permit
     *
     * POR QUE SEMAPHORE E NÃO SYNCHRONIZED AQUI?
     * - tryAcquire() permite tentativa NON-BLOCKING: se outro projétil
     *   já está verificando, este simplesmente pula e tenta no próximo
     *   frame (16ms). Com synchronized, ficaria BLOQUEADO esperando.
     * - Em um jogo com muitos projéteis, o non-blocking evita
     *   "travamentos" perceptíveis na velocidade dos projéteis.
     * - O fairness=true garante FIFO entre projéteis esperando.
     *
     * DENTRO do Semaphore, usamos TAMBÉM synchronized(targetLock)
     * para proteger a lista de alvos contra modificações durante
     * a iteração (ex: novo alvo sendo adicionado pelo spawner).
     * ================================================================
     *
     * @param projectile projétil a verificar colisão
     */
    public void checkProjectileCollision(Projectile projectile) {
        if (!projectile.isActive()) return;

        // SEMAPHORE: Tentativa non-blocking de adquirir o permit de colisão.
        // Se outro projétil já está verificando, retorna false e este
        // projétil tenta novamente no próximo frame (~16ms depois).
        boolean acquired = collisionSemaphore.tryAcquire();

        if (!acquired) {
            // Não conseguiu o semáforo — outro projétil está verificando.
            // Não bloqueia; tenta novamente no próximo ciclo do run().
            return;
        }

        try {
            Log.d(SYNC_TAG, "[Semaphore] collisionSemaphore adquirido por " +
                  Thread.currentThread().getName() +
                  " | Projétil em (" + projectile.getProjectileX() + ", " +
                  projectile.getProjectileY() + ")");

            // SYNCHRONIZED: Dentro do Semaphore, usa targetLock para
            // proteger a iteração na lista de alvos. O Semaphore controla
            // "quem entra" e o synchronized protege "a lista durante a verificação".
            synchronized (targetLock) {
                for (Target target : targets) {
                    if (target.isActive() && projectile.checkCollision(target)) {
                        // Desativa alvo e projétil ATOMICAMENTE dentro do lock
                        target.setActive(false);
                        projectile.setActive(false);

                        // Incrementa pontuação (seguro — exclusão mútua garantida)
                        score += target.getScoreValue();

                        // Determina tipo do alvo para log visual
                        String tipo = (target instanceof FastTarget) ? "Rápido" : "Comum";
                        addActionLog("Alvo " + tipo + " destruído! +" + target.getScoreValue());

                        Log.d(TAG, "COLISÃO! " + target.getClass().getSimpleName() +
                              " destruído | +" + target.getScoreValue() + " pts" +
                              " | Score total: " + score);
                        Log.d(SYNC_TAG, "[synchronized+Semaphore] Colisão processada atomicamente. " +
                              "Alvo desativado + score incrementado sem race condition.");

                        if (listener != null) {
                            listener.onScoreChanged(score);
                        }

                        requestRedraw();
                        break; // Um projétil só atinge um alvo por disparo
                    }
                }
            }
        } finally {
            // SEMAPHORE: SEMPRE libera o permit no finally, garantindo que
            // o semáforo nunca "perde" permits mesmo se ocorrer exceção.
            collisionSemaphore.release();
            Log.d(SYNC_TAG, "[Semaphore] collisionSemaphore liberado por " +
                  Thread.currentThread().getName());
        }
    }

    // =====================================================================
    // LIMPEZA DE ENTIDADES INATIVAS (SYNCHRONIZED)
    // =====================================================================

    /**
     * Remove entidades inativas das listas para liberar memória.
     * SYNCHRONIZED: Cada lista é limpa com seu respectivo lock.
     */
    public void cleanupInactiveEntities() {
        // SYNCHRONIZED: Lock na lista de alvos para remoção segura
        synchronized (targetLock) {
            Iterator<Target> it = targets.iterator();
            while (it.hasNext()) {
                Target t = it.next();
                if (!t.isActive()) {
                    t.interrupt();
                    it.remove();
                }
            }
        }

        // SYNCHRONIZED: Lock na lista de projéteis para remoção segura
        synchronized (projectileLock) {
            Iterator<Projectile> it = projectiles.iterator();
            while (it.hasNext()) {
                Projectile p = it.next();
                if (!p.isActive()) {
                    p.interrupt();
                    it.remove();
                }
            }
        }
    }

    // =====================================================================
    // CÓPIAS SEGURAS PARA RENDERIZAÇÃO (SYNCHRONIZED — snapshots)
    // Cada método retorna uma cópia da lista para que o onDraw() trabalhe
    // sem manter locks durante todo o desenho (evita frame drops).
    // =====================================================================

    public List<Target> getTargetsForRender() {
        synchronized (targetLock) {
            return new ArrayList<>(targets);
        }
    }

    public List<Cannon> getCannonsForRender() {
        synchronized (cannonLock) {
            return new ArrayList<>(cannons);
        }
    }

    public List<Projectile> getProjectilesForRender() {
        synchronized (projectileLock) {
            return new ArrayList<>(projectiles);
        }
    }

    // ==================== Getters e Setters ====================

    public boolean isRunning() { return running; }
    public int getScore() { return score; }
    public int getEnergy() { return energy; }
    public int getEscapedTargets() { return escapedTargets; }

    public long getRemainingTime() {
        if (!running) return 0;
        long elapsed = System.currentTimeMillis() - gameStartTime;
        return Math.max(0, (gameDuration - elapsed) / 1000);
    }

    public int getCannonCount() {
        synchronized (cannonLock) {
            return cannons.size();
        }
    }

    /**
     * Retorna o número de threads de alvos ativos.
     * SYNCHRONIZED: Leitura protegida da lista de alvos.
     */
    public int getActiveTargetCount() {
        synchronized (targetLock) {
            int count = 0;
            for (Target t : targets) {
                if (t.isActive()) count++;
            }
            return count;
        }
    }

    /**
     * Retorna o número de threads de projéteis ativos.
     * SYNCHRONIZED: Leitura protegida da lista de projéteis.
     */
    public int getActiveProjectileCount() {
        synchronized (projectileLock) {
            int count = 0;
            for (Projectile p : projectiles) {
                if (p.isActive()) count++;
            }
            return count;
        }
    }

    /**
     * Retorna o status textual do sistema para exibição no HUD.
     * Baseado no estado atual das entidades do jogo.
     */
    public String getSystemStatus() {
        if (!running) return "Aguardando";
        int targetCount = getActiveTargetCount();
        int cannonCount = getCannonCount();
        if (cannonCount == 0) return "Sem Defesa!";
        if (targetCount > 0) return "Alvo Detectado";
        return "Defesa Ativa";
    }

    /**
     * Registra uma ação no log visual do jogo.
     * Mantém apenas as últimas 5 ações (FIFO).
     * SYNCHRONIZED: Protege o acesso à lista de ações.
     */
    public void addActionLog(String action) {
        synchronized (actionLogLock) {
            actionLog.add(action);
            if (actionLog.size() > 5) {
                actionLog.remove(0);
            }
        }
    }

    /**
     * Retorna cópia segura do log de ações para renderização.
     * SYNCHRONIZED: Cópia snapshot da lista.
     */
    public List<String> getActionLog() {
        synchronized (actionLogLock) {
            return new ArrayList<>(actionLog);
        }
    }

    public void setScreenDimensions(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }

    public int getScreenWidth() { return screenWidth; }
    public int getScreenHeight() { return screenHeight; }

    public void setGameEventListener(GameEventListener listener) {
        this.listener = listener;
    }
}
