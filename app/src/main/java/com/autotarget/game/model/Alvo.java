package com.autotarget.game.model;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import java.util.Random;

public class Alvo implements Runnable {
    private float x, y; // Posição do alvo
    private float raio; // Raio do alvo
    private float velocidadeX, velocidadeY; // Velocidade do alvo
    private boolean ativo; // Se o alvo está ativo
    private Paint paint; // Objeto Paint para desenhar o alvo
    private int screenWidth, screenHeight; // Dimensões da tela

    private static final Random random = new Random();

    public Alvo(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.raio = 80; // Tamanho fixo para o alvo
        this.ativo = true;

        // Posição inicial aleatória dentro da tela
        this.x = random.nextFloat() * (screenWidth - 2 * raio) + raio;
        this.y = random.nextFloat() * (screenHeight - 2 * raio) + raio;

        // Velocidade aleatória
        this.velocidadeX = 15*(random.nextFloat() * 10 - 5); // Entre -5 e 5
        this.velocidadeY = 15*(random.nextFloat() * 10 - 5); // Entre -5 e 5

        this.paint = new Paint();
        this.paint.setColor(Color.RED);
    }

    public void move() {
        if (!ativo) return;

        x += velocidadeX;
        y += velocidadeY;

        // Colisão com as bordas da tela
        if (x - raio < 0 || x + raio > screenWidth) {
            velocidadeX *= -1; // Inverte a direção horizontal
            // Garante que o alvo não saia da tela
            if (x - raio < 0) x = raio;
            if (x + raio > screenWidth) x = screenWidth - raio;
        }
        if (y - raio < 0 || y + raio > screenHeight) {
            velocidadeY *= -1; // Inverte a direção vertical
            // Garante que o alvo não saia da tela
            if (y - raio < 0) y = raio;
            if (y + raio > screenHeight) y = screenHeight - raio;
        }
    }

    public void draw(Canvas canvas) {
        if (ativo) {
            canvas.drawCircle(x, y, raio, paint);
        }
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    @Override
    public void run() {
        while (isAtivo()) {
            move();
            try {
                Thread.sleep(50); // Atualiza a cada 50ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ativo = false;
            }
        }
    }
    //{
    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getRaio() {
        return raio;
    }



    public boolean colideCom(float px, float py) {
        float dx = this.x - px;
        float dy = this.y - py;

        float distancia = (float) Math.sqrt(dx * dx + dy * dy);

        return distancia < this.raio; // ou tamanho do alvo
    }

}

