package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;

public final class RegisterEmailConfirmationUtils {

    private static final String SMTP_SERVER = "smtp.gmail.com";
    private static final int SMTP_PORT = 587;

    public void sendEmail(String userEmail) throws EmailException {
        Email email = new SimpleEmail();
        email.setHostName(SMTP_SERVER);
        email.setSmtpPort(SMTP_PORT);
        email.setStartTLSEnabled(true);
        email.setAuthenticator(new DefaultAuthenticator("your-email@gmail.com", "your-password"));


        // Set the sender and recipient
        email.setFrom("your-email@gmail.com");
        email.addTo(userEmail);
        email.setSubject("Test Email");
        email.setMsg("This is a test email.");

        // Send the email
        email.send();
    }


}