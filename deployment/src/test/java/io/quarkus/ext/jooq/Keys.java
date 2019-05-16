/*
 * Copyright © 2018, Syncpo.com. All Rights Reserved.
 * 
 * Reproduction by any means, or disclosure to parties who are not employees
 * of Syncpo is forbidden unless authorized.
 */
package io.quarkus.ext.jooq;

import org.jooq.UniqueKey;
import org.jooq.impl.AbstractKeys;

/**
 * A class modelling foreign key relationships and constraints of tables of the
 * <code></code> schema.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Keys {

    // -------------------------------------------------------------------------
    // IDENTITY definitions
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // UNIQUE and PRIMARY KEY definitions
    // -------------------------------------------------------------------------

    public static final UniqueKey<RDemo> DEMO_PKEY = UniqueKeys0.DEMO_PKEY;

    // -------------------------------------------------------------------------
    // FOREIGN KEY definitions
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // [#1459] distribute members to avoid static initialisers > 64kb
    // -------------------------------------------------------------------------

    private static class UniqueKeys0 extends AbstractKeys {

        public static final UniqueKey<RDemo> DEMO_PKEY = createUniqueKey(QDemo.$, "demo_pkey", QDemo.$.id);
    }
}
