package org.jasig.cas.web.flow.resolver;

import com.google.common.collect.ImmutableSet;
import org.jasig.cas.authentication.Authentication;
import org.jasig.cas.services.MultifactorAuthenticationProvider;
import org.jasig.cas.services.RegisteredService;
import org.jasig.cas.web.support.WebUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;

/**
 * This is {@link RequestParameterAuthenticationPolicyWebflowEventResolver}
 * that attempts to resolve the next event basef on the authentication providers of this service.
 *
 * @author Misagh Moayyed
 * @since 4.3.0
 */
@Component("requestParameterAuthenticationPolicyWebflowEventResolver")
public class RequestParameterAuthenticationPolicyWebflowEventResolver extends AbstractCasWebflowEventResolver {

    @Value("${cas.mfa.request.parameter:authn_method}")
    private String parameterName;

    @Override
    protected Set<Event> resolveInternal(final RequestContext context) {
        final RegisteredService service = WebUtils.getRegisteredService(context);
        final Authentication authentication = WebUtils.getAuthentication(context);

        if (service == null || authentication == null) {
            logger.debug("No service or authentication is available to determine event for principal");
            return null;
        }
        final HttpServletRequest request = WebUtils.getHttpServletRequest(context);
        final String[] values = request.getParameterValues(this.parameterName);
        if (values != null && values.length > 0) {
            logger.debug("Received request parameter {} as {}", this.parameterName, values);

            final Map<String, MultifactorAuthenticationProvider> providerMap =
                    getAllMultifactorAuthenticationProvidersFromApplicationContext();
            if (providerMap == null || providerMap.isEmpty()) {
                logger.warn("No multifactor authentication providers are available in the application context");
                return null;
            }

            for (final MultifactorAuthenticationProvider provider : providerMap.values()) {
                try {
                    if (provider.getId().equals(values[0]) && provider.verify(service)) {
                        logger.debug("Attempting to build an event based on the authentication provider [{}] and service [{}]",
                                provider, service.getName());
                        final Event event = validateEventIdForMatchingTransitionInContext(provider.getId(), context,
                                buildEventAttributeMap(authentication.getPrincipal(), service, provider));
                        return ImmutableSet.of(event);
                    }
                } catch (final Exception e) {
                    logger.warn("Could not verify multifactor authentication provider {}", provider, e);
                }
            }
        }
        logger.debug("No values could be found for request parameter {}", this.parameterName);
        return null;
    }
}
