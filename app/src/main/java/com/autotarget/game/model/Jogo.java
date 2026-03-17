package com.autotarget.game.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Jogo {
    private List<Alvo> alvos;
    private ExecutorService executorService; // Para gerenciar as threads dos alvos
    private int screenWidth, screenHeight;
    private List<Canhao> canhoes;
    private List<Projetil> projeteis;
    public Jogo(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.alvos = new ArrayList<>();
        // Cria um pool de threads para gerenciar os alvos
        this.executorService = Executors.newCachedThreadPool();
        this.canhoes = new ArrayList<>();
        this.projeteis = new ArrayList<>();
    }

    public void adicionarAlvo() {
        Alvo novoAlvo = new Alvo(screenWidth, screenHeight);
        alvos.add(novoAlvo);
        executorService.execute(novoAlvo); // Inicia a thread do novo alvo
    }

    public List<Alvo> getAlvos() {
        return alvos;
    }

    public List<Canhao> getCanhoes() {
        return canhoes;
    }

    public List<Projetil> getProjeteis() {
        return projeteis;
    }

    public void iniciarJogo() {
        // A lógica de iniciar o jogo pode ser mais complexa, mas por enquanto, apenas adicionamos um alvo
        // E os alvos já são iniciados em suas próprias threads ao serem adicionados
        if (alvos.isEmpty()) {
            adicionarAlvo(); // Adiciona um alvo inicial se não houver nenhum
        }
    }

    public void pararJogo() {
        for (Alvo alvo : alvos) {
            alvo.setAtivo(false); // Sinaliza para as threads dos alvos pararem
        }
        executorService.shutdownNow(); // Tenta parar todas as threads imediatamente
    }
}
