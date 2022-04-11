package com.example.android.bluetoothlegatt.predict;//package hrv.calc.psd;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.util.Arrays;
import java.util.stream.DoubleStream;

import hrv.RRData;
import hrv.calc.parameter.AvgSampleSizeCalculator;
import hrv.calc.psd.PowerSpectralDensityEstimator;
import hrv.calc.psd.PowerSpectrum;

public class WelchPowerSpectralDensityEstimator implements PowerSpectralDensityEstimator {
    public WelchPowerSpectralDensityEstimator() {
    }

    @Override
    public PowerSpectrum calculateEstimate(RRData rr) {
        double[] power = this.calculatePower(rr);
        double[] frequencies = this.calculateFrequencies(rr);
        return new PowerSpectrum(power, frequencies);
    }

    public double sumOfDoubleArray(double[] doubles_array) {
        double d_sum = 0.0;

        for (int i=0; i<doubles_array.length; i++) {
            d_sum += doubles_array[i];
        }

        return d_sum;
    }

    private double[] calculatePower(RRData rr) {
        double[] rrY = rr.getValueAxis();

        for (int i=0; i< rrY.length; i++){
            rrY[i] = rrY[i] * 1000;
        }

        AvgSampleSizeCalculator calc = new AvgSampleSizeCalculator();
        double avgSampleSize = calc.process(rr).getValue();

        // ------- Welch estimate parameters -----------
        int fs = 4;
        int segment_size = 256;
        float overlap_fac = 0.5F;
        int overlap_size = (int)(overlap_fac*segment_size);
        int fft_size = 4096;
        boolean detrend = true; // If true, removes signal mean;
        boolean scale_by_freq = true;
        // PSD size = N/2 + 1
        int PSD_size = (fft_size/2)+1;

        // Number of segments
        int baseSegment_number = (rrY.length/segment_size); // Number of initial segments
        //int total_segments =  (baseSegment_number + (1/(1-overlap_fac) - 1 ) * (baseSegment_number - 1)); // No. segments including overlap
        int total_segments =  (baseSegment_number + (baseSegment_number - 1)); // No. segments including overlap, 针对overlap为0.5的情况的简化

        // Hann window, 参考：
        // https://github.com/amaurycrickx/recognito/blob/master/recognito/src/main/java/com/bitsinharmony/recognito/algorithms/windowing/HammingWindowFunction.java
        double[] window = getHammingWindow(segment_size);

        // calculate
        double powerDensity_normalization;
        if(scale_by_freq) {
            // Scale the spectrum by the norm of the window to compensate for
            // windowing loss; see Bendat & Piersol Sec 11.5.2.
            double[] window_square = getArraySquare(window);
            double S2 = DoubleStream.of(window_square).sum();
//            double S2 = sumOfDoubleArray(window_square);
            // Normalization including window effect on power
            powerDensity_normalization = 1/S2;
        }
        else{
            // In this case, preserve power in the segment, not amplitude
            double S2 = Math.pow(DoubleStream.of(window).sum(), 2);
//            double S2 = Math.pow(sumOfDoubleArray(window), 2);

            // Normalization including window effect on power
            powerDensity_normalization = 1/S2;
        }

        // calculate fft of segments
        Complex[][] fft_segment = new Complex[total_segments][fft_size];
        FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);

        for(int i: new Range(total_segments)){
            int offset_segment = (int)(i* (1-overlap_fac)*segment_size);
            double[] current_segment = Arrays.copyOfRange(rrY, offset_segment, offset_segment+segment_size);
            // Detrend (Remove mean value)
            if(detrend) {
                double current_segment_mean = DoubleStream.of(current_segment).sum() / (double) current_segment.length;
//                double current_segment_mean = sumOfDoubleArray(current_segment) / (double) current_segment.length;

                for(int j: new Range(current_segment.length)){
                    current_segment[j] = current_segment[j] - current_segment_mean;
                }
            }
            double[] windowed_segment = getElementProduct(current_segment,window);

            double[] windowed_segment_pad_zero = getPaddedSegment(windowed_segment, fft_size);
//            fft_segment[i] = np.fft.fft(windowed_segment,fft_size); // fft automatically pads if n<nfft
            fft_segment[i] = fft.transform(windowed_segment_pad_zero, TransformType.FORWARD);
        }

//        System.out.println(fft_segment[0][0]);
//        System.out.println(fft_segment[0][1]);


        // Add FFTs of different segments
        Complex[] fft_sum = new Complex[fft_size];

        for(int i: new Range(total_segments)){
            for(int j: new Range(fft_size)){
                if(i == 0){
                    fft_sum[j] = fft_segment[i][j];
                }else {
                    fft_sum[j] = fft_sum[j].add(fft_segment[i][j]);
                }
            }
        }

        // Averaging decreases FFT variance
        double powerDensity_averaging = 1/total_segments;
        // Transformation from Hz.s to Hz spectrum
        double powerDensity_transformation;
        if(scale_by_freq) {
            powerDensity_transformation = 1 / (double)fs;
        }
        else {
            powerDensity_transformation = 1;
        }

        // Make oneSided estimate 1st -> N+1st element
        Complex[] fft_WelchEstimate_oneSided = Arrays.copyOfRange(fft_sum, 0, PSD_size);
        // Convert FFT values to power density in U**2/Hz
        // fft_WelchEstimate_oneSided_abs =
        double[] PSD_own = new double[fft_WelchEstimate_oneSided.length];

        for(int i = 0; i < fft_WelchEstimate_oneSided.length; ++i) {
            double abs = fft_WelchEstimate_oneSided[i].abs();
//            System.out.println(abs);
            PSD_own[i] = abs * abs * powerDensity_averaging * powerDensity_normalization * powerDensity_transformation;
//            System.out.println(powerDensity_averaging);
//            System.out.println(powerDensity_normalization);
//            System.out.println(powerDensity_transformation);
//            System.out.println(PSD_own[i]);
//            System.out.println("------------");

        }

        // Double frequencies except DC and Nyquist
        for(int i = 2; i < fft_WelchEstimate_oneSided.length-1; ++i) {
            PSD_own[i] = PSD_own[i] * 2;
        }

        return PSD_own;

    }

    private double[] calculateFrequencies(RRData rr) {

        // ------- Welch estimate parameters -----------
        int fs = 4;
//        int segment_size = 256;
        int fft_size = 4096;
        int PSD_size = (fft_size/2)+1;

        double[] frequencies = new double[fft_size];

        for(int i: new Range(fft_size)){
            frequencies[i] = ((double)fs) * ((double)i) / ((double)fft_size);
        }

        double[] frequencies_slice = Arrays.copyOfRange(frequencies, 0, PSD_size);

        return frequencies_slice;
    }

    private double[] getHammingWindow(int windowSize) {
        // precompute factors for given window, avoid re-calculating for several instances
        double TWO_PI = 2 * Math.PI;
        double[] factors = new double[windowSize];
        int sizeMinusOne = windowSize - 1;

        for(int i = 0; i < windowSize; i++) {
            factors[i] = 0.54d - (0.46d * Math.cos((TWO_PI * i) / sizeMinusOne));
        }

        return factors;
    }

    private double[] getArraySquare(double[] input_array) {
        double[] array_square = new double[input_array.length];

        for (int i = 0; i < input_array.length; i++) {
            array_square[i] = Math.pow(input_array[i], 2);
        }

        return array_square;
    }

    private double[] getElementProduct(double[] input_a, double[] input_b){
        double[] output_c = new double[input_a.length];

        for(int i: new Range(input_a.length)){
            output_c[i] = input_a[i] * input_b[i];
        }

        return output_c;
    }

    private double[] getPaddedSegment(double[] windowed_segment, int fft_size) {
        double[] windowed_segment_padded = new double[fft_size];

        for(int i: new Range(windowed_segment.length)){
            windowed_segment_padded[i] = windowed_segment[i];
        }

        for(int i=windowed_segment.length; i<fft_size; i++){
            windowed_segment_padded[i] = 0.0;
        }

        return windowed_segment_padded;
    }
}
