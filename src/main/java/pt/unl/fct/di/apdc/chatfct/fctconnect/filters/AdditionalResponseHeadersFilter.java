package pt.unl.fct.di.apdc.chatfct.fctconnect.filters;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import java.util.List;

@Provider
public class AdditionalResponseHeadersFilter implements ContainerResponseFilter {
    public AdditionalResponseHeadersFilter() {
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        responseContext.getHeaders().add("Access-Control-Allow-Methods", "HEAD,GET,PUT,POST,DELETE,OPTIONS");
        responseContext.getHeaders().put("Access-Control-Allow-Origin", List.of("*"));
        responseContext.getHeaders().add("Access-Control-Allow-Headers", "Content-Type, X-Requested-With, X-Auth-Token");
        responseContext.getHeaders().add("Access-Control-Expose-Headers", "*");
    }
}