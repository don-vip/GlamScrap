package com.github.donvip.archscrap;

import java.sql.Types;

import org.hibernate.dialect.HSQLDialect;

public class ProperHsqlDialect extends HSQLDialect {

    public ProperHsqlDialect() {
        // Workaround to the erroneous implementation of CLOB in HSQLDialect
        // See https://stackoverflow.com/a/26805926/2257172
        registerColumnType(Types.CLOB, "clob");
    }
}
