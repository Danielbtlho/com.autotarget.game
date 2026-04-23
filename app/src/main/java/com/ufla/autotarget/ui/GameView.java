package com.ufla.autotarget.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import com.ufla.autotarget.engine.GameEngine;
import com.ufla.autotarget.model.Cannon;
import com.ufla.autotarget.model.Projectile;
import com.ufla.autotarget.model.Target;

import java.util.List;

/**
 * View customizada que renderiza o jogo e o HUD (Heads-Up Display).
 *
 * ATUALIZAÇÃO SÍNCRONA COM O MOTOR DO JOGO:
 * O GameEngine atualiza posições e estado das entidades em threads secundárias.
 * Após cada atualização, as threads chamam engine.requestRedraw() que invoca
 * postInvalidate() nesta View. A UI Thread então chama onDraw() e obtém
 * cópias snapshot (sincronizadas) das listas de entidades. O onDraw() itera
 * sobre essas CÓPIAS — não sobre as listas originais — eliminando risco de
 * ConcurrentModificationException mesmo com muitas threads ativas.
 *
 * FLUIDEZ: O uso de postInvalidate() evita flicker (cintilação) porque:
 * 1. Apenas a UI Thread desenha no Canvas (não há race condition visual)
 * 2. O framework Android gerencia o double-buffering automaticamente
 * 3. Objetos Paint são reutilizados (sem alocação durante onDraw)
 *
 * POLIMORFISMO VISUAL (Princípio Open/Closed): As cores de cada tipo de alvo
 * são obtidas via target.getColor() e target.getGlowColor(), sem usar instanceof.
 * Novos tipos de alvos podem ser adicionados sem modificar esta View.
 */
public class GameView extends View {

    // Referência ao motor do jogo
    private GameEngine engine;

    // =====================================================================
    // OBJETOS PAINT REUTILIZÁVEIS (alocados uma vez, reutilizados em onDraw)
    // Evita criar objetos durante onDraw() que causariam GC e frame drops.
    // =====================================================================
    private Paint targetPaint;
    private Paint targetGlowPaint;
    private Paint targetRingPaint;
    private Paint cannonPaint;
    private Paint cannonBarrelPaint;
    private Paint projectilePaint;
    private Paint projectileGlowPaint;
    private Paint gridPaint;

    // Paints do HUD
    private Paint hudBgPaint;
    private Paint hudTitlePaint;
    private Paint hudLabelPaint;
    private Paint hudValuePaint;
    private Paint hudStatusPaint;
    private Paint hudLogPaint;
    private Paint hudLogBgPaint;
    private Paint barBgPaint;
    private Paint barFillPaint;
    private Paint legendBoxPaint;

    // Paints especiais
    private Paint overlayPaint;
    private Paint gameOverTitlePaint;
    private Paint gameOverScorePaint;

    // Caminho reutilizável para triângulos dos canhões
    private Path cannonPath;

    // Contador de frames para micro-animações
    private long frameCount = 0;

    // =====================================================================
    // CORES DO POLIMORFISMO VISUAL
    // CommonTarget = Ciano/Azul (alvo lento, fácil de acertar)
    // FastTarget = Vermelho/Magenta (alvo rápido, difícil de acertar)
    // =====================================================================
    private static final int COLOR_COMMON_TARGET = 0xFF4FC3F7;   // Ciano
    private static final int COLOR_COMMON_GLOW = 0x404FC3F7;
    private static final int COLOR_FAST_TARGET = 0xFFEF5350;     // Vermelho
    private static final int COLOR_FAST_GLOW = 0x40EF5350;
    private static final int COLOR_CANNON = 0xFF66BB6A;          // Verde
    private static final int COLOR_PROJECTILE = 0xFFFFEE58;      // Amarelo
    private static final int COLOR_GOLD = 0xFFFFD700;            // Dourado (score)
    private static final int COLOR_HUD_BG = 0xCC0D0D1A;         // Fundo HUD

    public GameView(Context context) {
        super(context);
        init();
    }

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GameView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * Inicializa todos os objetos Paint UMA ÚNICA VEZ.
     * Reutilizá-los em onDraw() evita alocações repetidas e GC,
     * garantindo fluidez na renderização (~60 FPS).
     */
    private void init() {
        setWillNotDraw(false);
        cannonPath = new Path();

        // ---- Alvos ----
        targetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        targetPaint.setStyle(Paint.Style.FILL);

        targetGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        targetGlowPaint.setStyle(Paint.Style.FILL);

        targetRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        targetRingPaint.setStyle(Paint.Style.STROKE);
        targetRingPaint.setStrokeWidth(2);

        // ---- Canhão ----
        cannonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cannonPaint.setStyle(Paint.Style.FILL);

        cannonBarrelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cannonBarrelPaint.setColor(0xFF388E3C);
        cannonBarrelPaint.setStrokeCap(Paint.Cap.ROUND);

        // ---- Projétil ----
        projectilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        projectilePaint.setStyle(Paint.Style.FILL);

        projectileGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        projectileGlowPaint.setStyle(Paint.Style.FILL);

        // ---- Grade decorativa ----
        gridPaint = new Paint();
        gridPaint.setColor(0x12FFFFFF);
        gridPaint.setStrokeWidth(1);

        // ---- HUD ----
        hudBgPaint = new Paint();
        hudBgPaint.setColor(COLOR_HUD_BG);

        hudTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hudTitlePaint.setColor(COLOR_GOLD);
        hudTitlePaint.setTextSize(28);
        hudTitlePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        hudLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hudLabelPaint.setColor(0xBBFFFFFF);
        hudLabelPaint.setTextSize(24);
        hudLabelPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));

        hudValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hudValuePaint.setTextSize(26);
        hudValuePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        hudStatusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hudStatusPaint.setTextSize(22);
        hudStatusPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC));

        hudLogPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hudLogPaint.setTextSize(20);
        hudLogPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        hudLogPaint.setColor(0xAAFFFFFF);

        hudLogBgPaint = new Paint();
        hudLogBgPaint.setColor(0x99000000);

        barBgPaint = new Paint();
        barBgPaint.setColor(0x40FFFFFF);

        barFillPaint = new Paint();

        legendBoxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        legendBoxPaint.setStyle(Paint.Style.FILL);

        // ---- Overlay fim de jogo ----
        overlayPaint = new Paint();
        overlayPaint.setColor(0xBB000000);

        gameOverTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gameOverTitlePaint.setColor(0xFFFFFFFF);
        gameOverTitlePaint.setTextSize(60);
        gameOverTitlePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        gameOverTitlePaint.setTextAlign(Paint.Align.CENTER);

        gameOverScorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gameOverScorePaint.setColor(COLOR_GOLD);
        gameOverScorePaint.setTextSize(44);
        gameOverScorePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        gameOverScorePaint.setTextAlign(Paint.Align.CENTER);
    }

    /** Define a referência ao GameEngine. */
    public void setEngine(GameEngine engine) {
        this.engine = engine;
    }

    /**
     * Chamado quando a View muda de tamanho. Informa ao engine as dimensões reais.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (engine != null) {
            engine.setScreenDimensions(w, h);
        }
    }

    // =====================================================================
    // RENDERIZAÇÃO VIA onDraw() — EXECUTADO PELA UI THREAD
    // =====================================================================

    /**
     * Método principal de desenho — chamado APENAS pela UI Thread.
     *
     * PROTEÇÃO CONTRA ConcurrentModificationException:
     * As listas de entidades são obtidas como CÓPIAS SNAPSHOT via
     * getTargetsForRender(), getCannonsForRender(), getProjectilesForRender().
     * Cada método usa synchronized internamente para criar a cópia.
     * O onDraw() itera sobre essas cópias locais — as threads do jogo
     * podem modificar as listas originais livremente durante o desenho
     * sem causar ConcurrentModificationException.
     *
     * POSTINVALIDATE: Este método é acionado indiretamente pelas threads
     * secundárias que chamam postInvalidate(). A UI Thread processa o
     * pedido e chama onDraw() de forma segura, sem flicker nem cintilação.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (engine == null || canvas == null) return;

        frameCount++;

        // ---- Fundo com gradiente e grade decorativa ----
        drawBackground(canvas);

        // ---- Obtém CÓPIAS SEGURAS das listas (synchronized internamente) ----
        // Isso elimina risco de ConcurrentModificationException porque
        // o onDraw() trabalha com snapshots locais, não com as listas originais.
        List<Target> targets = engine.getTargetsForRender();
        List<Cannon> cannons = engine.getCannonsForRender();
        List<Projectile> projectiles = engine.getProjectilesForRender();

        // ---- Desenha entidades em ordem de profundidade ----
        // Alvos (fundo) → Canhões (meio) → Projéteis (frente)
        for (Target target : targets) {
            if (target.isActive()) {
                drawTarget(canvas, target);
            }
        }

        for (Cannon cannon : cannons) {
            if (cannon.isActive()) {
                drawCannon(canvas, cannon);
            }
        }

        for (Projectile projectile : projectiles) {
            if (projectile.isActive()) {
                drawProjectile(canvas, projectile);
            }
        }

        // ---- HUD completo (painel informativo, threads, status, log) ----
        drawHUD(canvas);

        // ---- Legenda de cores (Polimorfismo Visual) ----
        drawLegend(canvas);

        // ---- Log visual das últimas ações ----
        drawActionLog(canvas);

        // ---- Overlay de fim de jogo ----
        drawGameOverOverlay(canvas);

        // ---- Limpeza periódica de entidades inativas (~1 segundo) ----
        if (frameCount % 60 == 0) {
            engine.cleanupInactiveEntities();
        }
    }

    // =====================================================================
    // FUNDO DO JOGO
    // =====================================================================

    /**
     * Desenha fundo com gradiente escuro e grade sutil para profundidade.
     */
    private void drawBackground(Canvas canvas) {
        int w = canvas.getWidth();
        int h = canvas.getHeight();

        // Gradiente vertical escuro (roxo profundo → azul escuro)
        Paint bgGradient = new Paint();
        bgGradient.setShader(new LinearGradient(0, 0, 0, h,
                0xFF0F0C29, 0xFF302B63, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, bgGradient);

        // Grade decorativa para dar profundidade visual
        for (int x = 0; x < w; x += 50) {
            canvas.drawLine(x, 0, x, h, gridPaint);
        }
        for (int y = 0; y < h; y += 50) {
            canvas.drawLine(0, y, w, y, gridPaint);
        }
    }

    // =====================================================================
    // RENDERIZAÇÃO DAS ENTIDADES
    // =====================================================================

    /**
     * Desenha um alvo com glow e pulsação.
     *
     * POLIMORFISMO VISUAL:
     * - CommonTarget → Ciano/Azul (alvo lento, previsível)
     * - FastTarget → Vermelho/Magenta (alvo rápido, errático)
     *
     * POLIMORFISMO VISUAL (Open/Closed Principle):
     * - As cores são obtidas via target.getColor() e target.getGlowColor()
     * - Cada subclasse (CommonTarget, FastTarget, etc.) define suas próprias cores
     * - A View NÃO precisa conhecer os tipos concretos (sem instanceof)
     * - Novos tipos de alvo podem ser adicionados sem alterar este método
     */
    private void drawTarget(Canvas canvas, Target target) {
        float x = target.getX();
        float y = target.getY();
        float r = target.getRadius();

        // Micro-animação: pulsação sutil baseada no frame count
        float pulse = 1.0f + 0.08f * (float) Math.sin(frameCount * 0.05);
        float drawRadius = r * pulse;

        // POLIMORFISMO: Cores delegadas ao próprio alvo (sem instanceof)
        int color = target.getColor();
        int glowColor = target.getGlowColor();

        // Efeito glow (brilho) ao redor do alvo
        targetGlowPaint.setShader(new RadialGradient(x, y, drawRadius * 2,
                glowColor, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, drawRadius * 2, targetGlowPaint);

        // Corpo do alvo com gradiente radial (efeito 3D)
        targetPaint.setShader(new RadialGradient(x - r * 0.3f, y - r * 0.3f, drawRadius,
                Color.WHITE, color, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, drawRadius, targetPaint);

        // Anel externo decorativo
        targetRingPaint.setColor(color);
        targetRingPaint.setAlpha(180);
        canvas.drawCircle(x, y, drawRadius + 4, targetRingPaint);
    }

    /**
     * Desenha um canhão como triângulo verde com cano direcional.
     */
    private void drawCannon(Canvas canvas, Cannon cannon) {
        float cx = cannon.getCannonX();
        float cy = cannon.getCannonY();
        float size = cannon.getSize();
        float angle = cannon.getAngle();

        // Triângulo base
        cannonPath.reset();
        float frontX = cx + (float) Math.cos(angle) * size;
        float frontY = cy + (float) Math.sin(angle) * size;
        float backAngle1 = angle + (float) Math.PI * 0.75f;
        float backAngle2 = angle - (float) Math.PI * 0.75f;
        float back1X = cx + (float) Math.cos(backAngle1) * size * 0.6f;
        float back1Y = cy + (float) Math.sin(backAngle1) * size * 0.6f;
        float back2X = cx + (float) Math.cos(backAngle2) * size * 0.6f;
        float back2Y = cy + (float) Math.sin(backAngle2) * size * 0.6f;

        cannonPath.moveTo(frontX, frontY);
        cannonPath.lineTo(back1X, back1Y);
        cannonPath.lineTo(back2X, back2Y);
        cannonPath.close();

        // Glow verde ao redor
        targetGlowPaint.setShader(new RadialGradient(cx, cy, size * 1.5f,
                0x3066BB6A, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, size * 1.5f, targetGlowPaint);

        // Corpo com gradiente linear
        cannonPaint.setShader(new LinearGradient(
                back1X, back1Y, frontX, frontY,
                0xFF81C784, 0xFF2E7D32, Shader.TileMode.CLAMP));
        canvas.drawPath(cannonPath, cannonPaint);

        // Cano (barrel) na direção do ângulo de mira
        float barrelEndX = cx + (float) Math.cos(angle) * size * 1.3f;
        float barrelEndY = cy + (float) Math.sin(angle) * size * 1.3f;
        cannonBarrelPaint.setStrokeWidth(6);
        cannonBarrelPaint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(cx, cy, barrelEndX, barrelEndY, cannonBarrelPaint);

        // Indicador central
        cannonBarrelPaint.setStyle(Paint.Style.FILL);
        cannonBarrelPaint.setColor(0x78FFFFFF);
        canvas.drawCircle(cx, cy, 5, cannonBarrelPaint);
        cannonBarrelPaint.setColor(0xFF388E3C); // restaura cor
    }

    /**
     * Desenha projétil como círculo brilhante amarelo com glow.
     */
    private void drawProjectile(Canvas canvas, Projectile projectile) {
        float px = projectile.getProjectileX();
        float py = projectile.getProjectileY();
        float pr = projectile.getRadius();

        // Glow amarelo
        projectileGlowPaint.setShader(new RadialGradient(px, py, pr * 4,
                0x60FFEE58, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawCircle(px, py, pr * 4, projectileGlowPaint);

        // Corpo com gradiente radial (branco → amarelo)
        projectilePaint.setShader(new RadialGradient(px, py, pr,
                0xFFFFFFFF, COLOR_PROJECTILE, Shader.TileMode.CLAMP));
        canvas.drawCircle(px, py, pr, projectilePaint);
    }

    // =====================================================================
    // HUD — HEADS-UP DISPLAY COMPLETO
    // Desenhado no topo do Canvas com informações do jogo em tempo real.
    // O feedback visual é atualizado de forma síncrona com o motor:
    // as threads chamam requestRedraw() → postInvalidate() → onDraw()
    // que lê os valores atualizados do engine e redesenha o HUD.
    // =====================================================================

    /**
     * Desenha o HUD completo no topo da tela, contendo:
     * - Placar: Alvos destruídos vs. Alvos que escaparam
     * - Energia: Valor numérico + barra visual colorida
     * - Tempo restante da partida
     * - Contador de threads: Alvos, Canhões e Projéteis ativos
     * - Status do sistema: "Aguardando", "Defesa Ativa", "Alvo Detectado"
     */
    private void drawHUD(Canvas canvas) {
        if (engine == null) return;

        int w = canvas.getWidth();
        float pad = 16;
        float lineH = 30;

        // ====================== PAINEL SUPERIOR (PLACAR / ENERGIA / TEMPO) ======================
        RectF topRect = new RectF(8, 8, w - 8, 130);
        canvas.drawRoundRect(topRect, 14, 14, hudBgPaint);

        float y = 38;

        // ---- Placar (Destruídos vs Escaparam) ----
        hudLabelPaint.setColor(0xBBFFFFFF);
        canvas.drawText("Destruídos:", pad, y, hudLabelPaint);
        hudValuePaint.setColor(COLOR_GOLD);
        canvas.drawText(String.valueOf(engine.getScore()),
                pad + hudLabelPaint.measureText("Destruídos:") + 8, y, hudValuePaint);

        float escX = w / 2f;
        canvas.drawText("Escaparam:", escX, y, hudLabelPaint);
        hudValuePaint.setColor(COLOR_FAST_TARGET);
        canvas.drawText(String.valueOf(engine.getEscapedTargets()),
                escX + hudLabelPaint.measureText("Escaparam:") + 8, y, hudValuePaint);

        // ---- Energia + Tempo na segunda linha ----
        y += lineH;

        hudLabelPaint.setColor(0xBBFFFFFF);
        canvas.drawText("Energia:", pad, y, hudLabelPaint);

        // Cor da energia varia conforme o nível (verde → laranja → vermelho)
        int energyVal = engine.getEnergy();
        int energyColor = energyVal > 500 ? 0xFF66BB6A :
                          energyVal > 200 ? 0xFFFFB74D : 0xFFEF5350;
        hudValuePaint.setColor(energyColor);
        canvas.drawText(String.valueOf(energyVal),
                pad + hudLabelPaint.measureText("Energia:") + 8, y, hudValuePaint);

        // Tempo restante (azul brilhante)
        float timeX = w - 180;
        canvas.drawText("Tempo:", timeX, y, hudLabelPaint);
        hudValuePaint.setColor(0xFF4FC3F7);
        canvas.drawText(engine.getRemainingTime() + "s",
                timeX + hudLabelPaint.measureText("Tempo:") + 8, y, hudValuePaint);

        // ---- Barra de energia visual (terceira linha) ----
        float barY = y + 10;
        float barH = 8;
        float barW = w - 2 * pad - 16;
        float ratio = Math.max(0, Math.min(1, energyVal / 1000f));

        canvas.drawRoundRect(pad, barY, pad + barW, barY + barH, 4, 4, barBgPaint);

        barFillPaint.setShader(new LinearGradient(pad, barY,
                pad + barW * ratio, barY + barH,
                0xFF66BB6A, energyColor, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(pad, barY, pad + barW * ratio, barY + barH, 4, 4, barFillPaint);

        // ====================== PAINEL DE THREADS E STATUS ======================
        float panel2Top = 138;
        RectF panel2 = new RectF(8, panel2Top, w - 8, panel2Top + 65);
        canvas.drawRoundRect(panel2, 14, 14, hudBgPaint);

        float y2 = panel2Top + 26;

        // ---- Contador de Threads ativas ----
        int tCount = engine.getActiveTargetCount();
        int cCount = engine.getCannonCount();
        int pCount = engine.getActiveProjectileCount();

        hudLabelPaint.setColor(0xBBFFFFFF);
        hudLabelPaint.setTextSize(20);

        // Indicadores visuais com cores para cada tipo de thread
        String threadsInfo = "Alvos:" + tCount + "  Canhões:" + cCount + "  Proj:" + pCount;

        // Alvos (ciano)
        canvas.drawText("Alvos:", pad, y2, hudLabelPaint);
        hudValuePaint.setColor(COLOR_COMMON_TARGET);
        hudValuePaint.setTextSize(22);
        float afterAlvos = pad + hudLabelPaint.measureText("Alvos:") + 4;
        canvas.drawText(String.valueOf(tCount), afterAlvos, y2, hudValuePaint);

        // Canhões (verde)
        float cStart = afterAlvos + hudValuePaint.measureText(String.valueOf(tCount)) + 20;
        canvas.drawText("Canhões:", cStart, y2, hudLabelPaint);
        hudValuePaint.setColor(COLOR_CANNON);
        float afterCanhoes = cStart + hudLabelPaint.measureText("Canhões:") + 4;
        canvas.drawText(String.valueOf(cCount), afterCanhoes, y2, hudValuePaint);

        // Projéteis (amarelo)
        float pStart = afterCanhoes + hudValuePaint.measureText(String.valueOf(cCount)) + 20;
        canvas.drawText("Projéteis:", pStart, y2, hudLabelPaint);
        hudValuePaint.setColor(COLOR_PROJECTILE);
        float afterProj = pStart + hudLabelPaint.measureText("Projéteis:") + 4;
        canvas.drawText(String.valueOf(pCount), afterProj, y2, hudValuePaint);

        // ---- Status do Sistema na segunda linha do painel ----
        float y2b = y2 + 26;
        String status = engine.getSystemStatus();

        // Cor do status baseada no estado
        int statusColor;
        switch (status) {
            case "Alvo Detectado": statusColor = COLOR_FAST_TARGET; break;
            case "Sem Defesa!":    statusColor = 0xFFFF9800; break;
            case "Defesa Ativa":   statusColor = COLOR_CANNON; break;
            default:               statusColor = 0xFFAAAAAA; break; // Aguardando
        }

        hudStatusPaint.setColor(statusColor);
        canvas.drawText("Status: " + status, pad, y2b, hudStatusPaint);

        // Restaura tamanhos padrão do paint
        hudLabelPaint.setTextSize(24);
        hudValuePaint.setTextSize(26);
    }

    // =====================================================================
    // LEGENDA DE CORES (POLIMORFISMO VISUAL)
    // Mostra ao jogador o significado de cada cor na tela.
    // =====================================================================

    /**
     * Desenha legenda de cores no canto inferior esquerdo.
     * Facilita a identificação visual dos tipos de entidades.
     */
    private void drawLegend(Canvas canvas) {
        if (engine == null || !engine.isRunning()) return;

        int w = canvas.getWidth();
        int h = canvas.getHeight();
        float pad = 16;
        float legendY = h - 100;
        float boxSize = 14;

        // Fundo semitransparente para a legenda
        RectF legendRect = new RectF(pad - 4, legendY - 20, 220, legendY + 44);
        canvas.drawRoundRect(legendRect, 8, 8, hudBgPaint);

        hudLogPaint.setTextSize(18);

        // CommonTarget (ciano)
        legendBoxPaint.setColor(COLOR_COMMON_TARGET);
        canvas.drawRoundRect(pad, legendY - 4, pad + boxSize, legendY - 4 + boxSize, 3, 3, legendBoxPaint);
        canvas.drawText("Alvo Comum (lento)", pad + boxSize + 8, legendY + 8, hudLogPaint);

        // FastTarget (vermelho)
        legendBoxPaint.setColor(COLOR_FAST_TARGET);
        canvas.drawRoundRect(pad, legendY + 18, pad + boxSize, legendY + 18 + boxSize, 3, 3, legendBoxPaint);
        canvas.drawText("Alvo Rápido (2x vel.)", pad + boxSize + 8, legendY + 30, hudLogPaint);

        hudLogPaint.setTextSize(20); // restaura
    }

    // =====================================================================
    // LOG VISUAL DAS ÚLTIMAS AÇÕES
    // Exibe as últimas ações do sistema diretamente no Canvas.
    // O log é sincronizado pelo GameEngine (actionLogLock).
    // =====================================================================

    /**
     * Desenha as últimas ações do jogo no canto inferior direito.
     * Exemplos: "Alvo Rápido destruído! +3", "Canhão #2 posicionado".
     */
    private void drawActionLog(Canvas canvas) {
        if (engine == null || !engine.isRunning()) return;

        // Obtém cópia segura do log (SYNCHRONIZED internamente)
        List<String> log = engine.getActionLog();
        if (log.isEmpty()) return;

        int w = canvas.getWidth();
        int h = canvas.getHeight();
        float pad = 12;
        float lineH = 22;
        int maxEntries = Math.min(log.size(), 5);

        // Calcula posição do painel
        float logHeight = maxEntries * lineH + 28;
        float logTop = h - 100 - logHeight;
        float logLeft = w - 290;

        // Fundo semitransparente do log
        RectF logRect = new RectF(logLeft - 4, logTop, w - 8, logTop + logHeight);
        canvas.drawRoundRect(logRect, 10, 10, hudLogBgPaint);

        // Título do log
        hudLabelPaint.setTextSize(18);
        hudLabelPaint.setColor(COLOR_GOLD);
        canvas.drawText("📋 Log de Ações:", logLeft + pad, logTop + 18, hudLabelPaint);
        hudLabelPaint.setColor(0xBBFFFFFF);
        hudLabelPaint.setTextSize(24); // restaura

        // Entradas do log (mais recente embaixo)
        hudLogPaint.setTextSize(17);
        for (int i = 0; i < maxEntries; i++) {
            String entry = log.get(log.size() - maxEntries + i);
            float entryY = logTop + 38 + i * lineH;

            // Destaca ações de destruição em verde
            if (entry.contains("destruído")) {
                hudLogPaint.setColor(COLOR_CANNON);
            } else if (entry.contains("surgiu")) {
                hudLogPaint.setColor(0xFF90CAF9);
            } else {
                hudLogPaint.setColor(0xAAFFFFFF);
            }

            canvas.drawText("▸ " + entry, logLeft + pad, entryY, hudLogPaint);
        }

        hudLogPaint.setTextSize(20);
        hudLogPaint.setColor(0xAAFFFFFF); // restaura
    }

    // =====================================================================
    // OVERLAY DE FIM DE JOGO
    // =====================================================================

    /**
     * Desenha overlay semitransparente com resultado final quando o jogo termina.
     */
    private void drawGameOverOverlay(Canvas canvas) {
        if (engine == null) return;
        if (engine.isRunning() || engine.getScore() == 0) return;

        int w = canvas.getWidth();
        int h = canvas.getHeight();

        // Overlay escuro sobre todo o canvas
        canvas.drawRect(0, 0, w, h, overlayPaint);

        // Painel central
        float panelW = 400;
        float panelH = 200;
        float panelLeft = (w - panelW) / 2;
        float panelTop = (h - panelH) / 2;

        hudBgPaint.setColor(0xEE1A1A2E);
        canvas.drawRoundRect(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH,
                20, 20, hudBgPaint);
        hudBgPaint.setColor(COLOR_HUD_BG); // restaura

        // Título
        canvas.drawText("FIM DE JOGO!", w / 2f, panelTop + 60, gameOverTitlePaint);

        // Score
        canvas.drawText("Pontuação: " + engine.getScore(),
                w / 2f, panelTop + 115, gameOverScorePaint);

        // Escaparam
        gameOverScorePaint.setTextSize(30);
        gameOverScorePaint.setColor(COLOR_FAST_TARGET);
        canvas.drawText("Escaparam: " + engine.getEscapedTargets(),
                w / 2f, panelTop + 155, gameOverScorePaint);

        // Restaura
        gameOverScorePaint.setTextSize(44);
        gameOverScorePaint.setColor(COLOR_GOLD);
    }
}
