package com.jn.sqlhelper.examples.test.ddlmodel;

import com.jn.langx.util.collection.Collects;
import com.jn.langx.util.function.Consumer;
import com.jn.langx.util.io.IOs;
import com.jn.sqlhelper.common.connection.ConnectionConfiguration;
import com.jn.sqlhelper.common.connection.ConnectionFactory;
import com.jn.sqlhelper.common.ddl.dump.CommonTableGenerator;
import com.jn.sqlhelper.common.ddl.model.*;
import com.jn.sqlhelper.common.ddl.model.internal.TableType;
import com.jn.sqlhelper.common.resultset.BeanRowMapper;
import com.jn.sqlhelper.common.resultset.RowMapperResultSetExtractor;
import org.junit.Test;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class GetDatabaseInfoTests {
    @Test
    public void test() throws Throwable {
        InputStream inputStream = GetDatabaseInfoTests.class.getClassLoader().getResourceAsStream("jdbc.properties");
        ConnectionConfiguration connectionConfiguration = null;
        try {
            connectionConfiguration = ConnectionConfiguration.loadConfig(inputStream);
        } finally {
            IOs.close(inputStream);
        }

        ConnectionFactory connectionFactory = new ConnectionFactory(connectionConfiguration);
        Connection connection = connectionFactory.getConnection();

        DatabaseMetaData dbMetaData = connection.getMetaData();
        // showCatalogs(dbMetaData);
        // showSchemas(dbMetaData);
        showTables(dbMetaData);
    }

    /**
     * catalog.schema.table
     *
     * @param dbMetaData
     * @throws SQLException
     */
    private void showCatalogs(DatabaseMetaData dbMetaData) throws SQLException {
        ResultSet catalogRs = dbMetaData.getCatalogs();
        List<String> catalogs = Collects.emptyArrayList();
        while (catalogRs.next()) {
            String catalog = catalogRs.getString("TABLE_CAT");
            catalogs.add(catalog);
        }

        String catalogSeparator = dbMetaData.getCatalogSeparator();
        String catalogTerm = dbMetaData.getCatalogTerm();
        boolean catalogAtStart = dbMetaData.isCatalogAtStart();
        int maxCatalogLength = dbMetaData.getMaxCatalogNameLength();

        System.out.println("maxCatalogNameLength: " + maxCatalogLength);
        System.out.println("catalogSeparator: " + catalogSeparator); // .
        System.out.println("catalogTerm: " + catalogTerm); //catalog
        System.out.println("catalogs: " + catalogs.toString());
        System.out.println("catalog at start ? :" + catalogAtStart);
    }

    private void showSchemas(DatabaseMetaData dbMetaData) throws SQLException {

        ResultSet schemasRs = dbMetaData.getSchemas();
        while (schemasRs.next()) {
            String schema = schemasRs.getString("TABLE_SCHEM");
            String catalog = schemasRs.getString("TABLE_CATALOG");
            System.out.println(catalog + " " + schema);
        }

        String schemaTerm = dbMetaData.getSchemaTerm();
        System.out.println("schema term: " + schemaTerm);

        int maxSchemaNameLength = dbMetaData.getMaxSchemaNameLength();
        System.out.println("maxSchemaNameLength: " + maxSchemaNameLength);
    }

    private void showTables(DatabaseMetaData dbMetaData) throws SQLException {
        int maxTableNameLength = dbMetaData.getMaxTableNameLength();
        System.out.println("maxTableNameLength: " + maxTableNameLength);

        String[] tableTypes = new String[]{
                TableType.GLOBAL_TEMPORARY.getCode(),
                TableType.LOCAL_TEMPORARY.getCode(),
                TableType.TABLE.getCode()
        };

        ResultSet tablesRs = dbMetaData.getTables("TEST", "PUBLIC", null, tableTypes);
        List<Table> tables = new RowMapperResultSetExtractor<Table>(new BeanRowMapper<Table>(Table.class)).extract(tablesRs);

        for (Table table : tables) {
            System.out.println("Columns:");
            findColumns(dbMetaData, table);

            System.out.println("Indexes:");
            findTableIndexes(dbMetaData, table);

            findTablePKs(dbMetaData, table);

            findTableFKs(dbMetaData, table);

            System.out.println(table);

            System.out.println(new CommonTableGenerator(dbMetaData).generate(table));
        }
    }

    private void findTablePKs(DatabaseMetaData dbMetaData, Table table) throws SQLException {
        ResultSet pkRs = dbMetaData.getPrimaryKeys(table.getCatalog(), table.getSchema(), table.getName());
        List<PrimaryKeyColumn> pkColumns = new RowMapperResultSetExtractor<PrimaryKeyColumn>(new BeanRowMapper<PrimaryKeyColumn>(PrimaryKeyColumn.class)).extract(pkRs);
        for (PrimaryKeyColumn pk : pkColumns) {
            table.addPKColumn(pk);
        }
    }

    private void findTableFKs(DatabaseMetaData dbMetaData, Table table) throws SQLException {
        ResultSet fkRs = dbMetaData.getImportedKeys(table.getCatalog(), table.getSchema(), table.getName());
        List<ImportedColumn> fkColumns = new RowMapperResultSetExtractor<ImportedColumn>(new BeanRowMapper<ImportedColumn>(ImportedColumn.class)).extract(fkRs);
        for (ImportedColumn fk : fkColumns) {
            table.addFKColumn(fk);
        }
    }

    private void findTableIndexes(DatabaseMetaData dbMetaData, Table table) throws SQLException {
        ResultSet indexesRs = dbMetaData.getIndexInfo(table.getCatalog(), table.getSchema(), table.getName(), false, false);

        List<IndexColumn> indexes = new RowMapperResultSetExtractor<IndexColumn>(new BeanRowMapper<IndexColumn>(IndexColumn.class)).extract(indexesRs);
        Collects.forEach(indexes, new Consumer<IndexColumn>() {
            @Override
            public void accept(IndexColumn indexColumn) {
                System.out.println(indexColumn);

                String indexName = indexColumn.getIndexName();
                Index index = table.getIndex(indexName);
                if (index == null) {
                    index = new Index(table.getCatalog(), table.getSchema(), table.getName(), indexName);
                    table.addIndex(index);
                }

                index.addColumn(indexColumn);
            }
        });
    }

    private void findColumns(DatabaseMetaData dbMetaData, Table table) throws SQLException {
        ResultSet columnsRs = dbMetaData.getColumns(table.getCatalog(), table.getSchema(), table.getName(), null);
        List<Column> columns = new RowMapperResultSetExtractor<Column>(new BeanRowMapper<Column>(Column.class)).extract(columnsRs);
        Collects.forEach(columns, new Consumer<Column>() {
            @Override
            public void accept(Column column) {
                System.out.println(column);

                table.addColumn(column);

            }
        });
    }


}