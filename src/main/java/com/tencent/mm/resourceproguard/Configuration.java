package com.tencent.mm.resourceproguard;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.tencent.mm.util.Utils;

/**
 * @author shwenzhang
 */
public class Configuration {

    public final HashMap<String, HashMap<String, HashSet<Pattern>>> mWhiteList;
    public final HashMap<String, HashMap<String, HashMap<String, String>>> mOldResMapping;
    public final HashMap<String, String> mOldFileMapping;
    public final HashSet<Pattern> mCompressPatterns;

    private final Pattern MAP_PATTERN = Pattern.compile("\\s+(.*)->(.*)");
    public boolean mUse7zip = true;
    public boolean mKeepRoot = false;
    public String mMetaName = "META-INF";
    public boolean mUseSignAPk = false;
    public boolean mUseKeepMapping = false;
    public File mSignatureFile;
    public File mOldMappingFile;
    public boolean mUseWhiteList;
    public boolean mUseCompress;
    public String mKeyPass;
    public String mStorePass;
    public String mStoreAlias;
    public String m7zipPath;
    public String mZipalignPath;

    /**
     * use by gradle
     */
    public Configuration(InputParam param, String sevenzipPath,
            String zipAlignPath) throws IOException {
        mWhiteList = new HashMap<String, HashMap<String, HashSet<Pattern>>>();
        mOldResMapping = new HashMap<String, HashMap<String, HashMap<String, String>>>();
        mOldFileMapping = new HashMap<String, String>();
        mCompressPatterns = new HashSet<Pattern>();
        setSignData(param.signFile, param.keypass, param.storealias,
                param.storepass);
        if (param.mappingFile != null) {
            mUseKeepMapping = true;
            setKeepMappingData(param.mappingFile);
        }

        if (param.whiteList != null) {
            for (String item : param.whiteList) {
                if (StringUtils.isEmpty(item)) {
                    continue;
                }
                mUseWhiteList = true;
                addWhiteList(item);
            }
        }
        mUse7zip = param.use7zip;
        mKeepRoot = param.keepRoot;
        if (!StringUtils.isEmpty(param.metaName)) {
            mMetaName = param.metaName;
        }
        if (param.compressFilePattern != null) {
            for (String item : param.compressFilePattern) {
                if (StringUtils.isEmpty(item)) {
                    continue;
                }
                mUseCompress = true;
                addToCompressPatterns(item);
            }
        }
        this.m7zipPath = sevenzipPath;
        this.mZipalignPath = zipAlignPath;
    }

    public void setSignData(File signatureFile, String keypass,
            String storealias, String storepass) throws IOException {
        if (signatureFile == null) {
            return;
        }
        mUseSignAPk = true;
        mSignatureFile = signatureFile;
        if (!mSignatureFile.exists()) {
            throw new IOException(String.format(
                    "the signature file do not exit, raw path= %s\n",
                    mSignatureFile.getAbsolutePath()));
        }
        mKeyPass = keypass;
        mStoreAlias = storealias;
        mStorePass = storepass;
    }

    public void setKeepMappingData(File mappingFile) throws IOException {
        if (mUseKeepMapping) {
            mOldMappingFile = mappingFile;

            if (!mOldMappingFile.exists()) {
                throw new IOException(String.format(
                        "the old mapping file do not exit, raw path= %s",
                        mOldMappingFile.getAbsolutePath()));
            }
            processOldMappingFile();
        }
    }

    private void addWhiteList(String item) throws IOException {
        if (item.length() == 0) {
            throw new IOException("Invalid whiteList config");
        }

        int packagePos = item.indexOf(".R.");
        if (packagePos == -1) {

            throw new IOException(
                    String.format(
                            "please write the full package name,eg com.tencent.mm.R.drawable.dfdf, but yours %s\n",
                            item));
        }
        // 先去掉空格
        item = item.trim();
        String packageName = item.substring(0, packagePos);
        // 不能通过lastDot
        int nextDot = item.indexOf(".", packagePos + 3);
        String typeName = item.substring(packagePos + 3, nextDot);
        String name = item.substring(nextDot + 1);
        HashMap<String, HashSet<Pattern>> typeMap;

        if (mWhiteList.containsKey(packageName)) {
            typeMap = mWhiteList.get(packageName);
        } else {
            typeMap = new HashMap<String, HashSet<Pattern>>();
        }

        HashSet<Pattern> patterns;
        if (typeMap.containsKey(typeName)) {
            patterns = typeMap.get(typeName);
        } else {
            patterns = new HashSet<Pattern>();
        }

        name = Utils.convetToPatternString(name);
        Pattern pattern = Pattern.compile(name);
        patterns.add(pattern);
        typeMap.put(typeName, patterns);
        mWhiteList.put(packageName, typeMap);
    }

    private void addToCompressPatterns(String value) throws IOException {
        if (value.length() == 0) {
            throw new IOException("Invalid compress pattern config");
        }
        value = Utils.convetToPatternString(value);
        Pattern pattern = Pattern.compile(value);
        mCompressPatterns.add(pattern);
    }

    private void processOldMappingFile() throws IOException {
        mOldResMapping.clear();
        mOldFileMapping.clear();

        FileReader fr;
        try {
            fr = new FileReader(mOldMappingFile);
        } catch (FileNotFoundException ex) {
            throw new IOException(String.format(
                    "Could not find old mapping file %s",
                    mOldMappingFile.getAbsolutePath()));
        }
        BufferedReader br = new BufferedReader(fr);
        try {
            String line = br.readLine();

            while (line != null) {
                if (line.length() > 0) {
                    Matcher mat = MAP_PATTERN.matcher(line);

                    if (mat.find()) {
                        String nameAfter = mat.group(2);
                        String nameBefore = mat.group(1);
                        nameAfter = nameAfter.trim();
                        nameBefore = nameBefore.trim();

                        // 如果有这个的话，那就是mOldFileMapping
                        if (line.contains("/")) {
                            mOldFileMapping.put(nameBefore, nameAfter);
                        } else {
                            // 这里是resid的mapping
                            int packagePos = nameBefore.indexOf(".R.");
                            if (packagePos == -1) {
                                throw new IOException(
                                        String.format(
                                                "the old mapping file packagename is malformed, "
                                                        + "it should be like com.tencent.mm.R.attr.test, yours %s\n",
                                                nameBefore));

                            }
                            String packageName = nameBefore.substring(0,
                                    packagePos);
                            int nextDot = nameBefore.indexOf(".",
                                    packagePos + 3);
                            String typeName = nameBefore.substring(
                                    packagePos + 3, nextDot);

                            String beforename = nameBefore
                                    .substring(nextDot + 1);
                            String aftername = nameAfter.substring(nameAfter
                                    .indexOf(".", packagePos + 3) + 1);

                            HashMap<String, HashMap<String, String>> typeMap;

                            if (mOldResMapping.containsKey(packageName)) {
                                typeMap = mOldResMapping.get(packageName);
                            } else {
                                typeMap = new HashMap<String, HashMap<String, String>>();
                            }

                            HashMap<String, String> namesMap;
                            if (typeMap.containsKey(typeName)) {
                                namesMap = typeMap.get(typeName);
                            } else {
                                namesMap = new HashMap<String, String>();
                            }
                            namesMap.put(beforename, aftername);

                            typeMap.put(typeName, namesMap);
                            mOldResMapping.put(packageName, typeMap);
                        }
                    }

                }
                line = br.readLine();
            }
        } catch (IOException ex) {
            throw new RuntimeException("Error while mapping file");
        } finally {
            try {
                br.close();
                fr.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
