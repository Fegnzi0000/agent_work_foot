package com.hyf.agent_work_foot.config;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.session.Configuration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SqlPrivacyTests {
    @Test void diagnosticParametersNeverExposeHealthIdentityOrCredentials() throws Exception {
        var config=new Configuration();
        var values=Map.of("customValue","sensitive-allergy","providerSubject","secret-openid","password","secret-password","nickname","private-name");
        var mappings=values.keySet().stream().map(key->new ParameterMapping.Builder(config,key,String.class).build()).toList();
        var sql=new BoundSql(config,"INSERT INTO preference_items VALUES (?)",mappings,values);
        var method=DevelopmentSqlDiagnosticInterceptor.class.getDeclaredMethod("parameterSummary",BoundSql.class,Object.class);
        method.setAccessible(true);
        String result=(String)method.invoke(new DevelopmentSqlDiagnosticInterceptor(),sql,values);
        for(String value:values.values()) assertFalse(result.contains(value), "Parameter content must never appear in diagnostic logs");
    }
}
