package com.wkl.plugin.util;

import java.io.File;
import java.io.FileFilter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Util {

    public static File[] searchFileWithPattern(File dir, String fileName) {
        final Pattern p = Pattern.compile(convetToPatternString(fileName));
        return dir.listFiles(new FileFilter() {

            @Override
            public boolean accept(File file) {
                if (file.isDirectory()) {
                    return false;
                }
                Matcher matcher = p.matcher(file.getName());
                return matcher.matches();
            }
        });
    }

    public static String convetToPatternString(String str) {
        String pattern;
        pattern = str.replace('.', '#');
        pattern = pattern.replaceAll("#", "\\\\.");
        pattern = pattern.replace('*', '#');
        pattern = pattern.replaceAll("#", ".*");
        pattern = pattern.replace('?', '#');
        pattern = pattern.replaceAll("#", ".?");
        pattern = "^" + pattern + "$";
        return pattern;
    }

}
