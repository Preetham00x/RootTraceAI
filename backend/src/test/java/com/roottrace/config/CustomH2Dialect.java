package com.roottrace.config;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.sql.internal.DdlTypeImpl;

public class CustomH2Dialect extends H2Dialect {

    @Override
    public void contributeTypes(TypeContributions typeContributions, org.hibernate.service.ServiceRegistry serviceRegistry) {
        super.contributeTypes(typeContributions, serviceRegistry);
        typeContributions.getTypeConfiguration().getDdlTypeRegistry().addDescriptor(
            new DdlTypeImpl(SqlTypes.VECTOR, "real array", this)
        );
    }
}
