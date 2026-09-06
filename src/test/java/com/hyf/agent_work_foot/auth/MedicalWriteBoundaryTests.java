package com.hyf.agent_work_foot.auth;
import com.hyf.agent_work_foot.common.ApiException;
import com.hyf.agent_work_foot.common.MoneyParser;
import com.hyf.agent_work_foot.preference.*;
import com.hyf.agent_work_foot.preference.mapper.PreferenceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class MedicalWriteBoundaryTests {
    @Mock PreferenceMapper mapper;
    @Mock MoneyParser money;
    @Mock ConsentService consent;
    @InjectMocks PreferenceService service;
    @Test void rejectsMedicalWriteBeforeDeletingOrInsertingPreferences() {
        lenient().doThrow(new ApiException(HttpStatus.FORBIDDEN, "MEDICAL_CONSENT_REQUIRED", "required")).when(consent).requireMedical("user");
        var request = new PreferenceRequests.OnboardingRequest(null, false, null, List.of(new PreferenceRequests.PreferenceItem("CUSTOM", "花生", null)), List.of(), List.of(), List.of());
        assertThrows(ApiException.class, () -> service.submitOnboarding("user", request));
        verifyNoInteractions(mapper);
    }
}
