package com.example;

/**
 * Интерфейс, определяющий операции по выгрузке и синхронизации данных в БД и XML-файле
 */
public interface XmlSynchronizer {

    void exportToFile(String fileName);

    void syncWithFile(String fileName);
}
