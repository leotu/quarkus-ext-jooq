package io.quarkus.ext.jooq.runtime;

import org.jboss.logging.Logger;

import io.quarkus.agroal.runtime.AbstractDataSourceProducer;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.arc.runtime.BeanContainerListener;
import io.quarkus.runtime.annotations.Template;

/**
 * Quarkus Template class (runtime)
 * 
 * @author <a href="mailto:leo.tu.taipei@gmail.com">Leo Tu</a>
 */
@Template
public class JooqTemplate {
    private static final Logger log = Logger.getLogger(JooqTemplate.class);

    /**
     * Build Time
     */
    public BeanContainerListener addContainerCreatedListener(
            Class<? extends AbstractDslContextProducer> dslContextProducerClass) {
        return new BeanContainerListener() {

            /**
             * Runtime Time
             */
            @Override
            public void created(BeanContainer beanContainer) { // Arc.container()
                AbstractDataSourceProducer dataSourceProducer = beanContainer
                        .instance(AbstractDataSourceProducer.class);
                if (dataSourceProducer == null) {
                    log.warn("(dataSourceProducer == null)");
                } else {
                    log.debugv("dataSourceProducer.class: {0}", dataSourceProducer.getClass().getName());
                }

                AbstractDslContextProducer dslContextProducer = beanContainer.instance(dslContextProducerClass);
                if (dslContextProducer == null) {
                    log.warn("(dslContextProducer == null)");
                } else {
                    log.debugv("dslContextProducer.class: {0}", dslContextProducer.getClass().getName());
                }

            }
        };
    }

}
