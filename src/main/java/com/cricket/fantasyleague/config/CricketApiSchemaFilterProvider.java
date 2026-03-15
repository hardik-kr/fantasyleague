package com.cricket.fantasyleague.config;

import java.util.Set;

import org.hibernate.boot.model.relational.Namespace;
import org.hibernate.boot.model.relational.Sequence;
import org.hibernate.mapping.Table;
import org.hibernate.tool.schema.spi.SchemaFilter;
import org.hibernate.tool.schema.spi.SchemaFilterProvider;

/**
 * Prevents Hibernate ddl-auto from creating/altering tables that belong
 * to the cricketapi database. These tables are accessed via MySQL cross-database
 * VIEWs in the fantasyleague schema, not managed by Hibernate.
 */
public class CricketApiSchemaFilterProvider implements SchemaFilterProvider {

    private static final SchemaFilter EXCLUDE_CRICKETAPI = new CricketApiTableFilter();

    @Override
    public SchemaFilter getCreateFilter() {
        return EXCLUDE_CRICKETAPI;
    }

    @Override
    public SchemaFilter getDropFilter() {
        return EXCLUDE_CRICKETAPI;
    }

    @Override
    public SchemaFilter getTruncatorFilter() {
        return EXCLUDE_CRICKETAPI;
    }

    @Override
    public SchemaFilter getMigrateFilter() {
        return EXCLUDE_CRICKETAPI;
    }

    @Override
    public SchemaFilter getValidateFilter() {
        return SchemaFilter.ALL;
    }

    private static class CricketApiTableFilter implements SchemaFilter {

        private static final Set<String> EXCLUDED = Set.of("matches", "team", "player");

        @Override
        public boolean includeNamespace(Namespace namespace) {
            return true;
        }

        @Override
        public boolean includeTable(Table table) {
            return !EXCLUDED.contains(table.getName().toLowerCase());
        }

        @Override
        public boolean includeSequence(Sequence sequence) {
            return true;
        }
    }
}
