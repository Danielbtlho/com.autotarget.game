package com.autotarget.game.model;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class Projetil implements Runnable {

    private float x, y;
    private float dx, dy;
    private boolean ativo = true;

    public Projetil(float x, float y, float dx, float dy) {
        this.x = x;
        this.y = y;
        this.dx = dx;
        this.dy = dy;
    }

    @Override
    public void run() {
        while (ativo) {
            x += dx;
            y += dy;

            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                ativo = false;
            }
        }
    }

    public void draw(Canvas canvas) {
        Paint paint = new Paint();
        paint.setColor(Color.YELLOW);

        canvas.drawCircle(x, y, 10, paint);
    }
    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public boolean isAtivo() {
        return ativo;
    }
}