package com.autotarget.game.model;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
public class Canhao extends Thread {
    private float x, y;
    private Jogo jogo;
    private boolean ativo = true;

    public Canhao(float x, float y, Jogo jogo) {
        this.x = x;
        this.y = y;
        this.jogo = jogo;
    }

    @Override
    public void run() {
        while (ativo) {
            disparar();
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    private void disparar() {

        if (jogo.getAlvos().isEmpty()) return;

        Alvo alvo = jogo.getAlvos().get(0); // pega o primeiro alvo

        float dx = alvo.getX() - x;
        float dy = alvo.getY() - y;

        float distancia = (float) Math.sqrt(dx * dx + dy * dy);

        // normaliza
        dx = dx / distancia;
        dy = dy / distancia;

        float velocidade = 15;

        Projetil p = new Projetil(x, y, dx * velocidade, dy * velocidade);

        synchronized (jogo.getProjeteis()) {
            jogo.getProjeteis().add(p);
        }

        new Thread(p).start();
    }



    public void draw(Canvas canvas) {
        Paint paint = new Paint();
        paint.setColor(Color.GREEN);

        canvas.drawRect(x - 30, y - 30, x + 30, y + 30, paint);
    }
}
