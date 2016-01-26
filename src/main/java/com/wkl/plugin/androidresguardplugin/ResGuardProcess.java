package com.wkl.plugin.androidresguardplugin;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;

import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.tencent.mm.resourceproguard.InputParam;
import com.tencent.mm.resourceproguard.ResGuardMain;
import com.wkl.plugin.util.Util;

public class ResGuardProcess extends Builder implements SimpleBuildStep {

    private String apkPath = "app/build/outputs/apk/*release*.apk";
    private boolean keepRoot = false;
    private String whiteList;
    private String oldMapping;
    private String compress;
    private String signPath;
    private String storePass;
    private String keyPass;
    private String alias;
    private String outFolder = "app/build/outputs/apk/resguard";
    private String metaName;

    @DataBoundConstructor
    public ResGuardProcess(String apkPath, boolean keepRoot, String whiteList,
            String oldMapping, String compress, String signPath,
            String storePass, String keyPass, String alias, String metaName,
            String outFolder) {
        if (!StringUtils.isEmpty(apkPath)) {
            this.apkPath = apkPath;
        }
        this.keepRoot = keepRoot;
        this.whiteList = whiteList;
        this.oldMapping = oldMapping;
        this.compress = compress;
        this.signPath = signPath;
        this.storePass = storePass;
        this.keyPass = keyPass;
        this.metaName = metaName;
        this.alias = alias;
        if (!StringUtils.isEmpty(outFolder)) {
            this.outFolder = outFolder;
        }
    }

    public String getApkPath() {
        return apkPath;
    }

    public boolean isKeepRoot() {
        return keepRoot;
    }

    public String getWhiteList() {
        return whiteList;
    }

    public String getOldMapping() {
        return oldMapping;
    }

    public String getCompress() {
        return compress;
    }

    public String getSignPath() {
        return signPath;
    }

    public String getStorePass() {
        return storePass;
    }

    public String getKeyPass() {
        return keyPass;
    }

    public String getAlias() {
        return alias;
    }

    public String getOutFolder() {
        return outFolder;
    }

    public String getMetaName() {
        return metaName;
    }

    @Override
    public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher,
            TaskListener listener) throws InterruptedException, IOException {
        PrintStream log = listener.getLogger();
        File parent = null;
        String fileName = null;
        if (apkPath.contains(File.separator)) {
            String dir = apkPath.substring(0, apkPath.lastIndexOf(File.separator));
            parent = new File(workspace.getRemote(), dir);
            fileName = apkPath.substring(apkPath.lastIndexOf(File.separator) + 1);
        } else {
            parent = new File(workspace.getRemote());
            fileName = apkPath;
        }
        log.println("parent:" + parent.getAbsolutePath());
        log.println("fileName:" + fileName);
        if (!parent.exists() || !parent.isDirectory()) {
            throw new IOException("apk path not exist, please check");
        }

        InputParam.Builder builder = new InputParam.Builder();
        if (!StringUtils.isEmpty(compress)) {
            if (compress.contains(",")) {
                String[] com = compress.split(",");
                builder.setCompressFilePattern(Arrays.asList(com));
            } else {
                List<String> com = new ArrayList<String>(1);
                com.add(compress);
                builder.setCompressFilePattern(com);
            }
        }
        builder.setKeepRoot(keepRoot);
        builder.setKeypass(keyPass);
        if (!StringUtils.isEmpty(oldMapping)) {
            String dir;
            String file;
            if (oldMapping.contains(File.separator)) {
                dir = oldMapping.substring(0, oldMapping.lastIndexOf(File.separator));
                dir = workspace.getRemote() + File.separator + dir;
                file = oldMapping.substring(oldMapping.lastIndexOf(File.separator) + 1);
            } else {
                dir = workspace.getRemote();
                file = oldMapping;
            }
            File map = new File(dir, file);
            if (map.exists()) {
                builder.setMappingFile(map);
            }
        }
        builder.setOutBuilder(workspace.getRemote() + File.separator
                + outFolder);
        if (!StringUtils.isEmpty(signPath)) {
            String dir;
            String file;
            if (signPath.contains(File.separator)) {
                dir = signPath.substring(0, signPath.lastIndexOf(File.separator));
                dir = workspace.getRemote() + File.separator + dir;
                file = signPath.substring(signPath.lastIndexOf(File.separator) + 1);
            } else {
                dir = workspace.getRemote();
                file = signPath;
            }
            File signFile = new File(dir, file);
            builder.setSignFile(signFile);
        }
        builder.setStorealias(alias);
        builder.setStorepass(storePass);
        builder.setUse7zip(true);
        builder.setMetaName(metaName);
        if (!StringUtils.isEmpty(whiteList)) {
            if (whiteList.contains(",")) {
                String white[] = whiteList.split(",");
                builder.setWhiteList(Arrays.asList(white));
            } else {
                List<String> white = new ArrayList<String>(1);
                white.add(whiteList);
                builder.setWhiteList(white);
            }
        }
        if (apkPath.contains("*") || apkPath.contains("?")) {
            File[] files = Util.searchFileWithPattern(parent, fileName);
            if (files == null || files.length == 0) {
                log.println("android resGuard:no apk file to guard!");
                return;
            }
            for (File file : files) {
                log.printf("resguard file: %s \n", file.getAbsolutePath());
                builder.setApkPath(file.getAbsolutePath());
                InputParam param = builder.create();
                ResGuardMain.gradleRun(param, log);
            }
        } else {
            File file = new File(parent, fileName);
            if (!file.exists()) {
                log.println("android resGuard:no apk file to guard!");
                return;
            }
            log.printf("resguard file: %s \n", file.getAbsolutePath());
            builder.setApkPath(file.getAbsolutePath());
            InputParam param = builder.create();
            ResGuardMain.gradleRun(param, log);
        }
        log.println("resguard complete");
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends
            BuildStepDescriptor<Builder> {

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public FormValidation doCheckMetaName(@QueryParameter String value) {
            return FormValidation.ok();
        }

        public FormValidation doCheckOutFolder(@QueryParameter String value) {
            if (value.length() > 0 && value.startsWith(File.separator)) {
                return FormValidation
                        .error("Please do not use file separator with the path starts!");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckAlias(@QueryParameter String value) {
            return FormValidation.ok();
        }

        public FormValidation doCheckKeyPass(@QueryParameter String value) {
            return FormValidation.ok();
        }

        public FormValidation doCheckStorePass(@QueryParameter String value) {
            return FormValidation.ok();
        }

        public FormValidation doCheckSignPath(@QueryParameter String value) {
            return FormValidation.ok();
        }

        public FormValidation doCheckCompress(@QueryParameter String value) {
            return FormValidation.ok();
        }

        public FormValidation doCheckOldMapping(@QueryParameter String value) {
            if (value.length() > 0 && value.startsWith(File.separator)) {
                return FormValidation
                        .error("Please do not use file separator with the path starts!");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckWhiteList(@QueryParameter String value) {
            return FormValidation.ok();
        }

        public FormValidation doCheckKeepRoot(@QueryParameter boolean value) {
            return FormValidation.ok();
        }

        public FormValidation doCheckApkPath(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0) {
                return FormValidation.error("Please set the apk files path!");
            }
            if (!value.endsWith(".apk")) {
                return FormValidation
                        .error("Please use .apk with the path ends!");
            }
            if (value.startsWith(File.separator)) {
                return FormValidation
                        .error("Please do not use file separator with the path starts!");
            }
            return FormValidation.ok();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData)
                throws FormException {

            save();// 最后调用save
            return super.configure(req, formData);
        }

        @Override
        public String getDisplayName() {
            return "Android-ResGuard";
        }

    }

}
