package net.skoczylas.imap.backup;

import java.util.Objects;

public class MailAddress {

    private String address;
    private String encodedAddress;

    public MailAddress(String address, String encodedAddress) {
        this.address = address;
        this.encodedAddress = encodedAddress;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getEncodedAddress() {
        return encodedAddress;
    }

    public void setEncodedAddress(String encodedAddress) {
        this.encodedAddress = encodedAddress;
    }

    public String getValidAddress() {
        if (encodedAddress != null) {
            return encodedAddress;
        } else if (address != null) {
            return address;
        } else {
            return "Unknown";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MailAddress that = (MailAddress) o;
        return Objects.equals(address, that.address) && Objects.equals(encodedAddress, that.encodedAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, encodedAddress);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        if (encodedAddress != null) {
            builder.append(encodedAddress);
        }

        if (address != null && encodedAddress != null) {
            builder.append(" <");
            builder.append(address);
            builder.append(">");
        } else if (address != null) {
            builder.append(address);
        }

        return builder.toString();
    }
}
