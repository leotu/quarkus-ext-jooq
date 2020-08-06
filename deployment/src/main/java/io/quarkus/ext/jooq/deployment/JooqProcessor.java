package io.quarkus.ext.jooq.deployment;

import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.logging.Logger;
import org.jooq.DSLContext;
import org.jooq.tools.LoggerListener;
import org.objectweb.asm.Opcodes;

import io.quarkus.agroal.deployment.JdbcDataSourceBuildItem;
import io.quarkus.arc.deployment.BeanContainerListenerBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem.BeanClassNameExclusion;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.recording.RecorderContext;
import io.quarkus.deployment.util.HashUtil;
import io.quarkus.ext.jooq.runtime.AbstractDslContextProducer;
import io.quarkus.ext.jooq.runtime.AbstractDslContextProducer.DslContextQualifier;
import io.quarkus.ext.jooq.runtime.JooqConfig;
import io.quarkus.ext.jooq.runtime.JooqCustomContext;
import io.quarkus.ext.jooq.runtime.JooqItemConfig;
import io.quarkus.ext.jooq.runtime.JooqTemplate;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.FieldCreator;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;

/**
 * Deployment Processor
 * 
 * <pre>
 * https://quarkus.io/guides/cdi-reference#supported_features
 * https://github.com/quarkusio/gizmo
 * https://quarkus.io/guides/datasource
 * </pre>
 * 
 * @author Leo Tu
 */
public class JooqProcessor {
    private static final Logger log = Logger.getLogger(JooqProcessor.class);

    private static final DotName DSL_CONTEXT_QUALIFIER = DotName.createSimple(DslContextQualifier.class.getName());

    private final String dslContextProducerClassName = AbstractDslContextProducer.class.getPackage().getName()
            + ".DslContextProducer";

    /**
     * Register a extension capability and feature
     *
     * @return jOOQ feature build item
     */
    @Record(ExecutionTime.STATIC_INIT)
    FeatureBuildItem feature() {
        return new FeatureBuildItem("jooq");
    }

    @SuppressWarnings("unchecked")
    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    BeanContainerListenerBuildItem build(RecorderContext recorder, JooqTemplate template,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeans, JooqConfig jooqConfig,
            BuildProducer<GeneratedBeanBuildItem> generatedBean,
            List<JdbcDataSourceBuildItem> jdbcDataSourceBuildItems) {
        if (isUnconfigured(jooqConfig)) {
            return null;
        }
        reflectiveClass.produce(new ReflectiveClassBuildItem(true, false, AbstractDslContextProducer.class));
        reflectiveClass.produce(new ReflectiveClassBuildItem(false, true, LoggerListener.class));

        if (!isPresentDialect(jooqConfig.defaultConfig)) {
            log.warn("No default sql-dialect been defined");
        }

        createDslContextProducerBean(generatedBean, unremovableBeans, jooqConfig, jdbcDataSourceBuildItems);
        return new BeanContainerListenerBuildItem(template.addContainerCreatedListener(
                (Class<? extends AbstractDslContextProducer>) recorder.classProxy(dslContextProducerClassName)));
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void configureDataSource(JooqTemplate template,
            BuildProducer<JooqInitializedBuildItem> jooqInitialized, JooqConfig jooqConfig) {
        if (isUnconfigured(jooqConfig)) {
            return;
        }
        jooqInitialized.produce(new JooqInitializedBuildItem());
    }

    private boolean isUnconfigured(JooqConfig jooqConfig) {
        if (!isPresentDialect(jooqConfig.defaultConfig) && jooqConfig.namedConfig.isEmpty()) {
            // No jOOQ has been configured so bail out
            log.debug("No jOOQ has been configured");
            return true;
        } else {
            return false;
        }
    }

    private void createDslContextProducerBean(BuildProducer<GeneratedBeanBuildItem> generatedBean,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeans, JooqConfig jooqConfig,
            List<JdbcDataSourceBuildItem> jdbcDataSourceBuildItems) {
        ClassOutput classOutput = new ClassOutput() {
            @Override
            public void write(String name, byte[] data) {
                generatedBean.produce(new GeneratedBeanBuildItem(name, data));
            }
        };
        unremovableBeans.produce(new UnremovableBeanBuildItem(new BeanClassNameExclusion(dslContextProducerClassName)));

        ClassCreator classCreator = ClassCreator.builder().classOutput(classOutput)
                .className(dslContextProducerClassName).superClass(AbstractDslContextProducer.class).build();
        classCreator.addAnnotation(ApplicationScoped.class);

        Set<String> dataSourceNames = jdbcDataSourceBuildItems.stream().map(JdbcDataSourceBuildItem::getName)
                .collect(Collectors.toSet());

        JooqItemConfig defaultConfig = jooqConfig.defaultConfig;
        if (isPresentDialect(defaultConfig)) {
            if (!DataSourceUtil.hasDefault(dataSourceNames)) {
                log.warn("Default data source not found");
            }
            if (defaultConfig.datasource.isPresent()
                    && !DataSourceUtil.isDefault(defaultConfig.datasource.get())) {
                log.warnv("Skip default data source name: {}", defaultConfig.datasource.get());
            }
            String dsVarName = "defaultDataSource";

            FieldCreator defaultDataSourceCreator = classCreator.getFieldCreator(dsVarName, DataSource.class)
                    .setModifiers(Opcodes.ACC_MODULE);

            defaultDataSourceCreator.addAnnotation(Default.class);
            defaultDataSourceCreator.addAnnotation(Inject.class);

            //
            String dialect = defaultConfig.dialect;
            MethodCreator defaultDslContextMethodCreator = classCreator.getMethodCreator("createDefaultDslContext",
                    DSLContext.class);

            defaultDslContextMethodCreator.addAnnotation(Singleton.class);
            defaultDslContextMethodCreator.addAnnotation(Produces.class);
            defaultDslContextMethodCreator.addAnnotation(Default.class);

            ResultHandle dialectRH = defaultDslContextMethodCreator.load(dialect);

            ResultHandle dataSourceRH = defaultDslContextMethodCreator.readInstanceField(
                    FieldDescriptor.of(classCreator.getClassName(), dsVarName, DataSource.class.getName()),
                    defaultDslContextMethodCreator.getThis());

            if (defaultConfig.configurationInject.isPresent()) {
                String configurationInjectName = defaultConfig.configurationInject.get();
                String injectVarName = "configuration_" + HashUtil.sha1(configurationInjectName);

                FieldCreator configurationCreator = classCreator.getFieldCreator(injectVarName, JooqCustomContext.class)
                        .setModifiers(Opcodes.ACC_MODULE);

                configurationCreator.addAnnotation(Inject.class);
                configurationCreator.addAnnotation(AnnotationInstance.create(DotNames.NAMED, null,
                        new AnnotationValue[] { AnnotationValue.createStringValue("value", configurationInjectName) }));

                ResultHandle configurationRH = defaultDslContextMethodCreator.readInstanceField(FieldDescriptor
                        .of(classCreator.getClassName(), injectVarName, JooqCustomContext.class.getName()),
                        defaultDslContextMethodCreator.getThis());

                defaultDslContextMethodCreator.returnValue( //
                        defaultDslContextMethodCreator.invokeVirtualMethod(
                                MethodDescriptor.ofMethod(AbstractDslContextProducer.class, "createDslContext",
                                        DSLContext.class, String.class, DataSource.class,
                                        JooqCustomContext.class),
                                defaultDslContextMethodCreator.getThis(), dialectRH, dataSourceRH, configurationRH));
            } else {
                ResultHandle configurationRH = defaultConfig.configuration.isPresent()
                        ? defaultDslContextMethodCreator.load(defaultConfig.configuration.get())
                        : defaultDslContextMethodCreator.loadNull();

                if (defaultConfig.configuration.isPresent()) {
                    unremovableBeans.produce(new UnremovableBeanBuildItem(
                            new BeanClassNameExclusion(defaultConfig.configuration.get())));
                }

                defaultDslContextMethodCreator.returnValue( //
                        defaultDslContextMethodCreator.invokeVirtualMethod(
                                MethodDescriptor.ofMethod(AbstractDslContextProducer.class, "createDslContext",
                                        DSLContext.class, String.class, DataSource.class, String.class),
                                defaultDslContextMethodCreator.getThis(), dialectRH, dataSourceRH, configurationRH));
            }
        }

        for (Entry<String, JooqItemConfig> configEntry : jooqConfig.namedConfig.entrySet()) {
            String named = configEntry.getKey();
            JooqItemConfig namedConfig = configEntry.getValue();
            if (!isPresentDialect(namedConfig)) {
                log.warnv("!isPresentDialect(namedConfig), named: {0}, namedConfig: {1}", named, namedConfig);
                continue;
            }
            if (!namedConfig.datasource.isPresent()) {
                log.warnv("!namedConfig.datasource.isPresent(), named: {0}, namedConfig: {1}", named, namedConfig);
                continue;
            }

            String dataSourceName = namedConfig.datasource.get();
            if (!dataSourceNames.contains(dataSourceName)) {
                log.warnv("Named: {0} data source not found", dataSourceName);
            }

            String suffix = HashUtil.sha1(named);
            String dsVarName = "dataSource_" + suffix;

            FieldCreator dataSourceCreator = classCreator.getFieldCreator(dsVarName, DataSource.class)
                    .setModifiers(Opcodes.ACC_MODULE);
            dataSourceCreator.addAnnotation(Inject.class);
            dataSourceCreator.addAnnotation(AnnotationInstance.create(DotNames.NAMED, null,
                    new AnnotationValue[] { AnnotationValue.createStringValue("value", dataSourceName) }));

            MethodCreator namedDslContextMethodCreator = classCreator
                    .getMethodCreator("createNamedDslContext_" + suffix, DSLContext.class.getName());

            namedDslContextMethodCreator.addAnnotation(ApplicationScoped.class);
            namedDslContextMethodCreator.addAnnotation(Produces.class);
            namedDslContextMethodCreator.addAnnotation(AnnotationInstance.create(DotNames.NAMED, null,
                    new AnnotationValue[] { AnnotationValue.createStringValue("value", named) }));
            namedDslContextMethodCreator.addAnnotation(AnnotationInstance.create(DSL_CONTEXT_QUALIFIER, null,
                    new AnnotationValue[] { AnnotationValue.createStringValue("value", named) }));

            ResultHandle dialectRH = namedDslContextMethodCreator.load(namedConfig.dialect);

            ResultHandle dataSourceRH = namedDslContextMethodCreator.readInstanceField(
                    FieldDescriptor.of(classCreator.getClassName(), dsVarName, DataSource.class.getName()),
                    namedDslContextMethodCreator.getThis());

            if (namedConfig.configurationInject.isPresent()) {
                String configurationInjectName = namedConfig.configurationInject.get();
                String injectVarName = "configurationInjectName" + HashUtil.sha1(configurationInjectName);

                FieldCreator configurationCreator = classCreator.getFieldCreator(injectVarName, JooqCustomContext.class)
                        .setModifiers(Opcodes.ACC_MODULE);

                configurationCreator.addAnnotation(Inject.class);
                configurationCreator.addAnnotation(AnnotationInstance.create(DotNames.NAMED, null,
                        new AnnotationValue[] { AnnotationValue.createStringValue("value", configurationInjectName) }));

                ResultHandle configurationRH = namedDslContextMethodCreator.readInstanceField(FieldDescriptor
                        .of(classCreator.getClassName(), injectVarName, JooqCustomContext.class.getName()),
                        namedDslContextMethodCreator.getThis());

                namedDslContextMethodCreator.returnValue(namedDslContextMethodCreator.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(AbstractDslContextProducer.class, "createDslContext",
                                DSLContext.class, String.class, DataSource.class, JooqCustomContext.class),
                        namedDslContextMethodCreator.getThis(), dialectRH, dataSourceRH, configurationRH));
            } else {
                ResultHandle configurationRH = namedConfig.configuration.isPresent()
                        ? namedDslContextMethodCreator.load(namedConfig.configuration.get())
                        : namedDslContextMethodCreator.loadNull();

                if (namedConfig.configuration.isPresent()) {
                    unremovableBeans.produce(
                            new UnremovableBeanBuildItem(new BeanClassNameExclusion(namedConfig.configuration.get())));
                }

                namedDslContextMethodCreator.returnValue(namedDslContextMethodCreator.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(AbstractDslContextProducer.class, "createDslContext",
                                DSLContext.class, String.class, DataSource.class, String.class),
                        namedDslContextMethodCreator.getThis(), dialectRH, dataSourceRH, configurationRH));
            }
        }

        classCreator.close();
    }

    private boolean isPresentDialect(JooqItemConfig itemConfig) {
        return itemConfig.dialect != null && !itemConfig.dialect.isEmpty();
    }
}
