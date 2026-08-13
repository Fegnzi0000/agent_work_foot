package com.hyf.agent_work_foot.food.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** MySQL JSON字符串数组与List之间的通用MyBatis类型处理器。 */
public class JsonStringListTypeHandler extends BaseTypeHandler<List<String>> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 作用：写入JSON数组。输入：非空字符串列表。输出：预编译参数。逻辑：序列化失败转为SQLException。 */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
        try {
            ps.setString(i, MAPPER.writeValueAsString(parameter));
        } catch (Exception exception) {
            throw new SQLException("无法序列化JSON字符串数组", exception);
        }
    }

    /** 作用：按列名读取 JSON 数组。输入：结果集和列名。输出：字符串列表。逻辑：交由统一解析方法处理空值与异常。 */
    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return read(rs.getString(columnName));
    }

    /** 作用：按列序号读取 JSON 数组。输入：结果集和列序号。输出：字符串列表。逻辑：交由统一解析方法处理。 */
    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return read(rs.getString(columnIndex));
    }

    /** 作用：从存储过程结果读取 JSON 数组。输入：CallableStatement 和列序号。输出：字符串列表。逻辑：复用统一解析。 */
    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return read(cs.getString(columnIndex));
    }

    /** 作用：解析JSON数组。输入：数据库JSON文本。输出：字符串列表。逻辑：空值返回空列表，非法值抛SQL异常。 */
    private List<String> read(String json) throws SQLException {
        if (json == null) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception exception) {
            throw new SQLException("无法解析JSON字符串数组", exception);
        }
    }
}
