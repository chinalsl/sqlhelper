package com.jn.sqlhelper.dialect.sqlparser.jsqlparser;

import com.jn.langx.util.Emptys;
import com.jn.sqlhelper.dialect.instrument.ClauseInstrumentor;
import com.jn.sqlhelper.dialect.instrument.InstrumentConfig;
import com.jn.sqlhelper.dialect.instrument.WhereInstrumentConfig;
import com.jn.sqlhelper.dialect.sqlparser.SqlStatementWrapper;
import net.sf.jsqlparser.statement.Statement;

import java.util.List;

public class WhereClauseJSqlParserInstrumentor implements ClauseInstrumentor<Statement> {
    @Override
    public void instrument(SqlStatementWrapper<Statement> statementWrapper, InstrumentConfig config) {
        if(Emptys.isEmpty(statementWrapper) || Emptys.isEmpty(config)){
            return;
        }
        Statement statement = statementWrapper.get();
        List<WhereInstrumentConfig> expressionConfigs = config.getWhereInstrumentConfigs();
        if(Emptys.isEmpty(statement) || Emptys.isEmpty(expressionConfigs)){
            return;
        }
        if(!Jsqlparsers.isDML(statement)){
            return;
        }



    }
}
