package net.skoczylas.imap.backup;

import jakarta.activation.MimeType;
import org.apache.commons.codec.digest.DigestUtils;

import java.time.LocalDateTime;
import java.util.*;

public class MailInfo {

    private final int number;
    private final Deque<String> folder = new ArrayDeque<>();
    private final List<MailAddress> from;
    private final List<MailAddress> to;
    private final String subject;
    private final LocalDateTime receivedAt;
    private final String hash;
    private final MimeType mimeType;

    private final List<String> attachments = new ArrayList<>();

    public MailInfo(int number, Deque<String> folder, List<MailAddress> from, List<MailAddress> to, String subject, LocalDateTime receivedAt, MimeType mimeType) {
        this.number = number;
        this.folder.addAll(folder);
        this.from = from;
        this.to = to;
        this.subject = subject;
        this.receivedAt = receivedAt;
        this.mimeType = mimeType;

        String folderList = String.join("/", this.folder);
        this.hash = DigestUtils.sha256Hex(folderList + receivedAt + subject + from + to);
    }

    public int getNumber() {
        return number;
    }

    public Deque<String> getFolder() {
        return folder;
    }

    public List<MailAddress> getFrom() {
        return from;
    }

    public List<MailAddress> getTo() {
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

    public MimeType getMimeType() {
        return mimeType;
    }

    public List<String> getAttachments() {
        return attachments;
    }

    public void addAttachment(String fileName) {
        attachments.add(fileName);
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
                "number=" + number +
                ", folder=" + folder +
                ", from=" + from +
                ", to=" + to +
                ", subject='" + subject + '\'' +
                ", receivedAt=" + receivedAt +
                ", hash='" + hash + '\'' +
                ", mimeType=" + mimeType +
                ", attachments=" + attachments +
                '}';
    }
}
