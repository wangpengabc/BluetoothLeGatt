package com.example.android.bluetoothlegatt.predict;

import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
//import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import com.example.android.bluetoothlegatt.DeviceControlActivity;
import com.github.psambit9791.jdsp.signal.*;


import hrv.calc.parameter.HRVParameter;
//import org.apache.commons.math3.util.MathArrays;


public class QRSDetectorOffline{

    // Set ECG device frequency in samples per second here.
    private static int signal_frequency = 125;

//    private double filter_lowcut = 0.0;
//    private double filter_highcut = 15.0;
//    private int filter_order = 1;

    private static int integration_window = 9;  // Change proportionally when adjusting frequency (in samples).

    // self.findpeaks_limit = 0.35
    private static double findpeaks_limit = 0;
    private static int findpeaks_spacing = 30;  // Change proportionally when adjusting frequency (in samples).

    public static int detectQRS(String data_save_dir, String ecg_data_name)  {
        // load data and convert to double type
        ArrayList<String> ecg_data_raw = load_ecg_data(data_save_dir + "/" + ecg_data_name + ".txt");

        int nsamp = ecg_data_raw.size();

        System.out.println("ecg_data_raw.size:");
        System.out.println(ecg_data_raw.size());
        System.out.println(ecg_data_raw);

        double[] ecg = new double[nsamp];
        for(int i=0;i<nsamp;i++){
            ecg[i] = Double.parseDouble(ecg_data_raw.get(i));
        }

        // detect
        double[] ecg_diff = diff1d(ecg, nsamp);
        System.out.println("ecg_diff.size:");
        System.out.println(ecg_diff.length);

        double[] ecg_squared = squared(ecg_diff, nsamp);
        System.out.println("ecg_squared.size:");
        System.out.println(ecg_squared.length);

        double[] ecg_integrated = convolve(ecg_squared, nsamp, integration_window);
        System.out.println("ecg_integrated.size:");
        System.out.println(ecg_integrated.length);

        int[] detected_peaks_indices = find_peaks(ecg_integrated, nsamp, findpeaks_spacing, findpeaks_limit);
        System.out.println("detected_peaks_indices.size:");
        System.out.println(detected_peaks_indices.length);

        int[] peaks_indices_new = adjust_peak(ecg, detected_peaks_indices);
        System.out.println("peaks_indices_new.size:");
        System.out.println(peaks_indices_new.length);

        // generate model input
        // cut out signal fragment [r-100,r+100] ~ 200
        // firstly, discard the first and the last r wave pos index
        // secondly, discard those indices that can't fully intercept the 200 samples
        // finally, upsample the 200-points slice to 360-points, and get 260 points from 360-points slice
        int start=1, end=peaks_indices_new.length-2;
        for(int i=1; i<peaks_indices_new.length-1; i++){
            if(peaks_indices_new[i]-100>=0){
                start=i;
                break;
            }
        }
        for(int i=peaks_indices_new.length-2; i>0; i--){
            if(peaks_indices_new[i]+100<ecg.length){
                end=i;
                break;
            }
        }
        double [][]sigFrag_slice_200 = new double[end-start+1][200];
        double [][]sigFrag_slice_360 = new double[end-start+1][360];
        double [][]sigFrag = new double[end-start+1][260];
        int [][]rr = new int[end-start+1][3];
        int rr_mean = 0;
        for(int i=0; i<end-start+1; i++){
            int pos = peaks_indices_new[i+start]-100;
            for(int j=0; j<200; j++){
                sigFrag_slice_200[i][j] = ecg[pos+j];
            }
            // up-sample
            sigFrag_slice_360[i] = upsample(sigFrag_slice_200[i]);
            // slice from 360 to 260
            for(int j=0; j<260; j++){
                sigFrag[i][j] = sigFrag_slice_360[i][80+j];
            }
            rr[i][0] = peaks_indices_new[i+start] - peaks_indices_new[i+start-1];
            rr[i][1] = peaks_indices_new[i+start+1] - peaks_indices_new[i+start];
            rr_mean += peaks_indices_new[i+start] - peaks_indices_new[i+start-1];
        }
        rr_mean += peaks_indices_new[end+1] - peaks_indices_new[end];
        rr_mean = (int)(rr_mean/(end-start+2));

        for(int i=0; i<end-start+1; i++){
            rr[i][2] = rr_mean;
        }

        // 把以signal_frequency 频率表示的RR间期  转换为 360Hz的RR间期
        int new_sample_rate = 360;
        float rr_scale_ration = ((float) new_sample_rate) / (float)signal_frequency;
        for(int i = 0; i < end-start+1; i++){
            for(int j = 0; j < 3; j++) {
                rr[i][j] = (int)(rr_scale_ration * rr[i][j]);
            }
        }
        Log.d("rr_list", Arrays.deepToString(rr));

        // Normalization
        for (int dimension = 0; dimension < sigFrag.length; ++dimension) {
            double min = sigFrag[dimension][0];
            double max = sigFrag[dimension][0];

            for (int i = 0; i < sigFrag[dimension].length; ++i) {
                if (sigFrag[dimension][i] > max) {
                    max = sigFrag[dimension][i];
                }
                if (sigFrag[dimension][i] < min) {
                    min = sigFrag[dimension][i];
                }
            }

            for (int i = 0; i < sigFrag[dimension].length; ++i) {
                sigFrag[dimension][i] = (sigFrag[dimension][i] - min) / (max - min);
            }
        }

        boolean flag1 = writeToFile(data_save_dir + "/" + ecg_data_name + "_src.csv", sigFrag);
        boolean flag2 = writeToFile(data_save_dir + "/" + ecg_data_name + "_rr.csv", rr);
        boolean flag3 = writeToFileOneDim(data_save_dir + "/" + ecg_data_name + "_rrp.csv", peaks_indices_new);     // R points' position

        // calculate the hrv (heart rate variability) and save to $(ecg_data_name).properties file
        // convert rr_list to unit of second
        double[] rr_list = new double[end-start+1];
        for(int i = 0; i < end-start+1; i++){
            rr_list[i] = rr[i][0] / (double)new_sample_rate;
        }

        // 如果检测到的R波过少，那么产生随机RR序列 / 直接返回
        if (rr_list.length < 10) {
//            rr_list = new double[15];
//            Random rand = new Random();
//            for (int i=0; i<15; i++) {
//                rr_list[i] = ((double) rand.nextInt(400)) / 1000 + 0.8;
//            }
            return rr_list.length;
        }

        Log.d("rr_list", Arrays.toString(rr_list));
        Log.d("rr_list.length", String.valueOf(rr_list.length));
        Log.d("end", String.valueOf(end));
        Log.d("start", String.valueOf(start));

        try {
            List<HRVParameter> result = HRHRVStressArrhy.calcParames(rr_list, data_save_dir, ecg_data_name);
        }catch (IOException e) {
            e.printStackTrace();
        }

        if(flag1&&flag2){return (end-start+1);}
        else {return -1;}
    }

    public static double[] upsample(double[] ecg_slice){
        double[] ecg_slice_upsample = new double[260];

        Resample resample_instance = new Resample(ecg_slice, 360);

        // resample_instance.Resample(ecg_slice, 260);
        ecg_slice_upsample = resample_instance.resampleSignal();
        return ecg_slice_upsample;
    }


    public static double[] diff1d(double[] ecg, int ecg_size){
        double[] ecg_diff = new double[ecg_size - 1];

        for(int i=0; i<(ecg_size-1); i++){
            ecg_diff[i] = ecg[i+1] - ecg[i];
        }

        return ecg_diff;
    }

    public static double[] squared(double[] ecg_diff, int ecg_size){
        double[] ecg_squared = new double[ecg_size - 1];

        for(int i=0; i<(ecg_size-1); i++){
            ecg_squared[i] = (double)Math.pow(ecg_diff[i], 2);
        }

        return ecg_squared;
    }

    public static double[] convolve(double[] ecg_squared, int ecg_size, int window_size){
        double[] ecg_integrated = new double[ecg_size - 1];
        double[] ecg_expanded = new double[ecg_size -1 + window_size*2];

        for(int i=0; i<(ecg_size-1); i++){
            ecg_expanded[i+window_size] = ecg_squared[i];
        }
        for(int i=0; i<window_size; i++)
        {
            ecg_expanded[i] = 0f;
        }
        for(int i=0; i<window_size; i++)
        {
            ecg_expanded[i+window_size+ecg_size-1] = 0f;
        }

        for(int i=0; i<(ecg_size-1); i++){
            double sum=0;
            for (int j = i; j < i+window_size; j++) {
                sum = sum + ecg_expanded[j];
            }
            ecg_integrated[i] = sum;
        }

        return ecg_integrated;
    }

    public static int[] find_peaks(double[] ecg_integrated, int ecg_size, int spacing, double limit){
        double[] ecg_expanded = new double[ecg_size -1 + spacing*2];
        int[] peak_candidate = new int[ecg_size - 1];
        int peak_candidate_num = 0;

        // expand ecg_integrated
        for(int i=0; i<(ecg_size-1); i++){
            ecg_expanded[i+spacing] = ecg_integrated[i];
        }
        for(int i=0; i<spacing; i++)
        {
            ecg_expanded[i] = ecg_integrated[0]-0.0001;
        }
        for(int i=0; i<spacing; i++)
        {
            ecg_expanded[i+spacing+ecg_size-1] = ecg_integrated[ecg_size-1-1]-0.0001;
        }

        // compare the value with neigbouring points and choose the index
        int start_pre = 0;
        int start_post = 0;
        for(int i=0; i<spacing; i++){
            start_pre = spacing-1-i;
            start_post = spacing + 1 + i;
            double[] ecg_pre = Arrays.copyOfRange(ecg_expanded, start_pre, start_pre+ecg_size-1);
            double[] ecg_post = Arrays.copyOfRange(ecg_expanded, start_post, start_post+ecg_size-1);

            for(int j=0; j<ecg_size-1; j++){
                if((ecg_integrated[j] > ecg_pre[j]) && (ecg_integrated[j] > ecg_post[j])){
                    peak_candidate[j]++;
                }
            }
        }


        for(int j=0; j<ecg_size-1; j++){
            if(peak_candidate[j] >= spacing){
                peak_candidate[j] = 1;
                peak_candidate_num++;
            }else{
                peak_candidate[j] = 0;
            }
        }

        int[] peak_candidate_idx = new int[peak_candidate_num];
        int idx_cnt = 0;
        for(int j=0; j<ecg_size-1; j++){

            if(peak_candidate[j] == 1){
                peak_candidate_idx[idx_cnt] = j;
                idx_cnt++;
            }

        }

        return peak_candidate_idx;
    }

    /**
     * adjust peak_index
     *
     */
    public static int[] adjust_peak(double[] ecg, int[] peaks_indices){
        int data_len = ecg.length;
        int pre_window = (int)(0.15 * (double)signal_frequency);
        int post_window = (int)(0.1 * (double)signal_frequency);

        // convert int array to List
        List<Integer> peaks_indices_list = new ArrayList<Integer>(peaks_indices.length);
        for (int i : peaks_indices)
        {
            peaks_indices_list.add(i);
        }

        // compare the first and last peak index with the edge window length
        if(peaks_indices[0] < pre_window){
            peaks_indices_list.remove(0);
        }

        if((peaks_indices[peaks_indices.length - 1] + post_window) >= ecg.length){
            peaks_indices_list.remove(peaks_indices_list.size() - 1);
        }

        // adjust peak
        int start_pre = 0;
        int start_post = 0;
        for(int i=0; i<peaks_indices_list.size(); i++){
            start_pre = peaks_indices_list.get(i) - pre_window;
            start_post = peaks_indices_list.get(i) + post_window;
            double[] ecg_slice = Arrays.copyOfRange(ecg, start_pre, start_post);
            peaks_indices_list.set(i , findMaxIndex(ecg_slice) + start_pre);
        }

        // convert List Interger to array int[]
        int[] peaks_indices_new = new int[peaks_indices_list.size()];
        for(int i = 0; i < peaks_indices_new.length; i++)
            peaks_indices_new[i] = peaks_indices_list.get(i);

        return peaks_indices_new;
    }

    public static int findMaxIndex(double[] arr) {
        double max = arr[0];
        int maxIdx = 0;
        for(int i = 1; i < arr.length; i++) {
            if(arr[i] > max) {
                max = arr[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    // ******************************************************
    // load
    public static ArrayList<String> load_ecg_data(String filepath){
        ArrayList<String> dataAL = new ArrayList<String>();

        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(filepath));
            String line = null;

            while ((line = reader.readLine()) != null) {
                String item[] = line.split(",");
                // only need PPG
                dataAL.add(item[0]);
            }

        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return dataAL;

    }

    public static boolean writeToFileOneDim(String filename, int[] Array){
        try {
            FileWriter fileWriter = new FileWriter(filename);
            for(int value:Array){

                fileWriter.write(value + ",");
                fileWriter.write("\n");
            }
            fileWriter.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean writeToFile(String filename, int[][] Array){
        try {
            FileWriter fileWriter = new FileWriter(filename);
            for(int arr[]:Array){
                for(int value:arr){
                    fileWriter.write(value + ",");
                }
                fileWriter.write("\n");
            }
            fileWriter.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean writeToFile(String filename, double[][] Array){
        try {
            FileWriter fileWriter = new FileWriter(filename);
            for(double arr[]:Array){
                for(double value:arr){
                    fileWriter.write(value + ",");
                }
                fileWriter.write("\n");
            }
            fileWriter.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

}