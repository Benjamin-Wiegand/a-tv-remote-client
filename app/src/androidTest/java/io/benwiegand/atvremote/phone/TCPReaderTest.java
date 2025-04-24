package io.benwiegand.atvremote.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.benwiegand.atvremote.phone.network.TCPReader;

@RunWith(AndroidJUnit4.class)
public class TCPReaderTest {

    @Test
    public void LF_and_CRLF_Compatibility_Test() throws IOException, InterruptedException {

            final String[] TEST_LINES = new String[]{"testing 123",
                    "TCPReader should support", "both LF", "and CRLF", "forms of newline",
                    "and it shouldn't remove random '\r's in the middle of strings",
                    "in case for some reason that happens"};

            testWithString(String.join("\n", TEST_LINES) + "\n", TEST_LINES);
            testWithString(String.join("\r\n", TEST_LINES) + "\r\n", TEST_LINES);
    }

    public void testWithString(String string, String[] expectedLines) throws IOException, InterruptedException {
        ByteArrayInputStream is = new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
        TCPReader reader = TCPReader.createFromStream(is, StandardCharsets.UTF_8);


        for (String expectedLine : expectedLines) {
            String line = reader.nextLine(100);
            assertEquals(expectedLine, line);
        }

        assertThrows("exception when reading after end of stream",
                IOException.class, () -> reader.nextLine(100));

    }

}
