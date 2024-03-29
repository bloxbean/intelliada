/*
 * Copyright (c) 2021 BloxBean Project
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.bloxbean.intelliada.idea.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class IOUtil {
    private final static Logger logger = Logger.getInstance(IOUtil.class);

    public static Properties loadProperties(String propFile) {

        try (InputStream input = new FileInputStream(propFile)) {

            Properties prop = new Properties();

            // load a properties file
            prop.load(input);
            return prop;
        } catch (IOException ex) {
            logger.error("Error loading properties file : " + propFile, ex);
            return null;
        }
    }

    public static String getNameWithoutExtension(String file) {
        if (StringUtil.isEmpty(file))
            return file;

        int index = file.lastIndexOf(".");
        if (index == -1)
            return file;
        else {
            return file.substring(0, index);
        }
    }
}
