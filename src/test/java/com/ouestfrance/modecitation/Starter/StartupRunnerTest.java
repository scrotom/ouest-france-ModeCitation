package com.ouestfrance.modecitation.Starter;

import com.ouestfrance.modecitation.Exception.CustomAppException;
import com.ouestfrance.modecitation.Treatment.ModeCitationTreatment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.mockito.Mockito.*;

public class StartupRunnerTest {

    private StartupRunner startupRunner;
    private ModeCitationTreatment modeCitationTreatment;

    @BeforeEach
    public void setUp() throws Exception {
        modeCitationTreatment = mock(ModeCitationTreatment.class);
        startupRunner = new StartupRunner();

        injectPrivateField(startupRunner, modeCitationTreatment);
    }

    private void injectPrivateField(Object target, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField("modeCitationService");
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    public void testRun_Success() throws Exception {
        startupRunner.run();
        verify(modeCitationTreatment, times(1)).applyQuoteMode();
    }

    @Test
    public void testRun_Exception() throws Exception {
        doThrow(CustomAppException.class).when(modeCitationTreatment).applyQuoteMode();
        startupRunner.run();
        verify(modeCitationTreatment, times(1)).applyQuoteMode();
    }
}
