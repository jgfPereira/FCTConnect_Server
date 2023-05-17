package pt.unl.fct.di.apdc.chatfct.fctconnect.servlets;

import pt.unl.fct.di.apdc.chatfct.fctconnect.util.TokenUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CleanupRevokedTokensServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        TokenUtils.cleanupRevokedTokens();
    }
}