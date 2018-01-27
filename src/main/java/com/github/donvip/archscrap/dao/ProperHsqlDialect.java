/**
 * This file is part of ArchScrap.
 *
 *  ArchScrap is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  ArchScrap is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with ArchScrap. If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.donvip.archscrap.dao;

import java.sql.Types;

import org.hibernate.dialect.HSQLDialect;

public class ProperHsqlDialect extends HSQLDialect {

    public ProperHsqlDialect() {
        // Workaround to the erroneous implementation of CLOB in HSQLDialect
        // See https://stackoverflow.com/a/26805926/2257172
        registerColumnType(Types.CLOB, "clob");
    }
}
