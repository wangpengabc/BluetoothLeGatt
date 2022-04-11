package com.example.android.bluetoothlegatt.predict;//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import hrv.RRData;
import hrv.calc.manipulator.HRVDataManipulator;

public class HRVLinearInterpolator implements HRVDataManipulator {
    private double samplingRate;

    public HRVLinearInterpolator(double samplingRate) {
        this.samplingRate = samplingRate;
    }

    public RRData manipulate(RRData data) {
        double[] y = data.getValueAxis();
        double[] x = data.getTimeAxis();
        LinearInterpolator interpolator = new LinearInterpolator();
        PolynomialSplineFunction interpolFunction = interpolator.interpolate(x, y);
        double biggestXValue = x[x.length - 1];
        int numInterpolVals = (int)(biggestXValue * this.samplingRate);
        double[] xInterpolated = new double[numInterpolVals];
        double[] yInterpolated = new double[numInterpolVals];
        double stepSize = 1.0D / this.samplingRate;

        for(int i = 0; i < numInterpolVals; ++i) {
            xInterpolated[i] = stepSize * (double)i;
            yInterpolated[i] = interpolFunction.value(xInterpolated[i]);
        }

        return new RRData(xInterpolated, data.getTimeAxisUnit(), yInterpolated, data.getValueAxisUnit());
    }
}
