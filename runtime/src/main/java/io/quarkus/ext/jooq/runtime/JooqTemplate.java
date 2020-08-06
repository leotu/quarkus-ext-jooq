package io.quarkus.ext.jooq.runtime;

import org.jboss.logging.Logger;

import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.arc.runtime.BeanContainerListener;
import io.quarkus.runtime.annotations.Recorder;

/**
 * Quarkus Template class (runtime)
 * 
 * @author Leo Tu
 */
@Recorder
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
