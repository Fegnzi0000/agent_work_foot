package com.hyf.agent_work_foot.auth;
import com.hyf.agent_work_foot.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsentServiceTests {
    @Test void rejectsMissingRefusedStaleAndUnderAgeConsent() {
        assertThrows(ApiException.class, () -> ConsentService.validate(new AuthRequests.WeChatMiniProgramLoginRequest("code")));
        assertThrows(ApiException.class, () -> ConsentService.validate(new AuthRequests.WeChatMiniProgramLoginRequest("code", false, ConsentService.VERSION, ConsentService.VERSION, "ADULT")));
        assertThrows(ApiException.class, () -> ConsentService.validate(new AuthRequests.WeChatMiniProgramLoginRequest("code", true, "old", ConsentService.VERSION, "ADULT")));
        assertThrows(ApiException.class, () -> ConsentService.validate(new AuthRequests.WeChatMiniProgramLoginRequest("code", true, ConsentService.VERSION, ConsentService.VERSION, "UNDER_14")));
        assertDoesNotThrow(() -> ConsentService.validate(new AuthRequests.WeChatMiniProgramLoginRequest("code", true, ConsentService.VERSION, ConsentService.VERSION, "ADULT")));
    }
    @Test void missingConsentCannotSaveMedicalDataOrEnableIt() {
        var jdbc = mock(JdbcTemplate.class);
        var consent = new ConsentService(jdbc);
        assertThrows(ApiException.class, () -> consent.requireMedical("user"));
        assertThrows(ApiException.class, () -> consent.medical("user", true, ConsentService.VERSION));
    }
}
