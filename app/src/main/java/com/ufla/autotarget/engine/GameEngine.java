package com.ufla.autotarget.engine;

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

/**
 * Motor do jogo — gerencia todas as entidades e a lógica central.
 *
 * SINCRONIZAÇÃO / REGIÃO CRÍTICA:
 * Esta classe é o ponto central de sincronização do jogo. As listas de
 * alvos, canhões e projéteis são recursos compartilhados acessados por
 * múltiplas threads simultaneamente:
 * - Threads dos alvos (escrita de posição no move())
 * - Threads dos canhões (leitura da lista de alvos para achar o mais próximo)
 * - Threads dos projéteis (leitura de alvos + escrita ao desativar após colisão)
 * - UI Thread (leitura de todas as listas no onDraw() para renderização)
 * - Thread de spawn (escrita ao adicionar novos alvos periodicamente)
 *
 * Usamos objetos monitor separados (targetLock, cannonLock, projectileLock)
 * com blocos synchronized para proteger cada região crítica.
 * Locks separados maximizam a concorrência — por exemplo, novos alvos
 * podem ser adicionados enquanto projéteis são removidos, pois não
 * competem pelo mesmo monitor.
 *
 * POSTINVALIDATE: Após cada atualização de posição ou estado, as threads
 * chamam requestRedraw() que chama postInvalidate() na View. Isso garante
 * que APENAS a UI Thread acesse o Canvas para desenhar, enquanto as
 * threads secundárias apenas solicitam o redesenho.
 */
public class GameEngine {

    // Listas de entidades do jogo (RECURSOS COMPARTILHADOS — acesso sincronizado)
    private final ArrayList<Target> targets;
    private final ArrayList<Cannon> cannons;
    private final ArrayList<Projectile> projectiles;

    // Objetos monitor para sincronização das regiões críticas
    // Um lock por lista para maximizar concorrência entre operações independentes
    private final Object targetLock = new Object();
    private final Object cannonLock = new Object();
    private final Object projectileLock = new Object();

    // Referência à View para chamadas postInvalidate() — acessada via requestRedraw()
    private View gameView;

    // Estado do jogo
    private volatile boolean running;
    private volatile int score;
    private volatile int energy;
    private long gameStartTime;
    private long gameDuration; // duração em milissegundos

    // Configurações
    private int screenWidth;
    private int screenHeight;
    private static final int MAX_CANNONS = 10;
    private static final int CANNON_PENALTY_THRESHOLD = 5;
    private static final int MAX_TARGETS = 15;
    private static final int INITIAL_ENERGY = 1000;
    private static final int ENERGY_PER_CANNON_PER_SECOND = 10;
    private static final long DEFAULT_GAME_DURATION = 60_000; // 60 segundos

    // Thread de spawn de alvos
    private Thread targetSpawner;

    // Thread de gerenciamento de energia
    private Thread energyManager;

    // Gerador aleatório
    private final Random random;

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
        this.running = false;
        this.score = 0;
        this.energy = INITIAL_ENERGY;
        this.gameDuration = DEFAULT_GAME_DURATION;
        this.random = new Random();
    }

    /**
     * Define a referência à View do jogo para chamadas postInvalidate().
     * Deve ser chamado pela Activity ao inicializar.
     */
    public void setGameView(View view) {
        this.gameView = view;
    }

    /**
     * Solicita redesenho da tela chamando postInvalidate() na View.
     *
     * POSTINVALIDATE: Este método é chamado pelas threads secundárias
     * (alvos, canhões, projéteis) após atualizarem suas posições.
     * O postInvalidate() é THREAD-SAFE — pode ser chamado de qualquer thread.
     * Ele enfileira um pedido de invalidação que a UI Thread processará
     * quando estiver disponível, chamando onDraw() na GameView.
     *
     * Isso garante que as threads do jogo NUNCA acessam o Canvas
     * diretamente, respeitando o modelo single-thread da UI do Android.
     */
    public void requestRedraw() {
        if (gameView != null) {
            gameView.postInvalidate();
        }
    }

    /**
     * Inicia o jogo — ativa thread de spawn, energia, e canhões existentes.
     */
    public void startGame() {
        if (running) return;

        running = true;
        score = 0;
        energy = INITIAL_ENERGY;
        gameStartTime = System.currentTimeMillis();

        // Inicia thread de spawn de alvos
        startTargetSpawner();

        // Inicia thread de gerenciamento de energia
        startEnergyManager();

        // Inicia threads dos canhões existentes
        synchronized (cannonLock) {
            for (Cannon cannon : cannons) {
                if (!cannon.isAlive()) {
                    cannon.start();
                }
            }
        }

        if (listener != null) {
            listener.onScoreChanged(score);
            listener.onEnergyChanged(energy);
        }
    }

    /**
     * Para o jogo — interrompe graciosamente todas as threads e limpa as listas.
     *
     * SINCRONIZAÇÃO: Cada lista é bloqueada individualmente para
     * interromper as threads de forma segura.
     *
     * RESET: Após interromper, as listas são limpas (clear()) porque
     * threads Java não podem ser reiniciadas após terminar (IllegalThreadStateException).
     * Na próxima partida, novas instâncias de threads serão criadas.
     */
    public void stopGame() {
        running = false;

        // Interrompe thread de spawn
        if (targetSpawner != null) {
            targetSpawner.interrupt();
            targetSpawner = null;
        }

        // Interrompe thread de energia
        if (energyManager != null) {
            energyManager.interrupt();
            energyManager = null;
        }

        // SINCRONIZAÇÃO: Bloqueia cada lista para interromper threads
        // e limpa as listas para permitir reinício do jogo
        synchronized (targetLock) {
            for (Target target : targets) {
                target.setActive(false);
                target.interrupt();
            }
            targets.clear(); // Remove threads mortas — não podem ser reutilizadas
        }

        synchronized (cannonLock) {
            for (Cannon cannon : cannons) {
                cannon.setActive(false);
                cannon.interrupt();
            }
            cannons.clear(); // Remove canhões — novos serão criados na próxima partida
        }

        synchronized (projectileLock) {
            for (Projectile projectile : projectiles) {
                projectile.setActive(false);
                projectile.interrupt();
            }
            projectiles.clear(); // Remove projéteis em voo
        }

        // Reseta estado do jogo para a próxima partida
        score = 0;
        energy = INITIAL_ENERGY;

        // Solicita redesenho final para limpar a tela
        requestRedraw();
    }

    /**
     * Thread responsável por criar novos alvos periodicamente.
     * Cria CommonTarget (70%) ou FastTarget (30%) — POLIMORFISMO.
     * O loop principal percorre a lista de alvos e chama move()
     * de forma polimórfica através do método run() de cada thread.
     */
    private void startTargetSpawner() {
        targetSpawner = new Thread(() -> {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(2000); // Novo alvo a cada 2 segundos

                    // SINCRONIZAÇÃO: Verifica tamanho da lista atomicamente
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
     * Consome energia proporcional ao número de canhões ativos.
     * Quando a energia ou o tempo acabam, o jogo termina.
     */
    private void startEnergyManager() {
        energyManager = new Thread(() -> {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1000); // A cada segundo

                    int cannonCount;
                    synchronized (cannonLock) {
                        cannonCount = cannons.size();
                    }

                    // Consume energia proporcional ao número de canhões
                    int consumption = cannonCount * ENERGY_PER_CANNON_PER_SECOND;
                    energy -= consumption;

                    if (listener != null) {
                        listener.onEnergyChanged(energy);
                    }

                    // Solicita redesenho para atualizar HUD
                    requestRedraw();

                    // Verifica condições de fim de jogo
                    long elapsed = System.currentTimeMillis() - gameStartTime;
                    if (elapsed >= gameDuration || energy <= 0) {
                        running = false;
                        if (listener != null) {
                            listener.onGameOver(score);
                        }
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
     * Cria um alvo aleatório e o adiciona ao jogo.
     * 70% de chance de CommonTarget, 30% de FastTarget — POLIMORFISMO.
     * Cada alvo é uma Thread independente que chama move() no seu run().
     */
    private void spawnRandomTarget() {
        if (screenWidth <= 0 || screenHeight <= 0) return;

        float radius = 25 + random.nextFloat() * 15;
        float x = radius + random.nextFloat() * (screenWidth - 2 * radius);
        float y = radius + random.nextFloat() * (screenHeight * 0.6f);
        float speed = 2 + random.nextFloat() * 2;

        Target target;
        // POLIMORFISMO: O tipo concreto é decidido aqui, mas o engine
        // trata todos como Target (classe abstrata). O método move()
        // é resolvido em tempo de execução (late binding).
        if (random.nextFloat() < 0.7f) {
            target = new CommonTarget(x, y, radius, speed, screenWidth, screenHeight);
        } else {
            target = new FastTarget(x, y, radius, speed, screenWidth, screenHeight);
        }

        // Passa referência ao engine para que a thread chame requestRedraw()
        target.setEngine(this);

        // SINCRONIZAÇÃO: Adiciona à lista dentro de bloco synchronized
        synchronized (targetLock) {
            targets.add(target);
        }

        target.start();
    }

    /**
     * Adiciona um canhão ao jogo.
     *
     * TRATAMENTO DE EXCEÇÕES: Lança JogoException se:
     * - A posição está fora dos limites da tela
     * - O número máximo de canhões (10) foi excedido
     *
     * SINCRONIZAÇÃO: O bloco synchronized garante que a verificação
     * de limite + adição é atômica (outro thread não pode adicionar
     * entre a verificação e a inserção).
     *
     * @param x posição X do canhão
     * @param y posição Y do canhão
     * @throws JogoException se a posição é inválida ou limite excedido
     */
    public void addCannon(float x, float y) throws JogoException {
        // Validação de posição — lança exceção personalizada com mensagem descritiva
        if (x < 0 || x > screenWidth || y < 0 || y > screenHeight) {
            throw new JogoException(
                "Posição inválida para canhão: (" + x + ", " + y + "). " +
                "Deve estar dentro dos limites da tela (0-" + screenWidth + ", 0-" + screenHeight + ")."
            );
        }

        // SINCRONIZAÇÃO / REGIÃO CRÍTICA: Verificação + adição atômica
        synchronized (cannonLock) {
            if (cannons.size() >= MAX_CANNONS) {
                throw new JogoException(
                    "Número máximo de canhões atingido (" + MAX_CANNONS + "). " +
                    "Remova um canhão antes de adicionar outro."
                );
            }

            Cannon cannon = new Cannon(x, y, this);

            // Aplica penalidade de taxa de disparo acima do limiar
            // Canhões além do 5º disparam mais lentamente (penalidade)
            if (cannons.size() >= CANNON_PENALTY_THRESHOLD) {
                float penalty = 1.0f + (cannons.size() - CANNON_PENALTY_THRESHOLD + 1) * 0.5f;
                cannon.applyFireRatePenalty(penalty);
            }

            cannons.add(cannon);

            // Se o jogo já está rodando, inicia a thread do canhão imediatamente
            if (running) {
                cannon.start();
            }
        }
    }

    /**
     * Adiciona um projétil à lista.
     *
     * SINCRONIZAÇÃO: Protege a lista de projéteis com synchronized
     * para evitar ConcurrentModificationException quando múltiplos
     * canhões disparam simultaneamente.
     *
     * @param projectile projétil a ser adicionado
     * @throws JogoException se o projétil é nulo
     */
    public void addProjectile(Projectile projectile) throws JogoException {
        if (projectile == null) {
            throw new JogoException("Projétil não pode ser nulo.");
        }

        projectile.setScreenWidth(screenWidth);
        projectile.setScreenHeight(screenHeight);

        synchronized (projectileLock) {
            projectiles.add(projectile);
        }
    }

    /**
     * Retorna uma cópia segura da lista de alvos ativos.
     * Usada pelos canhões para buscar o alvo mais próximo.
     *
     * SINCRONIZAÇÃO: A cópia é feita atomicamente dentro do bloco
     * synchronized. A lista original não é exposta — o canhão
     * trabalha com uma cópia snapshot e não precisa manter o lock
     * durante toda a busca (melhora concorrência).
     *
     * @return cópia snapshot da lista de alvos
     */
    public List<Target> getTargetsSafe() {
        synchronized (targetLock) {
            return new ArrayList<>(targets);
        }
    }

    /**
     * Verifica colisão de um projétil com todos os alvos ativos.
     *
     * REGIÃO CRÍTICA: Este é o ponto mais crítico de sincronização.
     * O bloco synchronized(targetLock) garante que APENAS UM PROJÉTIL
     * POR VEZ pode verificar e modificar o estado dos alvos.
     *
     * Sem esta proteção, dois projéteis poderiam simultaneamente:
     * 1. Verificar que o mesmo alvo está ativo → ambos retornam true
     * 2. Desativar o alvo → o score seria incrementado 2x indevidamente
     *
     * Esta é uma CONDIÇÃO DE CORRIDA clássica resolvida com exclusão mútua.
     *
     * @param projectile projétil a verificar colisão
     */
    public void checkProjectileCollision(Projectile projectile) {
        if (!projectile.isActive()) return;

        // REGIÃO CRÍTICA: Lock exclusivo sobre a lista de alvos
        // Garante que verificação + desativação do alvo é ATÔMICA
        synchronized (targetLock) {
            for (Target target : targets) {
                if (target.isActive() && projectile.checkCollision(target)) {
                    // Desativa alvo e projétil atomicamente dentro do lock
                    target.setActive(false);
                    projectile.setActive(false);

                    // Incrementa pontuação (acesso seguro — dentro do lock)
                    score += target.getScoreValue();

                    if (listener != null) {
                        listener.onScoreChanged(score);
                    }

                    // Solicita redesenho para refletir a destruição no Canvas
                    requestRedraw();

                    break; // Um projétil só pode atingir um alvo por disparo
                }
            }
        }
    }

    /**
     * Remove entidades inativas das listas para liberar memória.
     * Chamado periodicamente pelo onDraw() da GameView.
     *
     * SINCRONIZAÇÃO: Cada lista é limpa com seu respectivo lock
     * para evitar ConcurrentModificationException.
     */
    public void cleanupInactiveEntities() {
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

    /**
     * Cópias seguras das listas para renderização no onDraw().
     * O onDraw() trabalha com snapshots — não mantém locks durante o desenho.
     */
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
