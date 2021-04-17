package net.skoczylas.imap.backup;

import com.sun.mail.imap.IMAPNestedMessage;
import com.sun.mail.util.BASE64DecoderStream;
import jakarta.activation.MimeType;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class ImapBackup {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImapBackup.class);

    private Store store;
    private Writer writer;
    private int mailCount = 0;

    public ImapBackup(Session session, String username, String password) {
        this.writer = new Writer("imapBackup");
        try {
            this.store = session.getStore("imap");
            LOGGER.info("Connecting as user {}", username);
            this.store.connect(username, password);
        } catch (MessagingException exception) {
            LOGGER.error("Connection failed: {}", exception.getMessage());
        }

    }

    public void run() {
        if (store.isConnected()) {
            try {
                Folder defaultFolder = store.getDefaultFolder();
                LOGGER.info("Default folder: {}", defaultFolder);

                List<Folder> folders = Arrays.asList(defaultFolder.list());

                String folderNames = folders.stream().map(Folder::getName).collect(Collectors.joining(", "));
                LOGGER.info("Found folders: {}", folderNames);

                folders.forEach(this::openFolder);

                LOGGER.info("Closing store");
                store.close();
            } catch (MessagingException exception) {
                LOGGER.error("Failed: {}", exception.getMessage());
            }
        }
    }

    private void openFolder(Folder folder) {
        try {
            LOGGER.info("Opening folder {}", folder.getName());
            folder.open(Folder.READ_ONLY);
            readFolder(folder, new ArrayDeque<>());
            LOGGER.info("Closing folder {}", folder.getName());
            folder.close();
        } catch (MessagingException exception) {
            LOGGER.error("Could not handle folder {}: {}", folder.getName(), exception.getMessage());
        }
    }

    private void readFolder(Folder folder, Deque<String> folderNames) {
        try {
            List<Message> messages = Arrays.asList(folder.getMessages());

            folderNames.push(folder.getName());

            messages.forEach(message -> readMessage(folderNames, (MimeMessage) message));

            List<Folder> subFolders = Arrays.asList(folder.list());
            String subFolderNames = subFolders.stream().map(Folder::getName).collect(Collectors.joining(", "));
            LOGGER.info("Found sub folders: {}", subFolderNames);
            subFolders.forEach(subFolder -> readFolder(subFolder, folderNames));

            folderNames.pop();

        } catch (MessagingException exception) {
            LOGGER.error("Could not read folder {}: {}", folder.getName(), exception.getMessage());
        }

    }

    private void readMessage(Deque<String> parents, MimeMessage message) {
        try {
            String subject = Utility.getSubject(message).orElse("No subject");
            MimeType mimeType = Utility.getMimeType(message.getContentType()).orElse(null);

            if (mimeType != null) {
                List<MailAddress> from = Utility.getAddresses(message.getFrom());
                List<MailAddress> to = Utility.getAddresses(message.getAllRecipients());
                LocalDateTime receivedDate = Utility.convertToLocalDateTimeViaInstant(message.getReceivedDate());
                MailInfo mailInfo = new MailInfo(parents, from, to, subject, receivedDate, mimeType);

                mailCount++;

                LOGGER.info("Reading message {}, {}", mailCount, mailInfo.asFormattedString(", ", false));
                Utility.getContent(message)
                        .ifPresent(content -> readContent(content, mimeType, mailInfo));

                writer.writeToFile(mailInfo);
            }
        } catch (MessagingException exception) {
            LOGGER.error("Could not read message: {}", exception.getMessage());
        }
    }

    private void readContent(Object content, MimeType mimeType, MailInfo mailInfo) {
        readContent(content, mimeType, mailInfo, null);
    }

    private void readContent(Object content, MimeType mimeType, MailInfo mailInfo, String nameFromParent) {
        if (mimeType == null) {
            LOGGER.warn("Skipping content, no mime type found");
            return;
        }
        LOGGER.debug("Reading content with type={}", mimeType);
        if (content instanceof MimeMultipart) {
            handleMultipart((MimeMultipart) content, mailInfo);
        } else if (content instanceof BASE64DecoderStream) {
            handleBASE64DecoderStream((BASE64DecoderStream) content, mimeType, mailInfo);
        } else if (content instanceof IMAPNestedMessage) {
            handleIMAPNestedMessage((IMAPNestedMessage) content, mimeType, mailInfo);
        } else if ("text".equals(mimeType.getPrimaryType()) && "plain".equals(mimeType.getSubType())) {
            handlePlain(String.valueOf(content), nameFromParent, mailInfo, "mail_content.txt");
        } else if ("text".equals(mimeType.getPrimaryType()) && "html".equals(mimeType.getSubType())) {
            handlePlain(String.valueOf(content), nameFromParent, mailInfo, "mail_content.html");
        } else {
            handleUnknownAsPlain(String.valueOf(content), mimeType, mailInfo);
        }
    }

    private void handleMultipart(MimeMultipart mimeMultipart, MailInfo mailInfo) {
        int count = Utility.getCount(mimeMultipart);
        for (int i = 0; i < count; i++) {
            try {
                BodyPart bodyPart = mimeMultipart.getBodyPart(i);
                MimeType mimeType = Utility.getMimeType(bodyPart.getContentType()).orElse(null);
                Utility.getContent(bodyPart).ifPresent(content -> readContent(content, mimeType, mailInfo));
            } catch (MessagingException exception) {
                LOGGER.error("Could not read multi part message: {}", exception.getMessage());
            }
        }
    }

    private void handleBASE64DecoderStream(BASE64DecoderStream base64DecoderStream, MimeType mimeType, MailInfo mailInfo) {
        Utility.getFileName(mimeType)
            .filter(StringUtils::isNotBlank)
            .ifPresentOrElse(fileName -> {
                LOGGER.info("Downloading attachment: {}", fileName);
                mailInfo.addAttachment(fileName);
                writer.writeToFile(base64DecoderStream, mailInfo, fileName);
            }, () -> {
                MimeTypes allTypes = MimeTypes.getDefaultMimeTypes();
                try {
                    org.apache.tika.mime.MimeType detectedMimeType = allTypes.forName(mimeType.getBaseType());
                    String fileName = UUID.randomUUID() + detectedMimeType.getExtension();
                    LOGGER.info("Downloading unnamed attachment: {}", fileName);
                    mailInfo.addAttachment(fileName);
                    writer.writeToFile(base64DecoderStream, mailInfo, fileName);
                } catch (MimeTypeException exception) {
                    LOGGER.warn("Skipped unnamed attachment, type: {}, because: {}", mimeType, exception.getMessage());
                }
            });
    }

    private void handleIMAPNestedMessage(IMAPNestedMessage imapNestedMessage, MimeType mimeType, MailInfo mailInfo) {
        try {
            MimeType nestedMimeType = Utility.getMimeType(imapNestedMessage.getContentType()).orElse(null);
            Utility.getFileName(mimeType)
                .filter(StringUtils::isNotBlank)
                .ifPresentOrElse(fileName -> {
                    try {
                        LOGGER.info("Downloading nested E-Mail: {}", fileName);
                        mailInfo.addAttachment(fileName);
                        readContent(imapNestedMessage.getContent(), nestedMimeType, mailInfo, fileName);
                    } catch (IOException | MessagingException exception) {
                        LOGGER.error("Failed", exception);
                    }
                }, () -> {
                    MimeTypes allTypes = MimeTypes.getDefaultMimeTypes();
                    try {
                        org.apache.tika.mime.MimeType detectedMimeType = allTypes.forName(nestedMimeType.getBaseType());
                        String fileName = UUID.randomUUID() + detectedMimeType.getExtension();
                        LOGGER.info("Downloading unnamed nested E-Mail: {}", fileName);
                        mailInfo.addAttachment(fileName);
                        readContent(imapNestedMessage.getContent(), nestedMimeType, mailInfo, fileName);
                    } catch (IOException | MessagingException | MimeTypeException exception) {
                        LOGGER.warn("Skipped unnamed content, type: {}, because: {}", mimeType, exception.getMessage());
                    }
                });
        } catch (MessagingException exception) {
            LOGGER.error("Failed", exception);
        }
    }

    private void handlePlain(String contentText, String contentName, MailInfo mailInfo, String fallbackName) {
        String currentFileName = Optional.ofNullable(contentName).map(name -> String.format("%s.txt", name)).orElse(fallbackName);
        writer.writeToFile(contentText, mailInfo, currentFileName);
    }

    private void handleUnknownAsPlain(String contentText, MimeType mimeType, MailInfo mailInfo) {
        Utility.getFileName(mimeType)
            .filter(StringUtils::isNotBlank)
            .ifPresentOrElse(fileName -> {
                LOGGER.info("Downloading named content: {}, {}", fileName, mimeType);
                mailInfo.addAttachment(fileName);
                writer.writeToFile(contentText, mailInfo, fileName);
            }, () -> {
                MimeTypes allTypes = MimeTypes.getDefaultMimeTypes();
                try {
                    org.apache.tika.mime.MimeType detectedMimeType = allTypes.forName(mimeType.getBaseType());
                    String fileName = UUID.randomUUID() + detectedMimeType.getExtension();
                    LOGGER.info("Downloading unnamed content: {}", fileName);
                    mailInfo.addAttachment(fileName);
                    writer.writeToFile(contentText, mailInfo, fileName);
                } catch (MimeTypeException exception) {
                    LOGGER.warn("Skipped unnamed content, type: {}, because: {}", mimeType, exception.getMessage());
                }
            });
    }


}
