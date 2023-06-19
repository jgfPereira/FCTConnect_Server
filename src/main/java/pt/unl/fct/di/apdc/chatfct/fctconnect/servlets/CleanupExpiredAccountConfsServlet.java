package pt.unl.fct.di.apdc.chatfct.fctconnect.servlets;

import pt.unl.fct.di.apdc.chatfct.fctconnect.resources.ConfirmAccountResource;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CleanupExpiredAccountConfsServlet extends HttpServlet {

    private static final ConfirmAccountResource ACCOUNT_CONF;

    static {
        ACCOUNT_CONF = new ConfirmAccountResource();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        ACCOUNT_CONF.cleanupExpiredAccountConfirmations();
    }
}