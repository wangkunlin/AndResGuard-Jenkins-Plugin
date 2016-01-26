package com.tencent.mm.androlib;

import com.tencent.mm.resourceproguard.Configuration;
import com.tencent.mm.util.FileOperation;
import com.tencent.mm.util.TypedValue;
import com.tencent.mm.util.Utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author shwenzhang
 */
public class ResourceApkBuilder {

    private final Configuration config;
    private       File          mOutDir;
    private       File          m7zipOutPutDir;

    private File mUnSignedApk;
    private File mSignedApk;
    private File mSignedWith7ZipApk;

    private File mAlignedApk;
    private File mAlignedWith7ZipApk;

    private String mApkName;
    private PrintStream log;

    public ResourceApkBuilder(Configuration config, PrintStream log) {
        this.config = config;
        this.log = log;
    }

    public void setOutDir(File outDir, String apkname) throws AndrolibException {
        mOutDir = outDir;
        mApkName = apkname;
    }

    public void buildApk(HashMap<String, Integer> compressData) throws IOException, InterruptedException {
        insureFileName();
        generalUnsignApk(compressData);
        signApk();
        use7zApk(compressData);
        alignApk();
        
    }

    private void insureFileName() {
        mUnSignedApk = new File(mOutDir, mApkName + "_unsigned.apk");
        //需要自己安装7zip
        mSignedWith7ZipApk = new File(mOutDir, mApkName + "_signed_7zip.apk");
        mSignedApk = new File(mOutDir, mApkName + "_signed.apk");
        mAlignedApk = new File(mOutDir, mApkName + "_signed_aligned.apk");
        mAlignedWith7ZipApk = new File(mOutDir, mApkName + "_signed_7zip_aligned.apk");
        m7zipOutPutDir = new File(mOutDir, TypedValue.OUT_7ZIP_FILE_PATH);
    }

    private void use7zApk(HashMap<String, Integer> compressData) throws IOException, InterruptedException {
        if (!config.mUse7zip) {
            return;
        }
        if (!config.mUseSignAPk) {
            throw new IOException("if you want to use 7z, you must set the sign issue to active in the config file first");
        }
        if (!mSignedApk.exists()) {
            throw new IOException(
                String.format("can not found the signed apk file to 7z, if you want to use 7z, " +
                    "you must fill the sign data in the config file path=%s", mSignedApk.getAbsolutePath())
            );
        }
        log.printf("use 7zip to repackage: %s, will cost much more time\n", mSignedWith7ZipApk.getName());
        FileOperation.unZipAPk(mSignedApk.getAbsolutePath(), m7zipOutPutDir.getAbsolutePath());
        //首先一次性生成一个全部都是压缩的安装包
        generalRaw7zip();

        ArrayList<String> storedFiles = new ArrayList<String>();
        //对于不压缩的要update回去
        for (String name : compressData.keySet()) {
            File file = new File(m7zipOutPutDir.getAbsolutePath() + File.separator + name);
            if (!file.exists()) {
                continue;
            }
            int method = compressData.get(name);
            if (method == TypedValue.ZIP_STORED) {
                storedFiles.add(name);
            }
        }

        addStoredFileIn7Zip(storedFiles);

        if (!mSignedWith7ZipApk.exists()) {
            throw new IOException(String.format(
                "7z repackage signed apk fail,you must install 7z command line version first, linux: p7zip, window: 7za, path=%s",
                mSignedWith7ZipApk.getAbsolutePath()));
        }
    }

    private void signApk() throws IOException, InterruptedException {
        //尝试去对apk签名
        if (config.mUseSignAPk) {
            log.printf("signing apk: %s\n", mSignedApk.getName());
            if (mSignedApk.exists()) {
                mSignedApk.delete();
            }

            String cmd = "jarsigner -sigalg MD5withRSA -digestalg SHA1 -keystore " + config.mSignatureFile
                + " -storepass " + config.mStorePass
                + " -keypass " + config.mKeyPass
                + " -signedjar " + mSignedApk.getAbsolutePath()
                + " " + mUnSignedApk.getAbsolutePath()
                + " " + config.mStoreAlias;
            Process pro;
            pro = Runtime.getRuntime().exec(cmd);
            //destroy the stream
            pro.waitFor();
            pro.destroy();

            if (!mSignedApk.exists()) {
                throw new IOException(
                    String.format("can not found the signed apk file, is the input sign data correct? path=%s",
                        mSignedApk.getAbsolutePath())
                );
            }
        }
    }

    private void alignApk() throws IOException, InterruptedException {
        //如果不签名就肯定不需要对齐了
        if (!config.mUseSignAPk) {
            return;
        }
        if (mSignedWith7ZipApk.exists()) {
            if (mSignedApk.exists()) {
                alignApk(mSignedApk, mAlignedApk);
            }
            alignApk(mSignedWith7ZipApk, mAlignedWith7ZipApk);
        } else if (mSignedApk.exists()) {
            alignApk(mSignedApk, mAlignedApk);
        } else {
            throw new IOException("can not found any signed apk file");
        }
    }

    private void alignApk(File before, File after) throws IOException, InterruptedException {
        log.printf("zipaligning apk: %s\n", before.getName());
        if (!before.exists()) {
            throw new IOException(String.format(
                "can not found the raw apk file to zipalign, path=%s",
                before.getAbsolutePath()));
        }
        String cmd = Utils.isPresent(config.mZipalignPath) ? config.mZipalignPath : TypedValue.COMMAND_ZIPALIGIN;
        cmd += " 4 " + before.getAbsolutePath() + " " + after.getAbsolutePath();
        Process pro;
        pro = Runtime.getRuntime().exec(cmd);
        //destroy the stream
        pro.waitFor();
        pro.destroy();
        if (!after.exists()) {
            throw new IOException(
                String.format("can not found the aligned apk file, the ZipAlign path is correct? path=%s", mAlignedApk.getAbsolutePath())
            );
        }
    }

    private void generalUnsignApk(HashMap<String, Integer> compressData) throws IOException, InterruptedException {
        log.printf("general unsigned apk: %s\n", mUnSignedApk.getName());
        File tempOutDir = new File(mOutDir.getAbsolutePath(), TypedValue.UNZIP_FILE_PATH);
        if (!tempOutDir.exists()) {
            log.printf("Missing apk unzip files, path=%s\n", tempOutDir.getAbsolutePath());
            throw new IOException(String.format("Missing apk unzip files, path=%s\n", tempOutDir.getAbsolutePath()));
        }

        File[] unzipFiles = tempOutDir.listFiles();
        List<File> collectFiles = new ArrayList<File>();
        for (File f : unzipFiles) {
            String name = f.getName();
            if (name.equals("res") || name.equals(config.mMetaName) || name.equals("resources.arsc")) {
                continue;
            }
            collectFiles.add(f);
        }

        File destResDir = new File(mOutDir.getAbsolutePath(), "res");
        //添加修改后的res文件
        if (!config.mKeepRoot) {
            destResDir = new File(mOutDir.getAbsolutePath(), TypedValue.RES_FILE_PATH);
        }

        /**
         * NOTE:文件数量应该是一样的，如果不一样肯定有问题
         */
        File rawResDir = new File(tempOutDir.getAbsolutePath() + File.separator + "res");
        log.printf("DestResDir %d rawResDir %d\n", FileOperation.getlist(destResDir), FileOperation.getlist(rawResDir));
        if (FileOperation.getlist(destResDir) != FileOperation.getlist(rawResDir)) {
            throw new IOException(String.format(
                "the file count of %s, and the file count of %s is not equal, there must be some problem\n",
                rawResDir.getAbsolutePath(), destResDir.getAbsolutePath()));
        }
        if (!destResDir.exists()) {
            log.printf("Missing res files, path=%s\n", destResDir.getAbsolutePath());
            throw new IOException(String.format("Missing res files, path=%s\n", tempOutDir.getAbsolutePath()));
        }
        //这个需要检查混淆前混淆后，两个res的文件数量是否相等
        collectFiles.add(destResDir);
        File rawARSCFile = new File(mOutDir.getAbsolutePath() + File.separator + "resources.arsc");
        if (!rawARSCFile.exists()) {
            log.printf("Missing resources.arsc files, path=%s\n", rawARSCFile.getAbsolutePath());
            throw new IOException(String.format("Missing resources.arsc files, path=%s\n", tempOutDir.getAbsolutePath()));
        }
        collectFiles.add(rawARSCFile);
        FileOperation.zipFiles(collectFiles, mUnSignedApk, compressData);

        if (!mUnSignedApk.exists()) {
            throw new IOException(String.format(
                "can not found the unsign apk file path=%s",
                mUnSignedApk.getAbsolutePath()));
        }
    }

    private void addStoredFileIn7Zip(ArrayList<String> storedFiles) throws IOException, InterruptedException {
        log.printf("rewrite the stored file into the 7zip, file count:%d\n", storedFiles.size());

        String storedParentName = mOutDir.getAbsolutePath() + File.separator + "storefiles" + File.separator;
        String outputName = m7zipOutPutDir.getAbsolutePath() + File.separator;
        for (String name : storedFiles) {
            FileOperation.copyFileUsingStream(new File(outputName + name), new File(storedParentName + name));

        }
        Process pro = null;

        storedParentName = storedParentName + File.separator + "*";

        //极限压缩
        String cmd = Utils.isPresent(config.m7zipPath) ? config.m7zipPath : TypedValue.COMMAND_7ZIP;
        cmd += " a -tzip " + mSignedWith7ZipApk.getAbsolutePath() + " " + storedParentName + " -mx0";
        pro = Runtime.getRuntime().exec(cmd);

        InputStreamReader ir;
        LineNumberReader input;
        ir = new InputStreamReader(pro.getInputStream());
        input = new LineNumberReader(ir);

        //如果不读会有问题，被阻塞
        while (input.readLine() != null) {
            ;
        }

        //destroy the stream
        if (pro != null) {
            pro.waitFor();
            pro.destroy();
        }
    }

    private void generalRaw7zip() throws IOException, InterruptedException {
        log.printf("general the raw 7zip file\n");
        Process pro;
        String outPath = m7zipOutPutDir.getAbsoluteFile().getAbsolutePath();

        String path = outPath + File.separator + "*";

        //极限压缩
        String cmd = Utils.isPresent(config.m7zipPath) ? config.m7zipPath : TypedValue.COMMAND_7ZIP;
        cmd += " a -tzip " + mSignedWith7ZipApk.getAbsolutePath() + " " + path + " -mx9";
        pro = Runtime.getRuntime().exec(cmd);

        InputStreamReader ir = null;
        LineNumberReader input = null;

        ir = new InputStreamReader(pro.getInputStream());

        input = new LineNumberReader(ir);

        //如果不读会有问题，被阻塞
        while (input.readLine() != null) {
            ;
        }
        //destroy the stream
        if (pro != null) {
            pro.waitFor();
            pro.destroy();
        }
    }
}
