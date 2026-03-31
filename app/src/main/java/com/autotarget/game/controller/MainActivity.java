package com.autotarget.game.controller;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.ufla.autotarget.R;
import com.autotarget.game.model.Canhao;
import com.autotarget.game.view.GameView;
import com.autotarget.game.model.Jogo;

public class MainActivity extends AppCompatActivity {

    private GameView gameView;
    private Jogo jogo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        gameView = findViewById(R.id.gameView);

        jogo = new Jogo(1000, 1800);
        jogo.iniciarJogo();
        gameView.setJogo(jogo);

        Canhao c = new Canhao(200,1500, jogo);

        jogo.getCanhoes().add(c);
        c.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameView != null) {
            gameView.stopGameLoop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gameView != null) {
            gameView.stopGameLoop();
        }
    }
}
