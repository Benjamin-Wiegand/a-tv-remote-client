package io.benwiegand.atvremote.phone.control;

public interface MouseCurve {

    MouseCurve ACCEL_LINEAR = d -> d;
    MouseCurve ACCEL_LOG10 = d -> (float) (d * Math.log10(Math.abs(d) + 1));
    MouseCurve ACCEL_LN = d -> (float) (d * Math.log(Math.abs(d) + 1));

    // given delta, calculate how many mouse to a mickey
    // I wish I was joking, this is the mickey/mouse equation
    float apply(float delta);

}
