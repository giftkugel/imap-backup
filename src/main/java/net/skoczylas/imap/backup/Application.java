package net.skoczylas.imap.backup;

import jakarta.mail.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        LOGGER.info("Info server={}, port={}, user={}", args[0], args[1], args[2]);

        Properties properties = new Properties();
        properties.setProperty("mail.imap.host", args[0]);
        properties.setProperty("mail.imap.port", args[1]);

        // SSL setting
        properties.setProperty("mail.imap.ssl.enable", "true");

        Session session = Session.getDefaultInstance(properties);
        ImapBackup imapBackup = new ImapBackup(session, args[2], args[3]);

        imapBackup.run();
    }

}
