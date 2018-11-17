package com.example;

/**
 * Головной класс
 */
public class App
{
    public static void main(String[] args) {
        if (args.length != 2) {
            printHelp();
            return;
        }
        String command = args[0];
        String fileName = args[1];
        switch (command) {
            case "export":
                XmlSynchronizerFactory.newInstance(new ConsoleProgressHandler()).exportToFile(fileName);
                return;
            case "sync":
                XmlSynchronizerFactory.newInstance(new ConsoleProgressHandler()).syncWithFile(fileName);
                return;
            default:
                printHelp();
        }
    }

    private static void printHelp() {
        final String helpText = "XmlSync - утилита синхронизации XML-файла с таблицей БД\n\n" +
                "Использование:\n" +
                "XmlSync <режим> <имя файла>, где\n" +
                "  <режим> - export либо sync.\n" +
                "    export - выполняет выгрузку данных из БД в XML-файл <имя файла>.\n" +
                "    sync - синхронизирует данные в XML-файле <имя файла> с данными в таблице БД.\n";
        System.out.println(helpText);
    }

}
