/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the LGPL, Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at  http://www.gnu.org/licenses/lgpl-3.0.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.fangjinuo.sqlhelper.dialect.internal.limit;

import com.github.fangjinuo.sqlhelper.dialect.RowSelection;
import com.github.fangjinuo.sqlhelper.dialect.StringHelper;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SQLServer2005LimitHandler
        extends AbstractLimitHandler {
    private static final String SELECT = "select";
    private static final String FROM = "from";
    private static final String DISTINCT = "distinct";
    private static final String ORDER_BY = "order by";
    private static final String SELECT_DISTINCT = "select distinct";
    private static final String SELECT_DISTINCT_SPACE = "select distinct ";
    final String SELECT_SPACE = "select ";

    private static final Pattern SELECT_DISTINCT_PATTERN = buildShallowIndexPattern("select distinct ", true);
    private static final Pattern SELECT_PATTERN = buildShallowIndexPattern("select(.*)", true);
    private static final Pattern FROM_PATTERN = buildShallowIndexPattern("from", true);
    private static final Pattern DISTINCT_PATTERN = buildShallowIndexPattern("distinct", true);
    private static final Pattern ORDER_BY_PATTERN = buildShallowIndexPattern("order by", true);
    private static final Pattern COMMA_PATTERN = buildShallowIndexPattern(",", false);

    private static final Pattern ALIAS_PATTERN = Pattern.compile("(?![^\\[]*(\\]))\\S+\\s*(\\s(?i)as\\s)\\s*(\\S+)*\\s*$|(?![^\\[]*(\\]))\\s+(\\S+)$");


    private boolean topAdded;

    @Override
    public int convertToFirstRowValue(int zeroBasedFirstResult) {
        return zeroBasedFirstResult + 1;
    }

    @Override
    public String processSql(String sql, RowSelection selection) {
        StringBuilder sb = new StringBuilder(sql);
        if (sb.charAt(sb.length() - 1) == ';') {
            sb.setLength(sb.length() - 1);
        }

        if (LimitHelper.hasFirstRow(selection)) {
            String selectClause = fillAliasInSelectClause(sb);

            int orderByIndex = shallowIndexOfPattern(sb, ORDER_BY_PATTERN, 0);
            if (orderByIndex > 0) {
                addTopExpression(sb);
            }

            encloseWithOuterQuery(sb);


            sb.insert(0, "WITH query AS (").append(") SELECT ").append(selectClause).append(" FROM query ");
            sb.append("WHERE __hibernate_row_nr__ >= ? AND __hibernate_row_nr__ < ?");
        } else {
            addTopExpression(sb);
        }

        return sb.toString();
    }
    @Override
    public int bindLimitParametersAtStartOfQuery(RowSelection selection, PreparedStatement statement, int index) throws SQLException {
        if (this.topAdded) {
            statement.setInt(index, getMaxOrLimit(selection) - 1);
            return 1;
        }
        return 0;
    }

    @Override
    public int bindLimitParametersAtEndOfQuery(RowSelection selection, PreparedStatement statement, int index) throws SQLException {
        return LimitHelper.hasFirstRow(selection) ? super.bindLimitParametersAtEndOfQuery(selection, statement, index) : 0;
    }


    protected String fillAliasInSelectClause(StringBuilder sb) {
        String separator = System.getProperty("line.separator");
        List<String> aliases = new LinkedList();
        int startPos = getSelectColumnsStartPosition(sb);
        int endPos = shallowIndexOfPattern(sb, FROM_PATTERN, startPos);

        int nextComa = startPos;
        int prevComa = startPos;
        int unique = 0;
        boolean selectsMultipleColumns = false;

        while (nextComa != -1) {
            prevComa = nextComa;
            nextComa = shallowIndexOfPattern(sb, COMMA_PATTERN, nextComa);
            if (nextComa > endPos) {
                break;
            }
            if (nextComa != -1) {
                String expression = sb.substring(prevComa, nextComa);
                if (selectsMultipleColumns(expression)) {
                    selectsMultipleColumns = true;
                } else {
                    String alias = getAlias(expression);
                    if (alias == null) {
                        alias = StringHelper.generateAlias("page", unique);
                        sb.insert(nextComa, " as " + alias);
                        int aliasExprLength = (" as " + alias).length();
                        unique++;
                        nextComa += aliasExprLength;
                        endPos += aliasExprLength;
                    }
                    aliases.add(alias);
                }
                nextComa++;
            }
        }


        endPos = shallowIndexOfPattern(sb, FROM_PATTERN, startPos);
        String expression = sb.substring(prevComa, endPos);
        if (selectsMultipleColumns(expression)) {
            selectsMultipleColumns = true;
        } else {
            String alias = getAlias(expression);
            if (alias == null) {
                alias = StringHelper.generateAlias("page", unique);
                boolean endWithSeparator = sb.substring(endPos - separator.length()).startsWith(separator);
                sb.insert(endPos - (endWithSeparator ? 2 : 1), " as " + alias);
            }
            aliases.add(alias);
        }


        return selectsMultipleColumns ? "*" : StringHelper.join(", ", aliases.iterator());
    }


    private int getSelectColumnsStartPosition(StringBuilder sb) {
        int startPos = getSelectStartPosition(sb);

        String sql = sb.toString().substring(startPos).toLowerCase();
        if (sql.startsWith("select distinct ")) {
            return startPos + "select distinct ".length();
        }
        if (sql.startsWith("select ")) {
            return startPos + "select ".length();
        }
        return startPos;
    }


    private int getSelectStartPosition(StringBuilder sb) {
        return shallowIndexOfPattern(sb, SELECT_PATTERN, 0);
    }


    private boolean selectsMultipleColumns(String expression) {
        String lastExpr = expression.trim().replaceFirst("(?i)(.)*\\s", "").trim();
        return ("*".equals(lastExpr)) || (lastExpr.endsWith(".*"));
    }


    private String getAlias(String expression) {
        expression = expression.replaceFirst("(\\((.)*\\))", "").trim();


        Matcher matcher = ALIAS_PATTERN.matcher(expression);

        String alias = null;
        if ((matcher.find()) && (matcher.groupCount() > 1)) {
            alias = matcher.group(3);
            if (alias == null) {
                alias = matcher.group(0);
            }
        }

        return alias != null ? alias.trim() : null;
    }


    protected void encloseWithOuterQuery(StringBuilder sql) {
        sql.insert(0, "SELECT inner_query.*, ROW_NUMBER() OVER (ORDER BY CURRENT_TIMESTAMP) as __hibernate_row_nr__ FROM ( ");
        sql.append(" ) inner_query ");
    }


    protected void addTopExpression(StringBuilder sql) {
        int selectPos = shallowIndexOfPattern(sql, SELECT_PATTERN, 0);
        int selectDistinctPos = shallowIndexOfPattern(sql, SELECT_DISTINCT_PATTERN, 0);
        if (selectPos == selectDistinctPos) {
            sql.insert(selectDistinctPos + "select distinct".length(), " TOP(?)");
        } else {
            sql.insert(selectPos + "select".length(), " TOP(?)");
        }
        this.topAdded = true;
    }


    private static int shallowIndexOfPattern(StringBuilder sb, Pattern pattern, int fromIndex) {
        int index = -1;
        String matchString = sb.toString();


        if ((matchString.length() < fromIndex) || (fromIndex < 0)) {
            return -1;
        }

        List<IgnoreRange> ignoreRangeList = generateIgnoreRanges(matchString);

        Matcher matcher = pattern.matcher(matchString);
        matcher.region(fromIndex, matchString.length());

        if (ignoreRangeList.isEmpty()) {

            if ((matcher.find()) && (matcher.groupCount() > 0)) {
                index = matcher.start();
            }

        } else {
            while ((matcher.find()) && (matcher.groupCount() > 0)) {
                int position = matcher.start();
                if (!isPositionIgnorable(ignoreRangeList, position)) {
                    index = position;
                    break;
                }
            }
        }
        return index;
    }


    private static Pattern buildShallowIndexPattern(String pattern, boolean wordBoundardy) {
        return Pattern.compile("(" + (wordBoundardy ? "\\b" : "") + pattern + ")(?![^\\(|\\[]*(\\)|\\]))", 2);
    }


    private static List<IgnoreRange> generateIgnoreRanges(String sql) {
        List<IgnoreRange> ignoreRangeList = new ArrayList();

        int depth = 0;
        int start = -1;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '(') {
                depth++;
                if (depth == 1) {
                    start = i;
                }
            } else if (ch == ')') {
                if (depth > 0) {
                    if (depth == 1) {
                        ignoreRangeList.add(new IgnoreRange(start, i));
                        start = -1;
                    }
                    depth--;
                } else {
                    throw new IllegalStateException("Found an unmatched ')' at position " + i + ": " + sql);
                }
            }
        }

        if (depth != 0) {
            throw new IllegalStateException("Unmatched parenthesis in rendered SQL (" + depth + " depth): " + sql);
        }

        return ignoreRangeList;
    }


    private static boolean isPositionIgnorable(List<IgnoreRange> ignoreRangeList, int position) {
        for (IgnoreRange ignoreRange : ignoreRangeList) {
            if (ignoreRange.isWithinRange(position)) {
                return true;
            }
        }
        return false;
    }

    static class IgnoreRange {
        private int start;
        private int end;

        IgnoreRange(int start, int end) {
            this.start = start;
            this.end = end;
        }

        boolean isWithinRange(int position) {
            return (position >= this.start) && (position <= this.end);
        }
    }
}