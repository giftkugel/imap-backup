package net.skoczylas.imap.backup;

import jakarta.mail.*;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

public class Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        Options options = new Options();
        options.addRequiredOption("h", "host",true, "IMAP server (e.g. imap.gmx.net)");
        options.addRequiredOption("u", "user",true, "Username");
        options.addRequiredOption("p", "password",true, "Password");
        options.addOption(null, "port",true, "Server port (default: 993)");
        options.addOption("s", "ssl",false, "Use SSL (default: SSL)");
        options.addOption("o", "output",true, String.format("Output folder, default: %s", System.getProperty("user.home")));
        options.addOption("t", "template",true, "Template (table or grid, table is default)");
        try {
            CommandLineParser commandLineParser = new DefaultParser();
            CommandLine commandLine = commandLineParser.parse(options, args);

            String host = commandLine.getOptionValue("h");
            String port = Optional.ofNullable(commandLine.getOptionValue("port")).orElse("993");
            String username = commandLine.getOptionValue("u");
            String password = commandLine.getOptionValue("p");
            String output = Optional.ofNullable(commandLine.getOptionValue("o")).orElse(System.getProperty("user.home"));
            String template = Optional.ofNullable(commandLine.getOptionValue("t")).orElse("table");
            String useSSL = Optional.of(String.valueOf(options.hasOption("s"))).orElse("true");

            LOGGER.info("Info server={}, port={}, ssl={}, user={}, output={}, template={}", host, port, options.hasOption("s"), username, output, template);

            Properties properties = new Properties();
            properties.setProperty("mail.imap.host", host);
            properties.setProperty("mail.imap.port", port);

            // SSL setting
            if (options.hasOption("s")) {
                properties.setProperty("mail.imap.ssl.enable", useSSL);
            }

            Session session = Session.getDefaultInstance(properties);

            ImapBackup imapBackup = new ImapBackup(session, output, template, username, password);
            imapBackup.run();
        } catch (IOException exception) {
            LOGGER.error("Could not start backup: {}", exception.getMessage());
        } catch (ParseException exception) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.setWidth(240);
            formatter.printHelp("imap-backup", options);
        }

    }

}
