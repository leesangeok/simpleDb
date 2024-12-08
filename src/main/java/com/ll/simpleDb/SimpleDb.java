package com.ll.simpleDb;

import com.ll.simpleDb.standard.util.Ut;
import lombok.RequiredArgsConstructor;

import javax.sound.midi.SysexMessage;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

@RequiredArgsConstructor
public class SimpleDb {
    private final String host;
    private final String username;
    private final String password;
    private final String dbName;
    private Connection connection;

    // 현재는 개발 환경에서 항상 true로 설정
    private boolean isNotProdMode() {
        return true; // 배포 환경에서는 false로 설정
    }

    // 데이터베이스 연결 초기화
    private void connect() {
        if (connection != null) {
            return; // 이미 연결되어 있으면 아무 작업도 하지 않음
        }

        String url = String.format("jdbc:mysql://%s:3307/%s?useSSL=false&allowPublicKeyRetrieval=true", host, dbName);
        try {
            connection = DriverManager.getConnection(url, username, password);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to database: " + e.getMessage(), e);
        }
    }

    // 연결 해제
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to close database connection: " + e.getMessage(), e);
            }
        }
    }

    public Sql genSql() {
        return new Sql(this);
    }

    // SQL과 파라미터를 입력받아 치환된 SQL 반환
    private String rawSql(String sql, Object[] params) {
        StringBuilder processedSql = new StringBuilder(sql);
        int index = 0;

        for (Object param : params) {
            index = processedSql.indexOf("?", index);
            if (index == -1) break;

            String replacement = formatRawSqlParam(param);
            processedSql.replace(index, index + 1, replacement);
            index += replacement.length();
        }

        return processedSql.toString();
    }

    // 파라미터를 적절한 SQL 값으로 변환
    private String formatRawSqlParam(Object param) {
        if (param == null) return "NULL";
        if (param instanceof Boolean) return param.toString().toUpperCase();
        if (param instanceof Number) return param.toString();
        if (param instanceof String || param instanceof LocalDateTime) {
            return "'" + param.toString().replace("'", "''") + "'";
        }
        return "'" + param.toString() + "'";
    }

    // PreparedStatement 파라미터 바인딩
    private void bindParameters(PreparedStatement preparedStatement, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            preparedStatement.setObject(i + 1, params[i]);
        }
    }

    // ResultSet을 Map으로 변환
    private Map<String, Object> parseResultSetToMap(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        Map<String, Object> row = new LinkedHashMap<>();

        for (int i = 1; i <= columnCount; i++) {
            String columnName = metaData.getColumnLabel(i);
            Object value = switch (metaData.getColumnType(i)) {
                case Types.BIGINT -> resultSet.getLong(columnName);
                case Types.TIMESTAMP -> {
                    Timestamp timestamp = resultSet.getTimestamp(columnName);
                    yield (timestamp != null) ? timestamp.toLocalDateTime() : null;
                }
                case Types.BOOLEAN -> resultSet.getBoolean(columnName);
                default -> resultSet.getObject(columnName);
            };
            row.put(columnName, value);
        }

        return row;
    }

    // ResultSet 파싱 (다양한 클래스 타입 지원)
    private <T> T parseResultSet(ResultSet resultSet, Class<T> cls) throws SQLException {
        if (!resultSet.next()) throw new NoSuchElementException("No data found");

        return switch (cls.getSimpleName()) {
            case "String" -> cls.cast(resultSet.getString(1));
            case "List" -> {
                List<Map<String, Object>> rows = new ArrayList<>();
                do {
                    rows.add(parseResultSetToMap(resultSet));
                } while (resultSet.next());
                yield cls.cast(rows);
            }
            case "Map" -> cls.cast(parseResultSetToMap(resultSet));
            case "LocalDateTime" -> cls.cast(resultSet.getTimestamp(1).toLocalDateTime());
            case "Long" -> cls.cast(resultSet.getLong(1));
            case "Boolean" -> cls.cast(resultSet.getBoolean(1));
            default -> throw new IllegalArgumentException("Unsupported class type: " + cls.getSimpleName());
        };
    }

    // 내부 SQL 실행 메서드
    private <T> T _run(String sql, Class<T> cls, Object... params) {
        connect();
        sql = sql.trim();

        if (isNotProdMode()) {
            System.out.println("== rawSql ==");
            System.out.println(rawSql(sql, params));
        }

        try (PreparedStatement preparedStatement = sql.startsWith("INSERT")
                ? connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)
                : connection.prepareStatement(sql)) {

            bindParameters(preparedStatement, params);

            if (sql.startsWith("SELECT")) {
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    return parseResultSet(resultSet, cls);
                }
            } else if (sql.startsWith("INSERT")) {
                preparedStatement.executeUpdate();
                ResultSet generatedKeys = preparedStatement.getGeneratedKeys();
                if (cls == Long.class && generatedKeys.next()) {
                    return cls.cast(generatedKeys.getLong(1));
                }
                return cls.cast(preparedStatement.getUpdateCount());
            } else {
                return cls.cast(preparedStatement.executeUpdate());
            }

        } catch (SQLException e) {
            throw new RuntimeException("SQL Execution failed: " + e.getMessage(), e);
        }
    }

    // Public 메서드 (명칭 변경 금지)
    public int run(String sql, Object... params) {
        return _run(sql, Integer.class, params);
    }

    public boolean selectBoolean(String sql, Object... params) {
        return _run(sql, Boolean.class, params);
    }

    public String selectString(String sql, Object... params) {
        return _run(sql, String.class, params);
    }

    public long selectLong(String sql, Object... params) {
        return _run(sql, Long.class, params);
    }

    public LocalDateTime selectDatetime(String sql, Object... params) {
        return _run(sql, LocalDateTime.class, params);
    }

    public Map<String, Object> selectRow(String sql, Object... params) {
        return _run(sql, Map.class, params);
    }

    public List<Map<String, Object>> selectRows(String sql, Object... params) {
        return _run(sql, List.class, params);
    }

    public <T> List<T> selectRows(String sql, Class<?> cls, Object... params) {
        return selectRows(sql,params)
                .stream()
                .map(row -> (T) Ut.mapper.mapToObj(row,cls))
                .toList();
    }

    public <T>  T selectRow(String sql, Class<?> cls, Object... params) {
        Map<String, Object> row = selectRow(sql,params);

        return  (T) Ut.mapper.mapToObj(row,cls);
    }

    public int delete(String sql, Object... params) {
        return _run(sql, Integer.class, params);
    }

    public int update(String sql, Object... params) {
        return _run(sql, Integer.class, params);
    }

    public long insert(String sql, Object... params) {
        return _run(sql, Long.class, params);
    }

    public List<Long> selectLongs(String sql, Object[] array) {
        return selectRows(sql, array)
                .stream()
                .map(row -> (Long) row.values().iterator().next())
                .toList();
    }
}
