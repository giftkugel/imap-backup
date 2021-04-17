package net.skoczylas.imap.backup;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

class Writer {

    private static final Logger LOGGER = LoggerFactory.getLogger(Writer.class);

    private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MM_dd");

    private final String baseFolder;

    public Writer(String baseFolder) {
        this.baseFolder = baseFolder;
    }

    void writeToFile(InputStream content, MailInfo mailInfo, String fileName) {
        Path path = getPath(mailInfo);
        try {
            Files.createDirectories(path);
            Path file = Paths.get(path.toString(), normalize(fileName));
            if (!Files.exists(file)) {
                byte[] buffer = content.readAllBytes();
                File targetFile = file.toFile();
                OutputStream outStream = new FileOutputStream(targetFile);
                outStream.write(buffer);
                outStream.flush();
                outStream.close();
            }
        } catch (IOException exception) {
            LOGGER.error("Failed", exception);
        }
    }

    void writeToFile(String content, MailInfo mailInfo, String fileName) {
        Path path = getPath(mailInfo);
        try {
            Files.createDirectories(path);
            Path file = Paths.get(path.toString(), normalize(fileName));
            Path infoFile = Paths.get(path.toString(), "mail_info.txt");
            if (!Files.exists(infoFile)) {
                Files.writeString(infoFile, mailInfo.asFormattedString(System.lineSeparator(), true), StandardOpenOption.CREATE_NEW);
            }
            if (!Files.exists(file)) {
                Files.writeString(file, content, StandardOpenOption.CREATE_NEW);
            }
        } catch (IOException exception) {
            LOGGER.error("Failed", exception);
        }
    }

    void writeToFile(MailInfo mailInfo) {
        Path mailPath = getRelativePath(mailInfo);
        Path path = Paths.get(System.getProperty("user.home"), baseFolder);
        try {
            Files.createDirectories(path);
            Path infoFile = Paths.get(path.toString(), "mail_index.html");
            if (!Files.exists(infoFile)) {
                Files.writeString(infoFile, "<!DOCTYPE html>", StandardOpenOption.CREATE_NEW);
                Files.writeString(infoFile, "<table>", StandardOpenOption.APPEND);
                Files.writeString(infoFile, "<tr><th>Folder</th><th>Date</th><th>Subject</th><th>From</th><th>To</th><th>Attachments</th></tr>", StandardOpenOption.APPEND);
            }
            Files.writeString(infoFile, mailInfo.asHTMLTableString(FilenameUtils.separatorsToUnix(mailPath.toString())), StandardOpenOption.APPEND);
            Files.writeString(infoFile, System.lineSeparator(), StandardOpenOption.APPEND);
        } catch (IOException exception) {
            LOGGER.error("Failed", exception);
        }
    }

    Path getPath(MailInfo mailInfo) {
        return Paths.get(System.getProperty("user.home"), getPathAsArray(mailInfo, true));
    }

    Path getRelativePath(MailInfo mailInfo) {
        return Paths.get("", getPathAsArray(mailInfo, false));
    }

    String normalize(String value) {
        return value
                .replaceAll("[^\\sa-zA-Z0-9_.ÄÖÜäöüß+-]", "_")
                .replaceAll("(\\r|\\n|\\t)", "");
    }

    private String[] getPathAsArray(MailInfo mailInfo, boolean root) {
        LocalDateTime receivedDate = mailInfo.getReceivedAt();
        String hash = mailInfo.getHash();
        String yearFolder = YEAR_FORMATTER.format(receivedDate);
        String monthDayFolder = MONTH_FORMATTER.format(receivedDate);
        List<String> folder = new ArrayList<>();
        if (root) {
            folder.add(baseFolder);
        }
        folder.addAll(mailInfo.getFolder());
        folder.add(yearFolder);
        folder.add(monthDayFolder);
        folder.add(hash);
        return folder.toArray(new String[0]);
    }

}
