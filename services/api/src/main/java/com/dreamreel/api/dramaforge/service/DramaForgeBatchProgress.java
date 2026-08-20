package com.dreamreel.api.dramaforge.service;

@FunctionalInterface
public interface DramaForgeBatchProgress {
    void report(int current, int total, String message);
}
