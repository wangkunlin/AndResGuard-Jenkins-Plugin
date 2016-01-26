package com.tencent.mm.resourceproguard;

import com.tencent.mm.androlib.AndrolibException;
import com.tencent.mm.androlib.ApkDecoder;
import com.tencent.mm.androlib.ResourceApkBuilder;
import com.tencent.mm.androlib.res.decoder.ARSCDecoder;
import com.tencent.mm.directory.DirectoryException;
import com.tencent.mm.util.FileOperation;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;


/**
 * @author shwenzhang
 * @author simsun
 */
public class ResGuardMain {
    public static final int ERRNO_ERRORS = 1;
    public static final int ERRNO_USAGE  = 2;
    protected static long          mRawApkSize;
    protected static String        mRunningLocation;
    protected static long          mBeginTime;
    /**
     * 是否通过命令行方式设置
     */
    public boolean mSetSignThroughCmd    = false;
    public boolean mSetMappingThroughCmd = false;
    public String  m7zipPath             = null;
    public String  mZipalignPath         = null;
    protected        Configuration config;
    protected        File          mOutDir;
    private PrintStream log;

    public ResGuardMain(PrintStream log) {
        this.log = log;
    }
    
    public static void gradleRun(InputParam inputParam, PrintStream log)
            throws IOException {
        ResGuardMain m = new ResGuardMain(log);
        m.run(inputParam);
    }

    private void run(InputParam inputParam) throws IOException {
        loadConfigFromGradle(inputParam);
        log.println("resourceproguard begin");
        resourceProguard(new File(inputParam.outFolder), inputParam.apkPath);
        log.printf(
                "resources proguard done, you can go to file to find the output %s\n",
                mOutDir.getAbsolutePath());
        clean();
    }

    protected void clean() {
        config = null;
        ARSCDecoder.mTableStringsProguard.clear();
    }

    private void loadConfigFromGradle(InputParam inputParam) throws IOException {
        config = new Configuration(inputParam, m7zipPath, mZipalignPath);
    }

    protected void resourceProguard(File outputFile, String apkFilePath) throws IOException {
        ApkDecoder decoder = new ApkDecoder(config);
        decoder.setLog(log);
        File apkFile = new File(apkFilePath);
        if (!apkFile.exists()) {
            log.printf("the input apk %s does not exit", apkFile.getAbsolutePath());
            throw new IOException(String.format("the input apk %s does not exit", apkFile.getAbsolutePath()));
        }
        mRawApkSize = FileOperation.getFileSizes(apkFile);
        try {
            decodeResource(outputFile, decoder, apkFile);
            buildApk(decoder, apkFile);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private void decodeResource(File outputFile, ApkDecoder decoder, File apkFile) throws AndrolibException, IOException, DirectoryException {
        decoder.setApkFile(apkFile);
        if (outputFile == null) {
            mOutDir = new File(mRunningLocation, apkFile.getName().substring(0, apkFile.getName().indexOf(".apk")));
        } else {
            mOutDir = outputFile;
        }
        decoder.setOutDir(mOutDir.getAbsoluteFile());
        decoder.decode();
    }

    private void buildApk(ApkDecoder decoder, File apkFile) throws AndrolibException, IOException, InterruptedException {
        ResourceApkBuilder builder = new ResourceApkBuilder(config, log);
        String apkBasename = apkFile.getName();
        apkBasename = apkBasename.substring(0, apkBasename.indexOf(".apk"));
        builder.setOutDir(mOutDir, apkBasename);
        builder.buildApk(decoder.getCompressData());
    }

    public double diffApkSizeFromRaw(long size) {
        return (mRawApkSize - size) / 1024.0;
    }

}