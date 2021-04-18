package net.skoczylas.imap.backup;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

class Writer {

    private static final Logger LOGGER = LoggerFactory.getLogger(Writer.class);

    private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MM_dd");

    private final List<MailInfo> mailQueue;
    private final String targetFolder;
    private final String backupFolder;
    private final String account;
    private final ExecutorService executorService = Executors.newFixedThreadPool(20);
    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(10);

    private final Template overviewTemplate;
    private final Template mailInfoTemplate;

    public Writer(List<MailInfo> mailQueue, String targetFolder, String backupFolder, String account) throws IOException {
        this.mailQueue = mailQueue;
        this.targetFolder = targetFolder;
        this.backupFolder = backupFolder;
        this.account = normalize(account);
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_31);
        configuration.setClassForTemplateLoading(getClass(), "/");

        overviewTemplate = configuration.getTemplate("overview.ftlh");
        mailInfoTemplate = configuration.getTemplate("mail-info.ftlh");
    }

    void run() {
        LOGGER.info("Overview writer started...");
        scheduledExecutorService.scheduleWithFixedDelay(this::writeOverview, 10, 10, TimeUnit.SECONDS);
    }

    void stop() {
        scheduledExecutorService.shutdown();
    }

    void writeToFile(InputStream content, MailInfo mailInfo, String fileName) {
        Path path = getPath(mailInfo);
        try {
            Files.createDirectories(path);
            Path file = Paths.get(path.toString(), normalize(fileName));
            executorService.submit(() -> writeStream(mailInfo, content, file));
        } catch (Exception exception) {
            LOGGER.error("Could not write {}: {}", fileName, exception);
        }
    }

    void writeToFile(String content, MailInfo mailInfo, String fileName) {
        Path path = getPath(mailInfo);
        try {
            Files.createDirectories(path);
            Path file = Paths.get(path.toString(), normalize(fileName));
            executorService.submit(() -> writeString(mailInfo, content, file));
        } catch (Exception exception) {
            LOGGER.error("Could not write {}: {}", fileName, exception);
        }
    }

    void writeInfoFile(MailInfo mailInfo) {
        Path path = getPath(mailInfo);
        try {
            Files.createDirectories(path);
            Path infoFile = Paths.get(path.toString(), "mail_info.txt");
            getMailInfoFromTemplate(mailInfo).ifPresent(content -> executorService.submit(() -> writeString(mailInfo, content, infoFile)));
        } catch (Exception exception) {
            LOGGER.error("Could not write mail information {}: {}", mailInfo.getNumber(), exception);
        }
    }

    private synchronized void writeOverview() {
        LOGGER.trace("Updating overview for {}", account);
        try {
            Path path = Paths.get(targetFolder, backupFolder, account);
            Files.createDirectories(path);
            Path overviewFile = Paths.get(path.toString(), "mail_index.html");
            getOverFromTemplate().ifPresent(content -> writeString(null, content, overviewFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));
        } catch (Exception exception) {
            LOGGER.error("Could not write mail overview for {}: {}", account, exception);
        }
    }

    private Path getPath(MailInfo mailInfo) {
        return Paths.get(targetFolder, getPaths(mailInfo, true));
    }

    private Path getRelativePath(MailInfo mailInfo) {
        return Paths.get("", getPaths(mailInfo, false));
    }

    private String normalize(String value) {
        return value
                .replaceAll("[^\\sa-zA-Z0-9_.ÄÖÜäöüß+-]", "_")
                .replaceAll("(\\r|\\n|\\t)", "");
    }

    private String[] getPaths(MailInfo mailInfo, boolean root) {
        LocalDateTime receivedDate = mailInfo.getReceivedAt();
        String hash = mailInfo.getHash();
        String yearFolder = YEAR_FORMATTER.format(receivedDate);
        String monthDayFolder = MONTH_FORMATTER.format(receivedDate);
        List<String> folder = new ArrayList<>();
        if (root) {
            folder.add(backupFolder);
            folder.add(account);
        }
        folder.addAll(mailInfo.getFolder());
        folder.add(yearFolder);
        folder.add(monthDayFolder);
        folder.add(hash);
        return folder.toArray(new String[0]);
    }

    private void writeStream(MailInfo mailInfo, InputStream inputStream, Path file) {
        if (!Files.exists(file)) {
            File targetFile = file.toFile();
            try (OutputStream outStream = new FileOutputStream(targetFile)) {
                byte[] buffer = inputStream.readAllBytes();
                outStream.write(buffer);
                outStream.flush();
                LOGGER.trace("File written for Mail {}: {}", mailInfo.getNumber(), file.getFileName());
            } catch (IOException exception) {
                LOGGER.error("Could not write stream to file {}: {}", file.getFileName(), exception);
            }
        } else {
            LOGGER.error("Could not write stream because file {}  already exists!", file.getFileName());
        }
    }

    private void writeString(MailInfo mailInfo, String content, Path file) {
        writeString(mailInfo, content, file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private void writeString(MailInfo mailInfo, String content, Path file, StandardOpenOption... options) {
        try {
            Files.writeString(file, content, options);
            if (mailInfo != null) {
                LOGGER.trace("File written for Mail {}: {}", mailInfo.getNumber(), file.getFileName());
            }
        } catch (FileAlreadyExistsException exception) {
            // Nothing to do, was expected
            LOGGER.debug("File already exists {}: {}", file.getFileName(), exception);
        } catch (IOException | IllegalArgumentException | UnsupportedOperationException | SecurityException exception) {
            LOGGER.error("Could not write content file {}: {}", file.getFileName(), exception);
        }
    }

    private Optional<String> getMailInfoFromTemplate(MailInfo mailInfo) {
        try {
            Map<String, String> root = toMap(mailInfo);
            StringWriter stringWriter = new StringWriter();
            mailInfoTemplate.process(root, stringWriter);
            return Optional.of(stringWriter.toString());
        } catch (IOException | TemplateException exception) {
            LOGGER.error("Could generate mail info from template", exception);
        }

        return Optional.empty();
    }

    private Optional<String> getOverFromTemplate() {
        try {
            Map<String, Object> root = new HashMap<>();
            List<Map<String, String>> mails = mailQueue.stream()
                    .map(this::toMap)
                    .collect(Collectors.toList());

            root.put("count", mailQueue.size());
            root.put("mails", mails);
            StringWriter stringWriter = new StringWriter();
            overviewTemplate.process(root, stringWriter);
            return Optional.of(stringWriter.toString());
        } catch (IOException | TemplateException exception) {
            LOGGER.error("Could generate overview from template", exception);
        }

        return Optional.empty();
    }

    private Map<String, String> toMap(MailInfo mailInfo) {
        Map<String, String> root = new HashMap<>();
        root.put("subject", mailInfo.getSubject());
        root.put("date", Utility.getDate(mailInfo.getReceivedAt()));
        root.put("from", mailInfo.getFrom().stream().map(MailAddress::toString).collect(Collectors.joining(", ")));
        root.put("to", mailInfo.getTo().stream().map(MailAddress::toString).collect(Collectors.joining(", ")));
        if (!mailInfo.getAttachments().isEmpty()) {
            root.put("attachments", String.join(", ", mailInfo.getAttachments()));
        }
        root.put("folder", String.join("/", mailInfo.getFolder()));
        root.put("link", String.valueOf(getRelativePath(mailInfo)));
        return root;
    }

}