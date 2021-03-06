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

    private final List<MailInfo> mailQueue;
    private final Writer writer;

    private Store store;
    private int mailCount = 0;

    public ImapBackup(Session session, String output, String template, String username, String password) throws IOException {
        this.mailQueue = new ArrayList<>();
        this.writer = new Writer(mailQueue, output, "imapBackup", username, template);

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
            writer.run();
            try {
                Folder defaultFolder = store.getDefaultFolder();
                List<Folder> folders = Arrays.asList(defaultFolder.list());

                String folderNames = folders.stream().map(Folder::getName).collect(Collectors.joining(", "));
                LOGGER.info("Found folders: {}", folderNames);

                folders.forEach(folder -> readFolder(folder, new ArrayDeque<>()));

                writer.stop();
            } catch (MessagingException exception) {
                LOGGER.error("Failed: {}", exception.getMessage());
            }
            LOGGER.info("Finished");
        }
    }

    private void readFolder(Folder folder, Deque<String> folderNames) {
        try {
            if (!folder.isOpen()) {
                folder.open(Folder.READ_ONLY);
            }
            folderNames.addLast(folder.getName());

            LOGGER.info("Reading folder {}", folder.getName());
            List<Message> messages = Arrays.asList(folder.getMessages());

            messages.forEach(message -> readMessage(folderNames, (MimeMessage) message));

            List<Folder> subFolders = Arrays.asList(folder.list());
            if (!subFolders.isEmpty()) {
                String subFolderNames = subFolders.stream().map(Folder::getName).collect(Collectors.joining(", "));
                LOGGER.info("{} has sub folders: {}", folder.getName(), subFolderNames);
                subFolders.forEach(subFolder -> readFolder(subFolder, folderNames));
            }

            folderNames.removeLast();
        } catch (MessagingException exception) {
            LOGGER.error("Could not read folder {}: {}", folder.getName(), exception.getMessage());
        }

    }

    private void readMessage(Deque<String> parents, MimeMessage message) {
        try {
            String subject = Utility.getSubject(message).orElse("No subject");
            MimeType mimeType = Utility.getMimeType(message.getContentType()).orElse(null);

            if (mimeType != null) {

                mailCount++;

                List<MailAddress> from = Utility.getAddresses(message.getFrom());
                List<MailAddress> to = Utility.getAddresses(message.getAllRecipients());
                LocalDateTime receivedDate = Utility.convertToLocalDateTimeViaInstant(message.getReceivedDate());
                MailInfo mailInfo = new MailInfo(mailCount, parents, from, to, subject, receivedDate, mimeType);
                mailQueue.add(mailInfo);

                String fromAddress = mailInfo.getFrom().stream().findFirst().map(MailAddress::getValidAddress).orElse("Unknown");
                String folder = String.join("/", parents);
                Utility.getContent(message)
                        .ifPresent(content -> {
                            readContent(content, mimeType, mailInfo);
                            writer.writeInfoFile(mailInfo);
                        });
                if (mailInfo.getAttachments().isEmpty()) {
                    LOGGER.info("Message {}, {}, {}, subject={}, from={}", mailCount, Utility.getDate(mailInfo.getReceivedAt()), folder, mailInfo.getSubject(), fromAddress);
                } else {
                    LOGGER.info("Message {}, {}, {}, subject={}, from={}, attachments={}", mailCount, Utility.getDate(mailInfo.getReceivedAt()), folder, mailInfo.getSubject(), fromAddress, mailInfo.getAttachments());
                }

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
                LOGGER.debug("Downloading attachment: {}", fileName);
                String validFileName = addAttachment(mailInfo, fileName);
                writer.writeToFile(base64DecoderStream, mailInfo, validFileName);
            }, () -> {
                MimeTypes allTypes = MimeTypes.getDefaultMimeTypes();
                try {
                    org.apache.tika.mime.MimeType detectedMimeType = allTypes.forName(mimeType.getBaseType());
                    String fileName = UUID.randomUUID() + detectedMimeType.getExtension();
                    LOGGER.debug("Downloading unnamed attachment: {}", fileName);
                    String validFileName = addAttachment(mailInfo, fileName);
                    writer.writeToFile(base64DecoderStream, mailInfo, validFileName);
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
                        LOGGER.debug("Downloading nested E-Mail: {}", fileName);
                        String validFileName = addAttachment(mailInfo, fileName);
                        readContent(imapNestedMessage.getContent(), nestedMimeType, mailInfo, validFileName);
                    } catch (IOException | MessagingException exception) {
                        LOGGER.error("Failed", exception);
                    }
                }, () -> {
                    MimeTypes allTypes = MimeTypes.getDefaultMimeTypes();
                    try {
                        org.apache.tika.mime.MimeType detectedMimeType = allTypes.forName(nestedMimeType.getBaseType());
                        String fileName = UUID.randomUUID() + detectedMimeType.getExtension();
                        LOGGER.debug("Downloading unnamed nested E-Mail: {}", fileName);
                        String validFileName = addAttachment(mailInfo, fileName);
                        readContent(imapNestedMessage.getContent(), nestedMimeType, mailInfo, validFileName);
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
                LOGGER.debug("Downloading named content: {}, {}", fileName, mimeType);
                String validFileName = addAttachment(mailInfo, fileName);
                writer.writeToFile(contentText, mailInfo, validFileName);
            }, () -> {
                MimeTypes allTypes = MimeTypes.getDefaultMimeTypes();
                try {
                    org.apache.tika.mime.MimeType detectedMimeType = allTypes.forName(mimeType.getBaseType());
                    String fileName = UUID.randomUUID() + detectedMimeType.getExtension();
                    LOGGER.debug("Downloading unnamed content: {}", fileName);
                    String validFileName = addAttachment(mailInfo, fileName);
                    writer.writeToFile(contentText, mailInfo, validFileName);
                } catch (MimeTypeException exception) {
                    LOGGER.warn("Skipped unnamed content, type: {}, because: {}", mimeType, exception.getMessage());
                }
            });
    }

    private String addAttachment(MailInfo mailInfo, String fileName) {
        String validFileName = getValidAttachmentName(mailInfo, fileName);
        mailInfo.addAttachment(validFileName);
        return validFileName;
    }

    private String getValidAttachmentName(MailInfo mailInfo, String fileName) {
        if (mailInfo.getAttachments().contains(fileName)) {
            return String.format("%s_%s", mailInfo.getAttachments().size(), fileName);
        }
        return fileName;
    }

}
