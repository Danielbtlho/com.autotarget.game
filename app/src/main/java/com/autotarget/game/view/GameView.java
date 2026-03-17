package com.autotarget.game.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;

import com.autotarget.game.model.Canhao;
import com.autotarget.game.model.Jogo;
import com.autotarget.game.model.Alvo;
import com.autotarget.game.model.Projetil;

public class GameView extends View {
    private Jogo jogo;
    private Thread gameThread;
    private boolean running = false;

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // Inicializa o jogo com as dimensões da tela (ainda não disponíveis aqui)
        // As dimensões serão passadas quando a view for desenhada pela primeira vez
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (jogo != null) {
            startGameLoop();
        }
    }

    private void startGameLoop() {
        running = true;
        gameThread = new Thread(() -> {
            while (running) {
                // O movimento dos alvos já é tratado em suas próprias threads
                // Aqui, apenas forçamos o redesenho da tela
                postInvalidate(); // Solicita que a view seja redesenhada na thread da UI
                try {
                    Thread.sleep(16); // Aproximadamente 60 FPS
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running = false;
                }
            }
        });
        gameThread.start();
    }

    public void stopGameLoop() {
        running = false;
        if (gameThread != null) {
            gameThread.interrupt();
            try {
                gameThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (jogo != null) {
            jogo.pararJogo();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.BLACK);

        if (jogo != null) {

            // desenha alvos
            for (Alvo alvo : jogo.getAlvos()) {
                alvo.draw(canvas);
            }

            for (Canhao c : jogo.getCanhoes()) {
                c.draw(canvas);
            }

            // 👇 adiciona isso
            for (Projetil p : jogo.getProjeteis()) {
                canvas.drawCircle(p.getX(), p.getY(), 10, new android.graphics.Paint());
            }
        }
    }

    public Jogo getJogo() {
        return jogo;
    }
    public void setJogo(Jogo jogo) {
        this.jogo = jogo;
    }
}
