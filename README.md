# 🎯 AutoTarget — Jogo de Defesa com Threads (GAT108)

Projeto Android desenvolvido para a disciplina **GAT108 — Programação Paralela e Concorrente** da UFLA.  
Um jogo de defesa em tempo real onde canhões autônomos devem destruir alvos em movimento, com toda a lógica de entidades executando em **threads concorrentes** e sincronização via **`synchronized`** e **`Semaphore`**.

---

## 📋 Visão Geral

O jogador posiciona canhões na tela que automaticamente detectam e disparam contra alvos móveis. Cada entidade (alvo, canhão, projétil) roda em sua **própria thread**, criando um ambiente verdadeiramente concorrente onde a sincronização correta é essencial.

### Funcionalidades Principais

- **Alvos polimórficos** — `CommonTarget` (lento, +1 pt) e `FastTarget` (2x velocidade, +3 pts)
- **Canhões autônomos** — Detectam o alvo mais próximo e disparam automaticamente
- **Sistema de energia** — Cada canhão consome energia por segundo; jogo termina se a energia zera
- **Tempo de partida** — 60 segundos por rodada
- **HUD em tempo real** — Placar, energia (barra visual), tempo restante, contagem de threads ativas
- **Log visual de ações** — Últimas 5 ações do sistema exibidas no Canvas
- **Overlay de fim de jogo** — Tela com pontuação final e alvos que escaparam

---

## 🏗️ Arquitetura

```
com.ufla.autotarget/
├── engine/
│   └── GameEngine.java         # Motor do jogo — gerencia entidades, colisões, sincronização
├── model/
│   ├── Target.java             # Classe abstrata base (Thread) — movimento + polimorfismo
│   ├── CommonTarget.java       # Alvo lento — move() com trajetória previsível
│   ├── FastTarget.java         # Alvo rápido — move() com 2x velocidade + zigzag
│   ├── Cannon.java             # Canhão autônomo (Thread) — detecta e dispara
│   └── Projectile.java         # Projétil (Thread) — movimento + verificação de colisão
├── ui/
│   ├── MainActivity.java       # Tela principal — botões de controle + tratamento de exceções
│   └── GameView.java           # View customizada — renderização do Canvas + HUD completo
└── exception/
    └── JogoException.java      # Exceção de domínio para validações do jogo
```

---

## 🔒 Sincronização

### `synchronized` (Monitor Pattern)
- **Locks independentes** por lista (`targetLock`, `cannonLock`, `projectileLock`) para maximizar concorrência
- **Cópias snapshot** para renderização — `onDraw()` trabalha com cópias das listas, sem manter locks durante o desenho

### `Semaphore`
- **`collisionSemaphore(1)`** — Mutex para verificação de colisão; `tryAcquire()` non-blocking evita travamentos
- **`cannonSlotSemaphore(MAX_CANNONS)`** — Controla capacidade máxima de canhões com fairness FIFO

### `postInvalidate()`
- Threads secundárias solicitam redesenho da View sem causar flicker ou race condition visual
- O framework Android garante double-buffering automático

---

## 🧪 Testes

| Teste | Descrição |
|-------|-----------|
| `GameEngineTest` | Ciclo de vida, adição de canhões, validação de exceções, concorrência com Semaphore |
| `CollisionDetectionTest` | Detecção de colisão entre projéteis e alvos |
| `DistanceCalculationTest` | Cálculo de distância para mira dos canhões |

Executar:
```bash
./gradlew test
```

---

## 🎮 Como Jogar

1. Toque em **"Iniciar"** para começar a partida
2. Toque em **"Adicionar Canhão"** para posicionar canhões (máx. 10)
3. Os canhões disparam automaticamente contra alvos detectados
4. **Alvos Comuns** (ciano) = +1 pt | **Alvos Rápidos** (vermelho) = +3 pts
5. Gerencie energia — cada canhão consome 10 energia/s
6. O jogo termina quando o tempo (60s) ou a energia acabam

---

## 🛠️ Tecnologias

- **Linguagem:** Java
- **Plataforma:** Android (API 24+)
- **Build:** Gradle 8.7
- **UI:** Canvas API (renderização customizada) + Material Design (botões)

---

## 👤 Autor

Desenvolvido por **Daniel** — UFLA, 2026.
