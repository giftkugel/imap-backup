package net.skoczylas.imap.backup;

import jakarta.activation.MimeType;
import org.apache.commons.codec.digest.DigestUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MailInfo {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final List<String> folder = new ArrayList<>();
    private final String from;
    private final String to;
    private final String subject;
    private final LocalDateTime receivedAt;
    private final String hash;
    private final MimeType mimeType;

    private final List<String> attachments = new ArrayList<>();

    public MailInfo(List<String> folder, String from, String to, String subject, LocalDateTime receivedAt, MimeType mimeType) {
        this.folder.addAll(folder);
        this.from = from;
        this.to = to;
        this.subject = subject;
        this.receivedAt = receivedAt;
        this.mimeType = mimeType;

        String folderList = String.join("/", this.folder);
        this.hash = DigestUtils.sha256Hex(folderList + receivedAt + subject + from + to);
    }

    public List<String> getFolder() {
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
    
    public String asFormattedString(String separator, boolean withMimeType) {
        StringBuilder builder = new StringBuilder();
        builder.append(String.join("/", this.folder));
        builder.append(separator);
        if (withMimeType) {
            builder.append(mimeType);
            builder.append(separator);
        }
        builder.append(getFormattedDate(receivedAt));
        builder.append(separator);
        builder.append("Subject: ");
        builder.append(subject);
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

    public String asHTMLTableString(String link) {
        StringBuilder builder = new StringBuilder();
        builder.append("<tr><td nowrap>");
        builder.append(String.join("/", this.folder));
        builder.append("</td>");
        builder.append("<td nowrap>");
        builder.append(getFormattedDate(receivedAt));
        builder.append("</td>");
        builder.append("<td nowrap><a href=\"");
        builder.append(link);
        builder.append("\">");
        builder.append(subject);
        builder.append("</a>");
        builder.append("</td>");
        builder.append("<td>");
        builder.append(from);
        builder.append("</td>");
        builder.append("<td>");
        builder.append(to);
        builder.append("</td>");
        if (!attachments.isEmpty()) {
            builder.append("<td>");
            builder.append(String.join(", ", attachments));
            builder.append("</td>");
        } else {
            builder.append("<td>&nbsp;</td>");
        }
        builder.append("</tr>");
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
        return Objects.equals(folder, mailInfo.folder) && Objects.equals(from, mailInfo.from) && Objects.equals(to, mailInfo.to) && Objects.equals(subject, mailInfo.subject) && Objects.equals(receivedAt, mailInfo.receivedAt) && Objects.equals(mimeType, mailInfo.mimeType) && Objects.equals(hash, mailInfo.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(folder, from, to, subject, receivedAt, mimeType, hash);
    }

    @Override
    public String toString() {
        return "MailInfo{" +
                "folder='" + folder + '\'' +
                ", from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", subject='" + subject + '\'' +
                ", receivedAt=" + receivedAt +
                ", mimeType=" + mimeType +
                ", hash='" + hash + '\'' +
                '}';
    }
}
