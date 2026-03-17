package com.autotarget.game.util;

public class JogoException extends Exception {
    public JogoException(String message) {
        super(message);
    }

    public JogoException(String message, Throwable cause) {
        super(message, cause);
    }
}
