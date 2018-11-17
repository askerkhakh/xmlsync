package com.example;

/**
 * Фабрика для объекта выполняющего алгоритмы выгрузки и синхронизации.
 * Изолирует пользователей интерфейса {@link XmlSynchronizer} от реализации.
 */
public class XmlSynchronizerFactory {

    public static XmlSynchronizer newInstance(ProgressHandler progressHandler) {
        return new XmlSynchronizerImpl(progressHandler);
    }

}
