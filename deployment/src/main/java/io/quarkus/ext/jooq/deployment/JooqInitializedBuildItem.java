package io.quarkus.ext.jooq.deployment;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Marker build item indicating the QuerySQL has been fully initialized.
 * 
 * @author Leo Tu
 */
public final class JooqInitializedBuildItem extends SimpleBuildItem {

    public JooqInitializedBuildItem() {
    }
}
