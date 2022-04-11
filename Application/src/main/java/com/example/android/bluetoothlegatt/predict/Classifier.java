//package com.example.android.bluetoothlegatt.predict;
//
//import android.content.Context;
//import android.util.Log;
//
//import org.pytorch.IValue;
//import org.pytorch.Module;
//import org.pytorch.Tensor;
//
//import java.io.BufferedReader;
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.FileReader;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//
//public class Classifier {
//
//    private static Module module = null;
//    public static String[] MIT_BIH = new String[]{
//            "非异位搏动（Non-ectopic beats）",
//            "室上性异位搏动（Supra ventricular ectopic beats）",
//            "室性早搏（Ventricular ectopic beats）",
//            "室性/室上性异位搏动（Supraventricular and Ventricular Ectopic Beats）",
//    };
//
//    public static String modelInference(Context context, String externalFilesDirPath, String dataSaveName, int lineNumber){
//        // load model
//        if(module==null){
//            String modelPath = getModelPath(context, "BioPlat_v1.pt");
//            module = Module.load(modelPath);
//        }else{
//            Log.d("Classifier.java","模型已加载");
//        }
//        // load data
//        String srcFileName = externalFilesDirPath + "/" + dataSaveName + "_src.csv";
//        String rrFileName = externalFilesDirPath + "/" + dataSaveName + "_rr.csv";
//        float [][]src = loadModelInput(srcFileName, lineNumber, true);
//        float [][]rr = loadModelInput(rrFileName, lineNumber, false);
//        int []output = new int[lineNumber];
//        for(int i=0; i<lineNumber; i++){
//            // 创建新的Tensor实例，from_blob不申请新空间，只是数据的view
//            Tensor tmp_rr = Tensor.fromBlob(rr[i], new long[] {1,3});
//            Tensor tmp_src = Tensor.fromBlob(src[i], new long[] {1,260});
//            // IValue对象可以保留对传递到其构造函数中的对象的引用，并且可以从toX()返回对其内部状态的引用
//            final Tensor outputTensor = module.forward(IValue.from(tmp_src),IValue.from(tmp_rr)).toTensor();
//            final float[] scores = outputTensor.getDataAsFloatArray();
//            float maxScore = -Float.MAX_VALUE;
//            int maxScoreIdx = -1;
//            for (int j = 0; i < scores.length; i++){
//                if (scores[i] > maxScore){
//                    maxScore = scores[i];
//                    maxScoreIdx = i;
//                }
//            }
//            output[i] = maxScoreIdx;
//        }
//        int count_N=0, count_S=0, count_V=0;
//        for(int tmp:output){
//            if(tmp==0){ count_N++; break;}
//            if(tmp==1){ count_S++; break;}
//            if(tmp==2){ count_V++; break;}
//        }
//        int outputIdx = -1;
//        if (count_S == 0 && count_V == 0){ outputIdx = 0; }
//        else if (count_V == 0){ outputIdx = 1; }
//        else if (count_S == 0){ outputIdx = 2; }
//        else outputIdx = 3;
//        return MIT_BIH[outputIdx];
//    }
//
//
//    public static String getModelPath(Context context, String assetName) {
//        // 外部存储私有目录，一般存储临时缓存数据，可通过设置中的清除缓存删除文件
//        // getExternalCacheDir() = storage/sdcard/Android/data/包名/cache
//        File file = new File(context.getExternalCacheDir(), assetName);
//        if (file.exists() && file.length()>0){
//            return file.getAbsolutePath();
//        }
//        // 第一次加载模型时，将assets下的model拷贝到缓存目录
//        try (InputStream is = context.getAssets().open(assetName)) {
//            try (OutputStream os = new FileOutputStream(file)) {
//                byte[] buffer = new byte[4 * 1024];
//                int read;
//                while ((read = is.read(buffer)) != -1) {
//                    os.write(buffer, 0, read);
//                }
//                os.flush();
//            }
//            return file.getAbsolutePath();
//        } catch (IOException e) {
//            Log.e("load model", "Error process asset " + assetName + " to file path");
//        }
//        return null;
//    }
//
//    public static float[][] loadModelInput(String filename, int lineNumber, boolean flag){
//        int column = 3;
//        if(flag){column = 260;}
//        float [][]data = new float[lineNumber][column];
//        try {
//            BufferedReader reader = new BufferedReader(new FileReader(filename));
//            String line = null;
//            int count = 0;
//            while((line=reader.readLine())!=null){
//                String item[] = line.split(",");
//                for (int i=0; i<column; i++){
//                    data[count][i] = Float.parseFloat(item[i]);
//                }
//                count++;
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return data;
//    }
//}
