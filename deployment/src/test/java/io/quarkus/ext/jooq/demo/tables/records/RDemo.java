/*
 * This file is generated by jOOQ.
 */
package io.quarkus.ext.jooq.demo.tables.records;


import io.quarkus.ext.jooq.demo.tables.QDemo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record4;
import org.jooq.Row4;
import org.jooq.impl.UpdatableRecordImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class RDemo extends UpdatableRecordImpl<RDemo> implements Record4<String, String, BigDecimal, LocalDateTime> {

    private static final long serialVersionUID = 1913445632;

    /**
     * Setter for <code>PUBLIC.DEMO.ID</code>.
     */
    public void setId(String value) {
        set(0, value);
    }

    /**
     * Getter for <code>PUBLIC.DEMO.ID</code>.
     */
    public String getId() {
        return (String) get(0);
    }

    /**
     * Setter for <code>PUBLIC.DEMO.NAME</code>.
     */
    public void setName(String value) {
        set(1, value);
    }

    /**
     * Getter for <code>PUBLIC.DEMO.NAME</code>.
     */
    public String getName() {
        return (String) get(1);
    }

    /**
     * Setter for <code>PUBLIC.DEMO.AMOUNT</code>.
     */
    public void setAmount(BigDecimal value) {
        set(2, value);
    }

    /**
     * Getter for <code>PUBLIC.DEMO.AMOUNT</code>.
     */
    public BigDecimal getAmount() {
        return (BigDecimal) get(2);
    }

    /**
     * Setter for <code>PUBLIC.DEMO.CREATED_AT</code>.
     */
    public void setCreatedAt(LocalDateTime value) {
        set(3, value);
    }

    /**
     * Getter for <code>PUBLIC.DEMO.CREATED_AT</code>.
     */
    public LocalDateTime getCreatedAt() {
        return (LocalDateTime) get(3);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<String> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record4 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row4<String, String, BigDecimal, LocalDateTime> fieldsRow() {
        return (Row4) super.fieldsRow();
    }

    @Override
    public Row4<String, String, BigDecimal, LocalDateTime> valuesRow() {
        return (Row4) super.valuesRow();
    }

    @Override
    public Field<String> field1() {
        return QDemo.$.id;
    }

    @Override
    public Field<String> field2() {
        return QDemo.$.name;
    }

    @Override
    public Field<BigDecimal> field3() {
        return QDemo.$.amount;
    }

    @Override
    public Field<LocalDateTime> field4() {
        return QDemo.$.createdAt;
    }

    @Override
    public String component1() {
        return getId();
    }

    @Override
    public String component2() {
        return getName();
    }

    @Override
    public BigDecimal component3() {
        return getAmount();
    }

    @Override
    public LocalDateTime component4() {
        return getCreatedAt();
    }

    @Override
    public String value1() {
        return getId();
    }

    @Override
    public String value2() {
        return getName();
    }

    @Override
    public BigDecimal value3() {
        return getAmount();
    }

    @Override
    public LocalDateTime value4() {
        return getCreatedAt();
    }

    @Override
    public RDemo value1(String value) {
        setId(value);
        return this;
    }

    @Override
    public RDemo value2(String value) {
        setName(value);
        return this;
    }

    @Override
    public RDemo value3(BigDecimal value) {
        setAmount(value);
        return this;
    }

    @Override
    public RDemo value4(LocalDateTime value) {
        setCreatedAt(value);
        return this;
    }

    @Override
    public RDemo values(String value1, String value2, BigDecimal value3, LocalDateTime value4) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached RDemo
     */
    public RDemo() {
        super(QDemo.$);
    }

    /**
     * Create a detached, initialised RDemo
     */
    public RDemo(String id, String name, BigDecimal amount, LocalDateTime createdAt) {
        super(QDemo.$);

        set(0, id);
        set(1, name);
        set(2, amount);
        set(3, createdAt);
    }
}
