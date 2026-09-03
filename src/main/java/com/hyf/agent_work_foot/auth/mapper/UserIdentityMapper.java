package com.hyf.agent_work_foot.auth.mapper;

import org.apache.ibatis.annotations.Param;

/** 外部身份绑定的数据访问接口；目前承载微信小程序 openid，后续可扩展其他登录提供方。 */
public interface UserIdentityMapper {
    /** 作用：按登录提供方和外部唯一标识锁定身份绑定。输入：provider、subject。输出：绑定行或空。逻辑：供登录和绑定流程串行判断。 */
    IdentityRow selectForUpdate(@Param("provider") String provider, @Param("providerSubject") String providerSubject);

    /** 作用：写入一条已验证的外部身份绑定。输入：身份字段。输出：无。逻辑：唯一索引防止一个微信身份绑定多个账号。 */
    void insert(@Param("identity") IdentityRow identity);

    /** 外部身份绑定持久化行。 */
    record IdentityRow(String id, String userId, String provider, String providerSubject, String unionId) {
    }
}
