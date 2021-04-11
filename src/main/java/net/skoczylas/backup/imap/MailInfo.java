package net.skoczylas.backup.imap;

import org.apache.commons.codec.digest.DigestUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MailInfo {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String folder;
    private final String from;
    private final String to;
    private final String subject;
    private final LocalDateTime receivedAt;
    private final String hash;

    private final List<String> attachments = new ArrayList<>();

    public MailInfo(String folder, String from, String to, String subject, LocalDateTime receivedAt) {
        this.folder = folder;
        this.from = from;
        this.to = to;
        this.subject = subject;
        this.receivedAt = receivedAt;
        this.hash = DigestUtils.sha256Hex(folder + receivedAt + subject + from + to);
    }

    public String getFolder() {
        return folder;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public String getSubject() {
        return subject;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public String getHash() {
        return hash;
    }

    public void addAttachment(String fileName) {
        attachments.add(fileName);
    }
    
    public String asFormattedString(String separator) {
        StringBuilder builder = new StringBuilder();
        builder.append(folder);
        builder.append(separator);
        builder.append(getFormattedDate(receivedAt));
        builder.append(separator);
        builder.append("Subject: ");
        builder.append(String.format("'%s'", subject));
        builder.append(separator);
        builder.append("From: ");
        builder.append(from);
        builder.append(separator);
        builder.append("To: ");
        builder.append(to);
        if (!attachments.isEmpty()) {
            builder.append(separator);
            builder.append("Attachments: ");
            builder.append(String.join(", ", attachments));
        }
        return builder.toString();
    }

    private String getFormattedDate(LocalDateTime localDateTime) {
        if (localDateTime != null) {
            return FORMATTER.format(localDateTime);
        } else {
            return "";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MailInfo mailInfo = (MailInfo) o;
        return Objects.equals(folder, mailInfo.folder) && Objects.equals(from, mailInfo.from) && Objects.equals(to, mailInfo.to) && Objects.equals(subject, mailInfo.subject) && Objects.equals(receivedAt, mailInfo.receivedAt) && Objects.equals(hash, mailInfo.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(folder, from, to, subject, receivedAt, hash);
    }

    @Override
    public String toString() {
        return "MailInfo{" +
                "folder='" + folder + '\'' +
                ", from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", subject='" + subject + '\'' +
                ", receivedAt=" + receivedAt +
                ", hash='" + hash + '\'' +
                '}';
    }
}
