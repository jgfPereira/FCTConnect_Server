package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;

import java.util.logging.Logger;

public final class RegisterEmailConfirmationUtils {

    private static final Logger LOG = Logger.getLogger(RegisterEmailConfirmationUtils.class.getName());
    private static final String SENDGRID_API_KEY_ENV = "SENDGRID_API_KEY";
    private static final String SENDER_EMAIL_ENV = "SENDER_EMAIL";
    private static final String SENDGRID_SEND_EMAIL_ENDPOINT = "mail/send";
    private static final String SENDGRID_CONTENT_TYPE = "text/html";
    private static final String EMAIL_SUBJECT = "Account Confirmation - FCTConnect";
    private static final String HTML_STR_MSG_FMT = "<html>\n" +
            "<head>\n" +
            "    <style>\n" +
            "        body {\n" +
            "            font-family: Arial, sans-serif;\n" +
            "            margin: 0;\n" +
            "            padding: 20px;\n" +
            "        }\n" +
            "\n" +
            "        h1,\n" +
            "        h3 {\n" +
            "            color: #333333;\n" +
            "        }\n" +
            "\n" +
            "        h6 {\n" +
            "            color: darkgray;\n" +
            "            margin-top: 20px;\n" +
            "        }\n" +
            "\n" +
            "        p {\n" +
            "            color: #666666;\n" +
            "\n" +
            "        }\n" +
            "\n" +
            "        a {\n" +
            "            display: inline-block;\n" +
            "            margin-top: 20px;\n" +
            "            padding: 10px 20px;\n" +
            "            background-color: #007bff;\n" +
            "            color: #ffffff;\n" +
            "            text-decoration: none;\n" +
            "            border-radius: 4px;\n" +
            "        }\n" +
            "\n" +
            "        .confirmation-link {\n" +
            "            margin-top: 0;\n" +
            "        }\n" +
            "\n" +
            "        .confirmation-link:hover {\n" +
            "            text-decoration: underline;\n" +
            "        }\n" +
            "\n" +
            "        .logo {\n" +
            "            width: 400px;\n" +
            "            height: auto;\n" +
            "            margin-top: -10px;\n" +
            "        }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "<h1>Confirm Your Account</h1>\n" +
            "<h3>Welcome to FCTConnect!</h3>\n" +
            "<img alt=\"FCTConnect Logo\" class=\"logo\" src=\"https://storage.googleapis.com/fctconnect23.appspot.com/logo.jpeg\"/>\n" +
            "<p>Please click the following button to confirm your account:</p>\n" +
            "<a class=\"https://fctconnect23.oa.r.appspot.com/rest/register/confirm?code=%s\" href=\"your-confirmation-url\">Confirm Account</a>\n" +
            "<h6>If you didn't create an account in FCTConnect, please ignore this message.</h6>\n" +
            "</body>\n" +
            "</html>\n";

    private RegisterEmailConfirmationUtils() {
    }

    public static boolean sendEmail(String userEmail, String code) {
        try {
            final String sendgridApiKey = System.getenv(SENDGRID_API_KEY_ENV);
            final String senderEmail = System.getenv(SENDER_EMAIL_ENV);
            Email from = new Email(senderEmail);
            Email to = new Email(userEmail);
            Content content = new Content(SENDGRID_CONTENT_TYPE, String.format(HTML_STR_MSG_FMT, code));
            Mail email = new Mail(from, EMAIL_SUBJECT, to, content);
            SendGrid sg = new SendGrid(sendgridApiKey);
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint(SENDGRID_SEND_EMAIL_ENDPOINT);
            request.setBody(email.build());
            Response response = sg.api(request);
            LOG.info(response.getBody());
            return true;
        } catch (Exception e) {
            LOG.severe("Send Email Error -> " + e.getLocalizedMessage());
            return false;
        }
    }
}