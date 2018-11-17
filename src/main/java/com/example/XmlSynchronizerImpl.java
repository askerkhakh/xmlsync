package com.example;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static java.lang.String.format;


/**
 * Реализует алгоритмы выгрузки в Xml-файл и синхронизации с Xml-файлом содержимого БД
 */
class XmlSynchronizerImpl implements XmlSynchronizer {


    private static final String XML_SYNC_PROPERTIES = "xmlsync.properties";

    private static Logger logger = Logger.getLogger(XmlSynchronizerImpl.class);

    private final ProgressHandler progressHandler;
    private final Connection connection;

    XmlSynchronizerImpl(ProgressHandler progressHandler) {
        this.progressHandler = progressHandler;
        Properties properties = loadProperties();
        String logfile = properties.getProperty("logfile");
        if (logfile != null)
            try {
                logger.addAppender(new FileAppender(new SimpleLayout(), logfile, true));
            }
        catch (IOException e) {
            throw new RuntimeException("Ошибка создания лог-файла", e);
        }

        try {
            logger.info("Подключение к БД");
            connection = DriverManager.getConnection(properties.getProperty("url"), properties);
        }
        catch (Throwable e) {
            throw new RuntimeException(format("Не удалось подключиться к БД. Проверьте настройки в \"%s\".", XML_SYNC_PROPERTIES), e);
        }
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try {
            try (InputStream inputStream = new FileInputStream(XML_SYNC_PROPERTIES)) {
                properties.load(inputStream);
                return properties;
            }
        }
        catch (Throwable e) {
            throw new RuntimeException(format("Ошибка при чтении файла \"%s\".", XML_SYNC_PROPERTIES), e);
        }

    }

    private Element getDepCodeByDepJobElement(Document document, @Nullable String depCode, @Nullable String depJob, @Nullable String description) {
        Element depElement = document.createElement(XmlConst.ELEMENT);

        Element depCodeElement = document.createElement(XmlConst.DEP_CODE);
        depCodeElement.appendChild(document.createTextNode(depCode == null ? "" : depCode));
        depElement.appendChild(depCodeElement);

        Element depJobElement = document.createElement(XmlConst.DEP_JOB);
        depJobElement.appendChild(document.createTextNode(depJob == null ? "" : depJob));
        depElement.appendChild(depJobElement);

        Element descriptionElement = document.createElement(XmlConst.DESCRIPTION);
        descriptionElement.appendChild(document.createTextNode(description == null ? "" : description));
        depElement.appendChild(descriptionElement);

        return depElement;
    }

    @Override
    public void exportToFile(String fileName) {
        try {
            progressHandler.printlnFormat("Выгрузка данных из БД в \"%s\"", fileName);
            internalExportToFile(fileName);
            progressHandler.println("Выгрузка завершена");
        }
        catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private void internalExportToFile(String fileName) throws Exception {
        logger.info("Начало выгрузки данных...");
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        try (ResultSet resultSet = getDataFromDb()) {
            Element root = document.createElement(XmlConst.ROOT_ELEMENT);
            document.appendChild(root);
            while (resultSet.next()) {
                root.appendChild(getDepCodeByDepJobElement(
                        document,
                        resultSet.getString(DbConst.DEP_CODE),
                        resultSet.getString(DbConst.DEP_JOB),
                        resultSet.getString(DbConst.DESCRIPTION)
                ));
            }
        }
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(new DOMSource(document), new StreamResult(new FileOutputStream(fileName)));
        logger.info("Окончание выгрузки данных.");
    }

    @Override
    public void syncWithFile(String fileName) {
        try {
            progressHandler.printlnFormat("Синхронизация данных в БД с данными в файле \"%s\"", fileName);
            internalSyncWithFile(fileName);
            progressHandler.println("Синхронизация завершена");
        }
        catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private void internalSyncWithFile(String fileName) throws Exception {
        Map<DepCodeByDepJob, String> xmlData = readXmlFile(fileName);

        PreparedStatement modifyStatement = null;
        PreparedStatement deleteStatement = null;
        try {
            connection.setAutoCommit(false);

            logger.info("Начало удаления отсутствующих и редактирования изменённых...");
            try (ResultSet dbData = getDataFromDb()) {
                while (dbData.next()) {
                    DepCodeByDepJob keyObj = new DepCodeByDepJob();
                    keyObj.setDepCode(dbData.getString(DbConst.DEP_CODE));
                    keyObj.setDepJob(dbData.getString(DbConst.DEP_JOB));
                    String description = xmlData.remove(keyObj);
                    if (description == null)
                        deleteStatement = delete(deleteStatement, dbData.getInt(DbConst.ID));
                    else
                        modifyStatement = modify(modifyStatement, dbData.getInt(DbConst.ID), description);
                }
            }
            logger.info("Окончание удаления отсутствующих и редактирования изменённых.");

            logger.info("Добавление новых...");
            insert(xmlData);
            logger.info("Окончание добавления.");

            connection.commit();
        }
        catch (Throwable e) {
            connection.rollback();
            throw e;
        }
        finally {
            if (modifyStatement != null)
                modifyStatement.close();
            if (deleteStatement != null)
                deleteStatement.close();
            connection.setAutoCommit(true);
        }
    }

    private void insert(Map<DepCodeByDepJob, String> xmlData) throws SQLException {
        try(PreparedStatement insertStatement = connection.prepareStatement(
                String.format("insert into %s(%s, %s, %s) values (?, ?, ?)", DbConst.TABLE_NAME, DbConst.DEP_CODE, DbConst.DEP_JOB, DbConst.DESCRIPTION)))
        {
            for (Map.Entry<DepCodeByDepJob, String> entry : xmlData.entrySet()) {
                DepCodeByDepJob depCodeByDepJob = entry.getKey();
                String description = entry.getValue();
                insertStatement.setString(1, depCodeByDepJob.getDepCode().isEmpty() ? null : depCodeByDepJob.getDepCode());
                insertStatement.setString(2, depCodeByDepJob.getDepJob().isEmpty() ? null : depCodeByDepJob.getDepJob());
                insertStatement.setString(3, description.isEmpty() ? null : description);
                insertStatement.execute();
            }
        }
    }

    private PreparedStatement modify(@Nullable PreparedStatement modifyStatement, int id, String description) throws SQLException {
        if (modifyStatement == null)
            modifyStatement = connection.prepareStatement(
                    format("update %s set %s = ? where %s = ?", DbConst.TABLE_NAME, DbConst.DESCRIPTION, DbConst.ID)
            );
        modifyStatement.setString(1, description.isEmpty() ? null : description);
        modifyStatement.setInt(2, id);
        modifyStatement.execute();
        return modifyStatement;
    }

    private PreparedStatement delete(@Nullable PreparedStatement deleteStatement, int id) throws SQLException {
        if (deleteStatement == null)
            deleteStatement = connection.prepareStatement(
                    format("delete from %s where %s = ?", DbConst.TABLE_NAME, DbConst.ID)
            );
        deleteStatement.setInt(1, id);
        deleteStatement.execute();
        return deleteStatement;
    }

    private ResultSet getDataFromDb() throws SQLException {
        return connection.createStatement().executeQuery(format("select * from %s", DbConst.TABLE_NAME));
    }

    private Map<DepCodeByDepJob, String> readXmlFile(String fileName) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(fileName);
        Element root = document.getDocumentElement();
        root.normalize();
        assert root.getNodeName().equals(XmlConst.ROOT_ELEMENT);

        Map<DepCodeByDepJob, String> xmlData = new HashMap<>();
        NodeList depCodeByDepJobElements = root.getElementsByTagName(XmlConst.ELEMENT);
        for (int i = 0; i < depCodeByDepJobElements.getLength(); i++) {
            Element depCodeByDepJobElement = (Element) depCodeByDepJobElements.item(i);

            DepCodeByDepJob depCodeByDepJob = new DepCodeByDepJob();
            depCodeByDepJob.setDepCode(depCodeByDepJobElement.getElementsByTagName(XmlConst.DEP_CODE).item(0).getTextContent());
            depCodeByDepJob.setDepJob(depCodeByDepJobElement.getElementsByTagName(XmlConst.DEP_JOB).item(0).getTextContent());

            String description = depCodeByDepJobElement.getElementsByTagName(XmlConst.DESCRIPTION).item(0).getTextContent();
            if (xmlData.containsKey(depCodeByDepJob))
                throw new XmlDataDuplicateException(
                        format("XML-файл содержит более одного узла с %s=\"%s\" и %s=\"%s\"",
                                XmlConst.DEP_CODE, depCodeByDepJob.getDepCode(),
                                XmlConst.DEP_JOB, depCodeByDepJob.getDepJob()));
            xmlData.put(depCodeByDepJob, description);
        }
        return xmlData;
    }

    public static class XmlDataDuplicateException extends RuntimeException {
        XmlDataDuplicateException(String s) {
            super(s);
        }
    }
}
