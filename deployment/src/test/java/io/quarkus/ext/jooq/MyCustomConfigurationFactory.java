package io.quarkus.ext.jooq;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Named;
import javax.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkus.ext.jooq.runtime.JooqCustomContext;

/**
 * 
 * @author Leo Tu
 */
//@ApplicationScoped
@Singleton
public class MyCustomConfigurationFactory {
    private static final Logger LOGGER = Logger.getLogger(MyCustomConfigurationFactory.class);

    @PostConstruct
    void onPostConstruct() {
        LOGGER.debug("MyCustomConfigurationFactory: onPostConstruct");
    }

    @ApplicationScoped
    @Produces
    @Named("myCustomConfiguration2")
    public JooqCustomContext create() {
        LOGGER.debug("MyCustomConfigurationFactory: create");
        return new JooqCustomContext() {
        };
    }
}
