package com.example;

public interface ProgressHandler {

    void println(String s);

    default void printlnFormat(String s, Object... objects) {
        println(String.format(s, objects));
    }

}
