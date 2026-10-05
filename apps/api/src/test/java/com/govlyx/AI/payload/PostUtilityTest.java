package com.govlyx.AI.payload;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PostUtilityTest {

    @Test
    public void testTruncateTextHtmlDecoding() {
        String encodedText = "He said &quot;Hello&quot; &amp; &rsquo;Hi&rsquo;";
        
        // Truncate length greater than text length so it doesn't actually truncate
        String result = PostUtility.truncateText(encodedText, 100);
        
        // Expected string should be properly decoded
        String expected = "He said \"Hello\" & ’Hi’";
        assertEquals(expected, result);
    }
    
    @Test
    public void testTruncateTextHtmlDecodingWithTruncation() {
        String encodedText = "He said &quot;Hello&quot; &amp; &rsquo;Hi&rsquo;";
        
        // Truncate length small enough to truncate
        String result = PostUtility.truncateText(encodedText, 10);
        
        // Expected string should be properly decoded and truncated with suffix "..."
        String expected = "He said \"H...";
        assertEquals(expected, result);
    }
}
