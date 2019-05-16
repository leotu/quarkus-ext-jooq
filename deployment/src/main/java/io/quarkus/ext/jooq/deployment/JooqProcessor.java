package io.quarkus.ext.jooq.deployment;

import java.util.Map.Entry;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.logging.Logger;
import org.jooq.DSLContext;
import org.jooq.tools.LoggerListener;
import org.objectweb.asm.Opcodes;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.deployment.DataSourceInitializedBuildItem;
import io.quarkus.agroal.runtime.AgroalBuildTimeConfig;
import io.quarkus.agroal.runtime.AgroalTemplate;
import io.quarkus.arc.deployment.BeanContainerListenerBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem.BeanClassNameExclusion;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.substrate.ReflectiveClassBuildItem;
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
 * </pre>
 * 
 * @author <a href="mailto:leo.tu.taipei@gmail.com">Leo Tu</a>
 */
public class JooqProcessor {
    private static final Logger log = Logger.getLogger(JooqProcessor.class);

    private static final DotName DSL_CONTEXT_QUALIFIER = DotName.createSimple(DslContextQualifier.class.getName());

    private final String dslContextProducerClassName = AbstractDslContextProducer.class.getPackage().getName() + "."
            + "DslContextProducer";

    /**
     * Register a extension capability and feature
     *
     * @return jOOQ feature build item
     */
    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep(providesCapabilities = "io.quarkus.ext.jooq")
    FeatureBuildItem featureBuildItem() {
        return new FeatureBuildItem("jooq");
    }

    @SuppressWarnings("unchecked")
    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    BeanContainerListenerBuildItem build(RecorderContext recorder, JooqTemplate template,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeans, JooqConfig jooqConfig,
            BuildProducer<GeneratedBeanBuildItem> generatedBean, AgroalBuildTimeConfig agroalBuildTimeConfig) {

        reflectiveClass.produce(new ReflectiveClassBuildItem(true, false, AbstractDslContextProducer.class));
        reflectiveClass.produce(new ReflectiveClassBuildItem(false, true, LoggerListener.class));

        if (!isPresentDialect(jooqConfig.defaultConfig)) {
            log.warn("No default sql-dialect been defined");
        }

        createDslContextProducerBean(generatedBean, unremovableBeans, jooqConfig, agroalBuildTimeConfig);
        return new BeanContainerListenerBuildItem(template.addContainerCreatedListener(
                (Class<? extends AbstractDslContextProducer>) recorder.classProxy(dslContextProducerClassName)));
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void configureDataSource(JooqTemplate template, DataSourceInitializedBuildItem dataSourceInitialized,
            BuildProducer<JooqInitializedBuildItem> jooqInitialized, JooqConfig jooqConfig) {

        if (!isPresentDialect(jooqConfig.defaultConfig) && jooqConfig.namedConfig.isEmpty()) {
            // No jOOQ has been configured so bail out
            log.info("No jOOQ has been configured");
            return;
        }
        jooqInitialized.produce(new JooqInitializedBuildItem());
    }

    private void createDslContextProducerBean(BuildProducer<GeneratedBeanBuildItem> generatedBean,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeans, JooqConfig jooqConfig,
            AgroalBuildTimeConfig dataSourceConfig) {
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

        JooqItemConfig defaultConfig = jooqConfig.defaultConfig;
        if (isPresentDialect(defaultConfig)) {
            if (!dataSourceConfig.defaultDataSource.driver.isPresent()) {
                log.warn("Default dataSource not found");
                System.err.println(">>> Default dataSource not found");
            }
            if (defaultConfig.datasource.isPresent()
                    && !AgroalTemplate.DEFAULT_DATASOURCE_NAME.equals(defaultConfig.datasource.get())) {
                log.warn("Skip default dataSource name: " + defaultConfig.datasource.get());
            }
            String dsVarName = "defaultDataSource";

            // FIXME: Lazy Initialize DataSource ?
            // Type[] args = new Type[] {
            // Type.create(DotName.createSimple(AgroalDataSource.class.getName()),
            // Kind.CLASS) };
            // ParameterizedType type =
            // ParameterizedType.create(DotName.createSimple(Instance.class.getName()),
            // args, null);
            // log.debug("type: " + type); //
            // javax.enterprise.inject.Instance<io.agroal.api.AgroalDataSource>
            // FieldCreator defaultDataSourceCreator = classCreator.getFieldCreator(varName,
            // DescriptorUtils.typeToString(type))
            // .setModifiers(Opcodes.ACC_MODULE);

            FieldCreator defaultDataSourceCreator = classCreator.getFieldCreator(dsVarName, AgroalDataSource.class)
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
                    FieldDescriptor.of(classCreator.getClassName(), dsVarName, AgroalDataSource.class.getName()),
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
                                        DSLContext.class, String.class, AgroalDataSource.class,
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
                                        DSLContext.class, String.class, AgroalDataSource.class, String.class),
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
                log.warnv("(!config.datasource.isPresent()), named: {0}, namedConfig: {1}", named, namedConfig);
                continue;
            }

            String dataSourceName = namedConfig.datasource.get();
            if (!dataSourceConfig.namedDataSources.containsKey(dataSourceName)) {
                log.warnv("Named: '{0}' dataSource not found", dataSourceName);
                System.err.println(">>> Named: '" + dataSourceName + "' dataSource not found");
            }

            String suffix = HashUtil.sha1(named);
            String dsVarName = "dataSource_" + suffix;

            FieldCreator dataSourceCreator = classCreator.getFieldCreator(dsVarName, AgroalDataSource.class)
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
                    FieldDescriptor.of(classCreator.getClassName(), dsVarName, AgroalDataSource.class.getName()),
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
                                DSLContext.class, String.class, AgroalDataSource.class, JooqCustomContext.class),
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
                                DSLContext.class, String.class, AgroalDataSource.class, String.class),
                        namedDslContextMethodCreator.getThis(), dialectRH, dataSourceRH, configurationRH));
            }
        }

        classCreator.close();
    }

    private boolean isPresentDialect(JooqItemConfig itemConfig) {
        return itemConfig.dialect != null && !itemConfig.dialect.isEmpty();
    }
}
