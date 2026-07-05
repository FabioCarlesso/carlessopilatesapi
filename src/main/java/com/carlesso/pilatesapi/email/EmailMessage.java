package com.carlesso.pilatesapi.email;

public record EmailMessage(String to, String subject, String htmlBody, String textBody) {
}
