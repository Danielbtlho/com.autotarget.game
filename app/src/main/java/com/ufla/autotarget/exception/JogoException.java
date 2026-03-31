package com.ufla.autotarget.exception;

/**
 * Exceção personalizada do jogo AutoTarget.
 * Lançada em situações de erro específicas do jogo, como:
 * - Tentar adicionar canhão fora dos limites da tela
 * - Exceder o número máximo de canhões
 * - Divisão por zero ao calcular ângulos
 * - Índices inválidos ao acessar listas de objetos
 *
 * TRATAMENTO DE EXCEÇÕES: Esta classe demonstra a criação de
 * exceções personalizadas (checked exceptions) para capturar
 * erros de lógica do domínio do jogo, diferenciando-os de
 * exceções genéricas do Java.
 */
public class JogoException extends Exception {

    /**
     * Construtor com mensagem de erro.
     * @param message descrição do erro ocorrido
     */
    public JogoException(String message) {
        super(message);
    }

    /**
     * Construtor com mensagem e causa raiz.
     * Permite encadear exceções para rastreamento completo.
     *
     * @param message descrição do erro ocorrido
     * @param cause exceção original que causou este erro
     */
    public JogoException(String message, Throwable cause) {
        super(message, cause);
    }
}
