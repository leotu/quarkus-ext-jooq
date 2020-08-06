package io.quarkus.ext.jooq.runtime;

import org.jboss.logging.Logger;
import org.jooq.Configuration;
import org.jooq.conf.RenderQuotedNames;

/**
 * Custom Configuration
 * 
 * @author Leo Tu
 */
public interface JooqCustomContext {
    static final Logger LOGGER = Logger.getLogger(JooqCustomContext.class);

    /**
     * File "jooq-settings.xml"
     */
    default public void apply(Configuration configuration) {
        configuration.settings()
                .withRenderCatalog(false)
                .withRenderSchema(false)
                .withRenderFormatted(false)
                .withRenderQuotedNames(RenderQuotedNames.EXPLICIT_DEFAULT_UNQUOTED)
                .withQueryTimeout(60); // seconds
    }
}
