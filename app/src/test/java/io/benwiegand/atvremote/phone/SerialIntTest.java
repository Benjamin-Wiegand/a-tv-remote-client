package io.benwiegand.atvremote.phone;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.benwiegand.atvremote.phone.stuff.SerialInt;

public class SerialIntTest {

    @Test
    public void wrapAround_Test() {
        SerialInt serial = new SerialInt();
        assertEquals("SerialInt must start at 0", serial.get(), 0);

        for (int i = 0; i < Integer.MAX_VALUE; i++) serial.advance();

        assertEquals("MAX_VALUE advance() calls resulting in a MAX_VALUE serial",
                Integer.MAX_VALUE, serial.get());
        assertEquals("wrap around to MIN_VALUE",
                Integer.MIN_VALUE, serial.advance());
        assertEquals("consistency between advance() and get() during wrap",
                Integer.MIN_VALUE, serial.get());
        assertEquals("increment after wrap",
                Integer.MIN_VALUE + 1, serial.advance());
        assertEquals("consistency between advance() and get() after wrap",
                Integer.MIN_VALUE + 1, serial.get());

    }

}
