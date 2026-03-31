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
import com.ufla.autotarget.model.CommonTarget;
import com.ufla.autotarget.model.FastTarget;
import com.ufla.autotarget.model.Projectile;
import com.ufla.autotarget.model.Target;

import java.util.List;

/**
 * View customizada que renderiza o jogo estendendo View.
 *
 * SINCRONIZAÇÃO COM UI: Esta View é redesenhada pela UI Thread quando
 * o método onDraw() é chamado. As threads secundárias (alvos, canhões,
 * projéteis) NÃO acessam o Canvas diretamente. Em vez disso, elas
 * chamam postInvalidate() (via GameEngine.requestRedraw()) para solicitar
 * que a UI Thread redesenhe a tela no próximo ciclo de renderização.
 *
 * Isso garante:
 * 1. Fluidez do jogo — a UI Thread gerencia o desenho de forma segura
 * 2. Sem exceções de acesso à UI — apenas a UI Thread toca no Canvas
 * 3. As threads do jogo ficam livres para atualizar posições sem esperar
 */
public class GameView extends View {

    // Referência ao motor do jogo
    private GameEngine engine;

    // Objetos de pintura reutilizáveis (evita alocação durante onDraw)
    private Paint targetPaint;
    private Paint targetGlowPaint;
    private Paint cannonPaint;
    private Paint cannonBarrelPaint;
    private Paint projectilePaint;
    private Paint projectileGlowPaint;
    private Paint hudPaint;
    private Paint hudValuePaint;
    private Paint gridPaint;
    private Paint dividerPaint;

    // Caminho reutilizável para desenhar triângulos (canhões)
    private Path cannonPath;

    // Contador de frames para animações
    private long frameCount = 0;

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
     * Inicializa os objetos de pintura.
     * Feito uma única vez para evitar alocações repetidas durante onDraw().
     */
    private void init() {
        // Habilita o desenho customizado
        setWillNotDraw(false);

        cannonPath = new Path();

        // ---- Pintura dos alvos ----
        targetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        targetPaint.setStyle(Paint.Style.FILL);

        targetGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        targetGlowPaint.setStyle(Paint.Style.FILL);

        // ---- Pintura do canhão ----
        cannonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cannonPaint.setStyle(Paint.Style.FILL);
        cannonPaint.setColor(0xFF66BB6A); // Verde

        cannonBarrelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cannonBarrelPaint.setStyle(Paint.Style.FILL);
        cannonBarrelPaint.setColor(0xFF388E3C);

        // ---- Pintura do projétil ----
        projectilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        projectilePaint.setStyle(Paint.Style.FILL);
        projectilePaint.setColor(0xFFFFEE58); // Amarelo

        projectileGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        projectileGlowPaint.setStyle(Paint.Style.FILL);

        // ---- Pintura do HUD ----
        hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hudPaint.setColor(0xAAFFFFFF);
        hudPaint.setTextSize(36);
        hudPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        hudValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hudValuePaint.setTextSize(40);
        hudValuePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        // ---- Grade decorativa ----
        gridPaint = new Paint();
        gridPaint.setColor(0x15FFFFFF);
        gridPaint.setStrokeWidth(1);

        dividerPaint = new Paint();
        dividerPaint.setColor(0x40FFFFFF);
        dividerPaint.setStrokeWidth(2);
        dividerPaint.setStyle(Paint.Style.STROKE);
        float[] intervals = {10, 10};
        dividerPaint.setPathEffect(new android.graphics.DashPathEffect(intervals, 0));
    }

    /**
     * Define a referência ao GameEngine.
     */
    public void setEngine(GameEngine engine) {
        this.engine = engine;
    }

    /**
     * Chamado quando o tamanho da View muda (incluindo a primeira medição).
     * Informa ao GameEngine as dimensões reais da tela para posicionamento.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (engine != null) {
            engine.setScreenDimensions(w, h);
        }
    }

    // ==================== Renderização via onDraw (UI Thread) ====================

    /**
     * Método principal de desenho — chamado pela UI Thread.
     *
     * POSTINVALIDATE: Este método é acionado indiretamente pelas threads
     * secundárias que chamam postInvalidate() após atualizarem suas posições.
     * O Android enfileira a chamada e executa onDraw() na UI Thread de forma
     * segura, sem risco de exceções CalledFromWrongThreadException.
     *
     * As listas são obtidas como cópias seguras (synchronized) do GameEngine
     * para que o desenho não precise manter locks durante toda a operação.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (engine == null || canvas == null) return;

        frameCount++;

        // Fundo com gradiente e grade decorativa
        drawBackground(canvas);

        // Obtém cópias seguras das listas de entidades
        // (sincronizado internamente pelo GameEngine)
        List<Target> targets = engine.getTargetsForRender();
        List<Cannon> cannons = engine.getCannonsForRender();
        List<Projectile> projectiles = engine.getProjectilesForRender();

        // Desenha entidades em ordem de profundidade
        // (alvos atrás, canhões no meio, projéteis na frente)
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

        // Desenha o HUD (pontuação, energia, tempo)
        drawHUD(canvas);

        // Limpa entidades inativas periodicamente (a cada ~1 segundo)
        if (frameCount % 60 == 0) {
            engine.cleanupInactiveEntities();
        }
    }

    /**
     * Desenha o fundo do jogo com gradiente escuro e grade sutil.
     */
    private void drawBackground(Canvas canvas) {
        int w = canvas.getWidth();
        int h = canvas.getHeight();

        // Gradiente vertical escuro
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

        // Linha divisória central (campo esquerdo vs direito)
        canvas.drawLine(w / 2f, 0, w / 2f, h, dividerPaint);
    }

    /**
     * Desenha um alvo como um círculo com efeito de brilho (glow).
     * POLIMORFISMO VISUAL: CommonTarget = azul claro, FastTarget = vermelho.
     * A cor é determinada pelo tipo concreto do alvo via instanceof.
     */
    private void drawTarget(Canvas canvas, Target target) {
        float x = target.getX();
        float y = target.getY();
        float r = target.getRadius();

        // Animação de pulsação sutil baseada no frame
        float pulse = 1.0f + 0.08f * (float) Math.sin(frameCount * 0.05);
        float drawRadius = r * pulse;

        // Cor baseada no tipo concreto do alvo (POLIMORFISMO visual)
        int color;
        int glowColor;
        if (target instanceof FastTarget) {
            color = 0xFFEF5350; // Vermelho para alvo rápido
            glowColor = 0x40EF5350;
        } else {
            color = 0xFF4FC3F7; // Azul claro para alvo comum
            glowColor = 0x404FC3F7;
        }

        // Efeito de brilho (glow) ao redor do alvo
        targetGlowPaint.setShader(new RadialGradient(x, y, drawRadius * 2,
                glowColor, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, drawRadius * 2, targetGlowPaint);

        // Corpo do alvo com gradiente radial (efeito 3D)
        targetPaint.setShader(new RadialGradient(x - r * 0.3f, y - r * 0.3f, drawRadius,
                Color.WHITE, color, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, drawRadius, targetPaint);

        // Anel externo decorativo
        Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(2);
        ringPaint.setColor(color);
        ringPaint.setAlpha(180);
        canvas.drawCircle(x, y, drawRadius + 4, ringPaint);
    }

    /**
     * Desenha um canhão como um triângulo com um cano (barrel) apontando
     * para o ângulo do alvo mais próximo.
     */
    private void drawCannon(Canvas canvas, Cannon cannon) {
        float cx = cannon.getCannonX();
        float cy = cannon.getCannonY();
        float size = cannon.getSize();
        float angle = cannon.getAngle();

        // Construção do triângulo base do canhão
        cannonPath.reset();

        // Vértice frontal (ponta apontando para o alvo)
        float frontX = cx + (float) Math.cos(angle) * size;
        float frontY = cy + (float) Math.sin(angle) * size;

        // Vértices traseiros (base do triângulo)
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

        // Glow verde ao redor do canhão
        Paint cannonGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
        cannonGlow.setShader(new RadialGradient(cx, cy, size * 1.5f,
                0x3066BB6A, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, size * 1.5f, cannonGlow);

        // Desenha o corpo (triângulo) com gradiente linear
        cannonPaint.setShader(new LinearGradient(
                back1X, back1Y, frontX, frontY,
                0xFF81C784, 0xFF2E7D32, Shader.TileMode.CLAMP));
        canvas.drawPath(cannonPath, cannonPaint);

        // Desenha o cano (ligne na direção do ângulo de mira)
        float barrelEndX = cx + (float) Math.cos(angle) * size * 1.3f;
        float barrelEndY = cy + (float) Math.sin(angle) * size * 1.3f;
        cannonBarrelPaint.setStrokeWidth(6);
        cannonBarrelPaint.setStyle(Paint.Style.STROKE);
        cannonBarrelPaint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(cx, cy, barrelEndX, barrelEndY, cannonBarrelPaint);

        // Indicador central do canhão
        Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerPaint.setColor(0xFFFFFFFF);
        centerPaint.setAlpha(120);
        canvas.drawCircle(cx, cy, 5, centerPaint);
    }

    /**
     * Desenha um projétil como um pequeno círculo brilhante com glow.
     */
    private void drawProjectile(Canvas canvas, Projectile projectile) {
        float px = projectile.getProjectileX();
        float py = projectile.getProjectileY();
        float pr = projectile.getRadius();

        // Glow amarelo ao redor do projétil
        projectileGlowPaint.setShader(new RadialGradient(px, py, pr * 4,
                0x60FFEE58, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawCircle(px, py, pr * 4, projectileGlowPaint);

        // Corpo do projétil com gradiente radial (branco → amarelo)
        projectilePaint.setShader(new RadialGradient(px, py, pr,
                0xFFFFFFFF, 0xFFFFEE58, Shader.TileMode.CLAMP));
        canvas.drawCircle(px, py, pr, projectilePaint);
    }

    /**
     * Desenha o HUD (Heads-Up Display) com pontuação, energia e tempo.
     */
    private void drawHUD(Canvas canvas) {
        if (engine == null) return;

        float padding = 20;
        float y = 60;

        // Fundo semitransparente para o HUD
        Paint hudBg = new Paint();
        hudBg.setColor(0x80000000);
        RectF hudRect = new RectF(10, 10, canvas.getWidth() - 10, 120);
        canvas.drawRoundRect(hudRect, 16, 16, hudBg);

        // Pontuação (dourado)
        hudPaint.setColor(0xAAFFFFFF);
        canvas.drawText("Alvos: ", padding, y, hudPaint);
        hudValuePaint.setColor(0xFFFFD700);
        canvas.drawText(String.valueOf(engine.getScore()),
                padding + hudPaint.measureText("Alvos: "), y, hudValuePaint);

        // Energia (verde/laranja/vermelho conforme nível)
        float energyX = canvas.getWidth() / 2f - 40;
        hudPaint.setColor(0xAAFFFFFF);
        canvas.drawText("Energia: ", energyX, y, hudPaint);

        int energyColor = engine.getEnergy() > 500 ? 0xFF66BB6A :
                          engine.getEnergy() > 200 ? 0xFFFFB74D : 0xFFEF5350;
        hudValuePaint.setColor(energyColor);
        canvas.drawText(String.valueOf(engine.getEnergy()),
                energyX + hudPaint.measureText("Energia: "), y, hudValuePaint);

        // Tempo restante (azul)
        float timeX = canvas.getWidth() - 180;
        hudPaint.setColor(0xAAFFFFFF);
        canvas.drawText("Tempo: ", timeX, y, hudPaint);
        hudValuePaint.setColor(0xFF4FC3F7);
        canvas.drawText(engine.getRemainingTime() + "s",
                timeX + hudPaint.measureText("Tempo: "), y, hudValuePaint);

        // Barra de energia visual
        float barY = 80;
        float barHeight = 8;
        float barWidth = canvas.getWidth() - 2 * padding - 20;
        float energyRatio = Math.max(0, Math.min(1, engine.getEnergy() / 1000f));

        Paint barBgPaint = new Paint();
        barBgPaint.setColor(0x40FFFFFF);
        canvas.drawRoundRect(padding, barY, padding + barWidth, barY + barHeight, 4, 4, barBgPaint);

        Paint barFillPaint = new Paint();
        barFillPaint.setShader(new LinearGradient(padding, barY,
                padding + barWidth * energyRatio, barY + barHeight,
                0xFF66BB6A, energyColor, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(padding, barY,
                padding + barWidth * energyRatio, barY + barHeight, 4, 4, barFillPaint);

        // Overlay de fim de jogo
        if (!engine.isRunning() && engine.getScore() > 0) {
            Paint overlayPaint = new Paint();
            overlayPaint.setColor(0xAA000000);
            canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), overlayPaint);

            Paint gameOverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            gameOverPaint.setColor(0xFFFFFFFF);
            gameOverPaint.setTextSize(64);
            gameOverPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            gameOverPaint.setTextAlign(Paint.Align.CENTER);

            canvas.drawText("FIM DE JOGO!",
                    canvas.getWidth() / 2f, canvas.getHeight() / 2f - 40, gameOverPaint);

            gameOverPaint.setTextSize(48);
            gameOverPaint.setColor(0xFFFFD700);
            canvas.drawText("Pontuação: " + engine.getScore(),
                    canvas.getWidth() / 2f, canvas.getHeight() / 2f + 40, gameOverPaint);
        }
    }
}
