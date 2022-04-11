package com.example.android.bluetoothlegatt.predict;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import hrv.HRVLibFacade;
import hrv.RRData;
import hrv.calc.parameter.HRVParameter;
import hrv.calc.parameter.HRVParameterEnum;
import units.TimeUnit;


public class HRHRVStressArrhy {
    public HRHRVStressArrhy() { }

    /*
     * calculate parameters and save to $(ecg_data_name).properties file
     */
    public static List<HRVParameter> calcParames(double[] rr_list, String data_save_dir, String ecg_data_name) throws IOException {
//        // load rr array
//        String rr_data_name = ecg_data_name + "";
//        double[] rr_list = load_rr_data_double(data_save_dir, rr_data_name);

        // save parmaters to properties-type file
        HashMap<String, Double> parameters_map = new HashMap<String, Double>();
        String parameters_save_name = data_save_dir + "/" + ecg_data_name + "_paramters.properties";

        // multiply 1000, change the unit from s to ms
//        for (int i=0; i<rr_list.length; i++){
//            rr_list[i] = rr_list[i] ;
//        }

        // build RRData, construct rrTimeAxis(accumulate rr_interval)
        RRData rr_data = RRData.createFromRRInterval(rr_list, TimeUnit.SECOND);

        // calculate parameters
        HRVLibFacade facade = new HRVLibFacade(rr_data);
        List<HRVParameter> result = facade.calculateParameters();

        HRVCalculatorFacade facade_calc =  new HRVCalculatorFacade(rr_data);
        HRVParameter  rr_mean_parameter = facade_calc.getMean();
        rr_mean_parameter.setValue(60/rr_mean_parameter.getValue());

//        System.out.println("name:" + rr_mean_parameter.getName());
//        System.out.println("value:" + rr_mean_parameter.getValue());

        result.add(rr_mean_parameter);

        //save to hashMap
        Iterator result_iterator = result.iterator();

        while(result_iterator.hasNext()){
            HRVParameter param_temp = (HRVParameter)result_iterator.next();
            parameters_map.put(param_temp.getName(), param_temp.getValue());
        }

        // get arrhy
        int arrhy_status = 0;

        if(rr_mean_parameter.getValue() < 60){
            arrhy_status = 1; // too low
        }else if(rr_mean_parameter.getValue() > 100){
            arrhy_status = 2; // too high
        }
        result.add(new HRVParameter(HRVParameterEnum.NON, (double)(arrhy_status), "arrhy"));

        parameters_map.put("arrhy", (double)(arrhy_status));

        // get Stress
        int stress_metrics_count = 0;
        result_iterator = result.iterator();
        Set<HRVParameterEnum> stressParameters = EnumSet.of(HRVParameterEnum.MEAN, HRVParameterEnum.RMSSD,
                HRVParameterEnum.SDNN, HRVParameterEnum.PNN50);

        while(result_iterator.hasNext()){
            HRVParameter param_temp = (HRVParameter)result_iterator.next();

            if(stressParameters.contains(param_temp.getType())){
                System.out.println("name:" + param_temp.getName());
                System.out.println("value:" + param_temp.getValue());

                switch (param_temp.getType())
                {
                    case MEAN:
                        if(param_temp.getValue() > 85)
                            stress_metrics_count++;
                        break;
                    case RMSSD:
                        if(param_temp.getValue() < 0.045)
                            stress_metrics_count++;
                        break;
                    case SDNN:
                        if(param_temp.getValue() < 0.055)
                            stress_metrics_count++;
                        break;
                    case PNN50:
                        if(param_temp.getValue() < 7)
                            stress_metrics_count++;
                        break;
                    default:
                        break;
                }
            }
        }

        if(stress_metrics_count >= 3){
            stress_metrics_count = 1;
        }else{
            stress_metrics_count = 0;
        }

        result.add(new HRVParameter(HRVParameterEnum.NON, (double)(stress_metrics_count), "Stress"));
        parameters_map.put("Stress", (double)(stress_metrics_count));

        // save to file
        writeToFile(parameters_save_name, parameters_map);

        return result;
    }

    private static double[] load_rr_data_double(String rr_data_save_dir, String rr_data_name) {
        // load data and convert to double type
        ArrayList<String> rr_data_raw = load_rr_data_string(rr_data_save_dir + "/" + rr_data_name + ".txt");

        int nsamp = rr_data_raw.size();

        System.out.println("rr_data_raw.size:");
        System.out.println(rr_data_raw.size());

        double[] rr_list = new double[nsamp];
        for (int i = 0; i < nsamp; i++) {
            rr_list[i] = Double.parseDouble(rr_data_raw.get(i));
        }

        return rr_list;
    }

    // ******************************************************
    // load
    private static ArrayList<String> load_rr_data_string(String filepath){
        ArrayList<String> dataAL = new ArrayList<String>();

        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(filepath));
            //reader.readLine();//
            String line = null;//

            int line_num = 0;
            while ((line = reader.readLine()) != null) {

                if(line_num >= 0){
                    String item[] = line.split("\t");
                    dataAL.add(item[0]);
                }
                //System.out.println(dataAL.get(line_num));
                line_num++;

            }
            //System.out.println(dataAL.size());
            //System.out.print(ticketStr.toString());

        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return dataAL;
    }

    public static Map<String, Double> loadFromFile(String filename) throws IOException {
        Map<String, Map<String, Double>> outerMap = new HashMap<>();

        try (ObjectInput objectInputStream = new ObjectInputStream(new BufferedInputStream(new FileInputStream(filename)))) {
            outerMap = (Map<String, Map<String, Double>>) objectInputStream.readObject();
        } catch (Throwable cause) {
            cause.printStackTrace();
        }

//        Map<String, Double> ldapContent = new HashMap<String, Double>();
//        Properties properties = new Properties();
//        properties.load(new FileInputStream(filename));
//
//        for (String key : properties.stringPropertyNames()) {
//            ldapContent.put(key, (Double) properties.get(key));
//        }
        return outerMap.get("key");
    }

    private static boolean writeToFile(String filename, HashMap<String, Double> parameters_map) throws IOException {
        Map<String, Map<String, Double>> outerMap = new HashMap<>();
        outerMap.put("key",parameters_map);
        try (ObjectOutput objectOutputStream = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(filename, false)))) {
            objectOutputStream.writeObject(outerMap);
            return true;
        } catch (Throwable cause) {
            cause.printStackTrace();
            return false;
        }

//        Properties properties = new Properties();
//
//        for (Map.Entry<String,Double> entry : parameters_map.entrySet()) {
//            properties.put(entry.getKey(), entry.getValue());
//        }
//
//        try {
//            properties.store(new FileOutputStream(filename), null);
//            return true;
//        } catch (IOException e) {
//            return false;
//        }


//        try {
//            FileWriter fileWriter = new FileWriter(filename);
//            for(int arr[]:Array){
//                for(int value:arr){
//                    fileWriter.write(value + ",");
//                }
//                fileWriter.write("\n");
//            }
//            fileWriter.close();
//            return true;
//        } catch (IOException e) {
//            e.printStackTrace();
//            return false;
//        }
    }

}
