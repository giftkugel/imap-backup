package net.skoczylas.imap.backup;

import jakarta.activation.MimeType;
import jakarta.activation.MimeTypeParameterList;
import jakarta.activation.MimeTypeParseException;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

class Utility {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(Utility.class);

    static Optional<Object> getContent(Message message) {
        try {
            return Optional.ofNullable(message.getContent());
        } catch (MessagingException | IOException exception) {
            LOGGER.warn("Could not read content: {}", exception.getMessage());
        }

        return Optional.empty();
    }

    static Optional<Object> getContent(Part part) {
        try {
            return Optional.ofNullable(part.getContent());
        } catch (MessagingException | IOException exception) {
            LOGGER.warn("Could not read content of part", exception);
        }

        return Optional.empty();
    }

    static int getCount(MimeMultipart mimeMultipart) {
        try {
            return mimeMultipart.getCount();
        } catch (MessagingException exception) {
            LOGGER.warn("Could not determinate amount of parts: {}", exception.getMessage());
        }
        return  0;
    }

    static List<MailAddress> getAddresses(Address... addresses) {
        if (addresses != null) {
            return Arrays.stream(addresses)
                    .filter(Objects::nonNull)
                    .filter(InternetAddress.class::isInstance)
                    .map(InternetAddress.class::cast)
                    .map(Utility::toMailAddress)
                    .collect(Collectors.toList());
        } else {
            LOGGER.warn("Could not read addresses");
            return Collections.emptyList();
        }
    }

    static Optional<MimeType> getMimeType(String contentType) {
        try {
            return Optional.of(new MimeType(contentType));
        } catch (MimeTypeParseException exception) {
            LOGGER.warn("Could not determinate content type: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    static Optional<String> getSubject(Message message) {
        try {
            if (StringUtils.isNotBlank(message.getSubject())) {
                return Optional.ofNullable(MimeUtility.decodeText(message.getSubject()));
            }
        } catch (MessagingException | UnsupportedEncodingException exception) {
            LOGGER.warn("Could not read subject: {}", exception.getMessage());
        }

        return Optional.empty();
    }


    static Optional<String> getFileName(MimeType mimeType) {
        if (mimeType == null) {
            return Optional.empty();
        }
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
            return Optional.of(collectedFileName);
        }
        if (StringUtils.isNotBlank(fileName)) {
            return Optional.of(fileName);
        }

        return Optional.empty();
    }

    static LocalDateTime convertToLocalDateTimeViaInstant(Date dateToConvert) {
        return dateToConvert.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    private static MailAddress toMailAddress(InternetAddress address) {
        return new MailAddress(address.getAddress(), address.getPersonal());
    }

}
