package net.skoczylas.backup.imap;

import com.sun.mail.util.BASE64DecoderStream;
import jakarta.activation.MimeType;
import jakarta.activation.MimeTypeParameterList;
import jakarta.activation.MimeTypeParseException;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class BackupImap {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupImap.class);
    private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MM_dd");

    public static void init(Session session, String username, String password) throws MessagingException {
        new BackupImap(session, username, password);
    }

    private int mailCount = 0;

    BackupImap(Session session, String username, String password) throws MessagingException {
        Store store = session.getStore("imap");
        LOGGER.info("Connecting as user {}", username);
        store.connect(username, password);

        Folder defaultFolder =  store.getDefaultFolder();

        Folder[] folders = defaultFolder.list();

        for (Folder folder : folders) {
            readFolder(folder);
        }

        store.close();
    }

    private void readFolder(Folder folder) throws MessagingException {
        LOGGER.info("Opening folder {}", folder.getName());
        folder.open(Folder.READ_ONLY);
        Message[] messages = folder.getMessages();

        for (Message message : messages) {
            readMessage(folder, (MimeMessage) message);
        }
        folder.close();
    }

    private void readMessage(Folder folder, MimeMessage message) throws MessagingException {
        String subject = getSubject(message);
        MimeType mimeType = getMimeType(message.getContentType());

        if (mimeType != null) {
            String from = getAddresses(message.getFrom());
            String to = getAddresses(message.getAllRecipients());
            LocalDateTime receivedDate = convertToLocalDateTimeViaInstant(message.getReceivedDate());
            MailInfo mailInfo = new MailInfo(folder.getName(), from, to, subject, receivedDate);

            mailCount++;

            LOGGER.info("Reading message {}, {}", mailCount, mailInfo.asFormattedString(", "));
            getContent(message).ifPresent(content -> readContent(content, mimeType, mailInfo));

            writeToFile(mailInfo);
        }
    }

    private String getAddresses(Address... addresses) {
        if (addresses != null) {
            return Arrays.stream(addresses)
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
        } else {
            return "Unknown";
        }
    }

    private MimeType getMimeType(String contentType) {
        try {
            return new MimeType(contentType);
        } catch (MimeTypeParseException exception) {
            return null;
        }
    }

    private String getSubject(Message message) {
        try {
            return MimeUtility.decodeText(message.getSubject());
        } catch (MessagingException | UnsupportedEncodingException exception) {
            LOGGER.error("Failed", exception);
        }

        return String.format("Unknown %s", UUID.randomUUID());
    }

    private void readContent(Object content, MimeType mimeType, MailInfo mailInfo) {
        LOGGER.debug("Reading content with type={}", mimeType);
        if (content instanceof MimeMultipart) {
            MimeMultipart mimeMultipart = (MimeMultipart) content;
            int count = getCount(mimeMultipart);
            for (int i = 0; i < count; i++) {
                try {
                    BodyPart bodyPart = mimeMultipart.getBodyPart(i);
                    MimeType partContentType = getMimeType(bodyPart.getContentType());
                    if (partContentType != null) {
                        getContent(bodyPart).ifPresent(partContent -> readContent(partContent, partContentType, mailInfo));
                    }
                } catch (MessagingException exception) {
                    LOGGER.error("Failed", exception);
                }
            }
        } else if ("application".equals(mimeType.getPrimaryType()) && "octet-stream".equals(mimeType.getSubType())) {
            getFileName(mimeType).ifPresent(fileName -> {
                LOGGER.info("Downloading attachment {}!", fileName);
                mailInfo.addAttachment(fileName);
                if (content instanceof BASE64DecoderStream) {
                    BASE64DecoderStream decoderStream = (BASE64DecoderStream) content;
                    writeToFile(decoderStream, mailInfo, fileName);
                }
            });
        } else if ("text".equals(mimeType.getPrimaryType()) && "plain".equals(mimeType.getSubType())) {
            String contentText = String.valueOf(content);
            writeToFile(contentText, mailInfo, "txt");
        }  else if ("text".equals(mimeType.getPrimaryType()) && "html".equals(mimeType.getSubType())) {
            String contentText = String.valueOf(content);
            writeToFile(contentText, mailInfo, "html");
        }
    }

    private Optional<String> getFileName(MimeType mimeType) {
        String fileName = mimeType.getParameter("name");
        if (StringUtils.isBlank(fileName)) {
            MimeTypeParameterList parameterList = mimeType.getParameters();
            Enumeration<String> names = parameterList.getNames();
            Map<String, String> sepNames = new HashMap<>();
            while(names.hasMoreElements()) {
                String name = names.nextElement();
                if (name.startsWith("name*")) {
                    String value = parameterList.get(name);
                    String index = name.substring(5);
                    sepNames.put(index, value);
                }
            }
            String collectedFileName = sepNames.keySet().stream().sorted().map(sepNames::get).collect(Collectors.joining());
            return Optional.of(cleanUp(collectedFileName));
        }
        if (StringUtils.isNoneEmpty(fileName)) {
            return Optional.of(cleanUp(fileName));
        }

        return Optional.empty();
    }

    private String cleanUp(String value) {
        return value.replaceAll("[^a-zA-Z0-9_.-]", "");
    }

    private Path getPath(MailInfo mailInfo) {
        LocalDateTime receivedDate = mailInfo.getReceivedAt();
        String hash = mailInfo.getHash();
        String yearFolder = YEAR_FORMATTER.format(receivedDate);
        String monthDayFolder = MONTH_FORMATTER.format(receivedDate);
        return Paths.get(System.getProperty("user.home"), "backupImap", mailInfo.getFolder(), yearFolder, monthDayFolder, hash);
    }

    private Path getRelativePath(MailInfo mailInfo) {
        LocalDateTime receivedDate = mailInfo.getReceivedAt();
        String hash = mailInfo.getHash();
        String yearFolder = YEAR_FORMATTER.format(receivedDate);
        String monthDayFolder = MONTH_FORMATTER.format(receivedDate);
        return Paths.get(mailInfo.getFolder(), yearFolder, monthDayFolder, hash);
    }

    private void writeToFile(InputStream content, MailInfo mailInfo, String fileName) {
        Path path = getPath(mailInfo);
        try {
            Files.createDirectories(path);
            Path file = Paths.get(path.toString(), fileName);
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

    private void writeToFile(String content, MailInfo mailInfo, String extension) {
        Path path = getPath(mailInfo);
        try {
            Files.createDirectories(path);
            Path file = Paths.get(path.toString(), String.format("mail_content.%s", extension));
            Path infoFile = Paths.get(path.toString(), "mail_info.txt");
            if (!Files.exists(infoFile)) {
                Files.writeString(infoFile, mailInfo.asFormattedString(System.lineSeparator()), StandardOpenOption.CREATE_NEW);
            }
            if (!Files.exists(file)) {
                Files.writeString(file, content, StandardOpenOption.CREATE_NEW);
            }
        } catch (IOException exception) {
            LOGGER.error("Failed", exception);
        }
    }

    private void writeToFile(MailInfo mailInfo) {
        Path mailPath = getRelativePath(mailInfo);
        Path path = Paths.get(System.getProperty("user.home"), "backupImap");
        try {
            Files.createDirectories(path);
            Path infoFile = Paths.get(path.toString(), "mail_index.html");
            if (!Files.exists(infoFile)) {
                Files.writeString(infoFile, "<!DOCTYPE html>", StandardOpenOption.CREATE_NEW);
            }
            Files.writeString(infoFile, String.format("<a href=\"%s\">", FilenameUtils.separatorsToUnix(mailPath.toString())), StandardOpenOption.APPEND);
            Files.writeString(infoFile, mailInfo.asFormattedString(", "), StandardOpenOption.APPEND);
            Files.writeString(infoFile, "</a>", StandardOpenOption.APPEND);
            Files.writeString(infoFile, "<br>", StandardOpenOption.APPEND);
            Files.writeString(infoFile, System.lineSeparator(), StandardOpenOption.APPEND);
        } catch (IOException exception) {
            LOGGER.error("Failed", exception);
        }
    }

    private String getPrefixed(int value) {
        if (value < 10) {
            return String.format("0%s", value);
        }
        return String.valueOf(value);
    }

    private Optional<Object> getContent(Message message) {
        Object content = null;
        try {
            content = message.getContent();
        } catch (MessagingException | IOException exception) {
            LOGGER.error("Failed", exception);
        }

        return Optional.ofNullable(content);
    }

    private Optional<Object> getContent(Part part) {
        Object content = null;
        try {
            content = part.getContent();
        } catch (MessagingException | IOException exception) {
            LOGGER.error("Failed", exception);
        }

        return Optional.ofNullable(content);
    }

    private int getCount(MimeMultipart mimeMultipart) {
        try {
            return mimeMultipart.getCount();
        } catch (MessagingException exception) {
            LOGGER.error("Failed", exception);
        }
        return  0;
    }

    public LocalDateTime convertToLocalDateTimeViaInstant(Date dateToConvert) {
        return dateToConvert.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
