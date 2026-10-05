package com.govlyx.AI.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DatabaseMigrationRunnerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private DatabaseMigrationRunner migrationRunner;

    @Test
    void testRun_SuccessfulThemeAndEmailMigrations() throws Exception {
        when(jdbcTemplate.update(contains("is_email_verified"))).thenReturn(5);
        when(jdbcTemplate.update(contains("theme = 'light'"))).thenReturn(12);

        migrationRunner.run();

        verify(jdbcTemplate, times(1)).update(contains("is_email_verified"));
        verify(jdbcTemplate, times(1)).update(contains("theme = 'light'"));
    }

    @Test
    void testRun_NoRowsNeedingUpdate() throws Exception {
        when(jdbcTemplate.update(anyString())).thenReturn(0);

        migrationRunner.run();

        verify(jdbcTemplate, times(1)).update(contains("is_email_verified"));
        verify(jdbcTemplate, times(1)).update(contains("theme = 'light'"));
    }

    @Test
    void testRun_HandlesDatabaseExceptionGracefully() throws Exception {
        when(jdbcTemplate.update(anyString())).thenThrow(new RuntimeException("DB offline"));

        // Runner should handle the exception gracefully without throwing out of run()
        migrationRunner.run();

        verify(jdbcTemplate, atLeastOnce()).update(anyString());
    }
}
