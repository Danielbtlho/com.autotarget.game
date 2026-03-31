package com.ufla.autotarget.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.ufla.autotarget.R;
import com.ufla.autotarget.engine.GameEngine;
import com.ufla.autotarget.exception.JogoException;

import java.util.Random;

/**
 * Activity principal do jogo AutoTarget.
 * Gerencia a interface do usuário (botões) e integra o GameEngine com o GameView.
 *
 * Responsabilidades:
 * - Iniciar/parar o jogo via botão "Iniciar"
 * - Adicionar canhões via botão "Adicionar Canhão"
 * - Exibir feedback visual (Toasts) para o jogador
 * - Tratar exceções do tipo JogoException e exibir mensagens ao usuário
 */
public class MainActivity extends AppCompatActivity implements GameEngine.GameEventListener {

    private GameView gameView;
    private GameEngine engine;
    private MaterialButton btnStart;
    private MaterialButton btnAddCannon;
    private boolean gameStarted = false;
    private final Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializa o motor do jogo
        engine = new GameEngine();
        engine.setGameEventListener(this);

        // Referências da UI
        gameView = findViewById(R.id.gameView);
        btnStart = findViewById(R.id.btnStart);
        btnAddCannon = findViewById(R.id.btnAddCannon);

        // Conecta o motor ao GameView e vice-versa
        // A GameView precisa do engine para ler dados no onDraw()
        // O engine precisa da View para chamar postInvalidate() das threads secundárias
        gameView.setEngine(engine);
        engine.setGameView(gameView);

        // ---- Botão Iniciar/Parar ----
        btnStart.setOnClickListener(v -> {
            if (!gameStarted) {
                startGame();
            } else {
                stopGame();
            }
        });

        // ---- Botão Adicionar Canhão ----
        btnAddCannon.setOnClickListener(v -> {
            addCannon();
        });
    }

    /**
     * Inicia o jogo e atualiza o estado dos botões.
     */
    private void startGame() {
        gameStarted = true;
        engine.startGame();

        // Atualiza visual do botão
        btnStart.setText(R.string.btn_stop);
        btnStart.setBackgroundColor(getResources().getColor(R.color.btn_stop_bg));
        btnStart.setIconResource(android.R.drawable.ic_media_pause);

        Toast.makeText(this, "Jogo iniciado! Adicione canhões para defender.", Toast.LENGTH_SHORT).show();
    }

    /**
     * Para o jogo e reseta o estado.
     */
    private void stopGame() {
        gameStarted = false;
        engine.stopGame();

        // Atualiza visual do botão
        btnStart.setText(R.string.btn_start);
        btnStart.setBackgroundColor(getResources().getColor(R.color.btn_start_bg));
        btnStart.setIconResource(android.R.drawable.ic_media_play);
    }

    /**
     * Adiciona um canhão em posição aleatória na parte inferior da tela.
     *
     * TRATAMENTO DE EXCEÇÕES: O método addCannon() do GameEngine pode
     * lançar JogoException se a posição é inválida ou se o limite
     * de canhões foi atingido. A exceção é capturada e exibida ao
     * usuário como um Toast, sem causar crash do aplicativo.
     */
    private void addCannon() {
        try {
            int width = engine.getScreenWidth();
            int height = engine.getScreenHeight();

            if (width <= 0 || height <= 0) {
                Toast.makeText(this, "Aguarde a tela carregar...", Toast.LENGTH_SHORT).show();
                return;
            }

            // Posiciona o canhão na parte inferior da tela (zona segura)
            float x = 60 + random.nextFloat() * (width - 120);
            float y = height * 0.7f + random.nextFloat() * (height * 0.2f);

            engine.addCannon(x, y);

            Toast.makeText(this,
                    "Canhão adicionado! (" + engine.getCannonCount() + " ativo" +
                    (engine.getCannonCount() > 1 ? "s" : "") + ")",
                    Toast.LENGTH_SHORT).show();

        } catch (JogoException e) {
            // TRATAMENTO: Exibe mensagem de erro amigável ao usuário
            // sem causar crash. A JogoException encapsula erros de domínio.
            Toast.makeText(this, "⚠️ " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ==================== GameEventListener ====================

    /**
     * Callback chamado quando a pontuação muda.
     * Roda na thread do jogo, então usa runOnUiThread para atualizar UI.
     */
    @Override
    public void onScoreChanged(int score) {
        // A pontuação é exibida no HUD do GameView
    }

    /**
     * Callback chamado quando a energia muda.
     */
    @Override
    public void onEnergyChanged(int energy) {
        // A energia é exibida no HUD do GameView
    }

    /**
     * Callback chamado quando o jogo termina (tempo ou energia esgotada).
     * Atualiza o estado dos botões na thread da UI.
     */
    @Override
    public void onGameOver(int finalScore) {
        runOnUiThread(() -> {
            gameStarted = false;
            btnStart.setText(R.string.btn_start);
            btnStart.setBackgroundColor(getResources().getColor(R.color.btn_start_bg));
            btnStart.setIconResource(android.R.drawable.ic_media_play);

            Toast.makeText(this,
                    "🏆 Fim de Jogo! Pontuação final: " + finalScore,
                    Toast.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameStarted) {
            engine.stopGame();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        engine.stopGame();
    }
}
