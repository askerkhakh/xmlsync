package com.example;

public class ConsoleProgressHandler implements ProgressHandler{
    @Override
    public void println(String s) {
        System.out.println(s);
    }
}
