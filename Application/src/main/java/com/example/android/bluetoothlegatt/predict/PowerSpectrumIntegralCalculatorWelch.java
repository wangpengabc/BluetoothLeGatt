package com.example.android.bluetoothlegatt.predict;

import org.apache.commons.math3.analysis.integration.TrapezoidIntegrator;

import hrv.calc.parameter.HRVParameter;
import hrv.calc.parameter.HRVParameterEnum;
import hrv.calc.parameter.HRVPowerSpectrumProcessor;
import hrv.calc.psd.PowerSpectrum;
import hrv.calc.psd.PowerSpectrumUnivariateFunctionAdapter;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//


public class PowerSpectrumIntegralCalculatorWelch implements HRVPowerSpectrumProcessor {
    private double lowerIntegrationLimit;
    private double upperIntegrationLimit;

    public PowerSpectrumIntegralCalculatorWelch(double lowerIntegrationLimit, double upperIntegrationLimit) {
        this.lowerIntegrationLimit = lowerIntegrationLimit;
        this.upperIntegrationLimit = upperIntegrationLimit;
    }

//    public HRVParameter process(PowerSpectrum ps) {
//        TrapezoidIntegrator integrator = new TrapezoidIntegrator();
//        return new HRVParameter(HRVParameterEnum.NON, getIntergrator(this.lowerIntegrationLimit, this.upperIntegrationLimit, ps), ps.getUnit() + "*" + ps.getUnit());
//    }

//    public HRVParameter process(PowerSpectrum ps) {
//        PolynomialFunction f = new PolynomialFunction(ps.getPower());
//        UnivariateFunction uf = (UnivariateFunction)new PolynomialFunction(vector);
//        TrapezoidIntegrator integrator = new TrapezoidIntegrator();
//        return new HRVParameter(HRVParameterEnum.NON, integrator.integrate(2147483647, ps.getPower()this.lowerIntegrationLimit, this.upperIntegrationLimit,), ps.getUnit() + "*" + ps.getUnit());
//    }

    public HRVParameter process(PowerSpectrum ps) {
        PowerSpectrumUnivariateFunctionAdapter psAdapter = new PowerSpectrumUnivariateFunctionAdapter(ps);
        TrapezoidIntegrator integrator = new TrapezoidIntegrator();
        return new HRVParameter(HRVParameterEnum.NON, integrator.integrate(2147483647, psAdapter, this.lowerIntegrationLimit, this.upperIntegrationLimit), ps.getUnit() + "*" + ps.getUnit());
    }

    private double getIntergrator(double lower, double upper, PowerSpectrum ps) {

        double[] power = ps.getPower();
        double[] frequency = ps.getFrequency();
        double power_sum = 0.0;

        for(int i: new Range(power.length)){
            if((frequency[i]> lower) && (frequency[i]<upper)){
                power_sum += power[i]*(frequency[1]-frequency[0]);
            }
        }
        return power_sum;
    }
}
