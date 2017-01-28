/*******************************************************************************
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html.
 *
 * This file is a derivative of code released under the terms listed below.  
 *
 * 	Copyright (c) 2017,
 *      Maik Wiesner
 * 	 All rights reserved.
 *
 * 	Redistribution and use in source and binary forms, with or without
 * 	modification, are permitted provided that the following conditions are met:
 *
 * 	1. Redistributions of source code must retain the above copyright notice,
 * 	this list of conditions and the following disclaimer.
 *  
 * 	2. Redistributions in binary form must reproduce the above copyright notice,
 * 	this list of conditions and the following disclaimer in the documentation
 * 	and/or other materials provided with the distribution.
 *
 * 	3. The names of the contributors may not be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *******************************************************************************/
package com.ibm.wala.dalvik.analysis.strings.main;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.wala.dalvik.analysis.strings.util.Util;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.CancelException;


/**
 * used to run analysis via command line with apk file as argument
 * @author maik
 *
 */
public class Standalone {

	private static Logger log = LoggerFactory.getLogger(Standalone.class);

	private static String helpMessage;

	static {
		// building helpMessage
		StringBuilder sb = new StringBuilder("Usage:\n\n");
		//sb.append(
		//		"Standalone path\t\t where path leads to a class file, a jar file or a folder, in which case all class files in this folder are "
		//				+ "added to a single scope, e.g the assumption is that all classes belong to one application\n");
		sb.append(
				"Standalone -apk path\t\t where path leads to an apk file or a folder, in which case every apk file in this folder will be "
						+ "analyzed by its own\n");
		sb.append("Standalone -h\t\t prints this message\n");
		sb.append("\nThe result of the analysis will be a log file in the logs folder");
		helpMessage = sb.toString();
	}

	public static void main(String[] args) throws IOException {
		// read input
		String current = args[0];
		if (current.startsWith("-")) {
			String param = current.substring(1); // remove '-'
			if (param.equals("apk")) {
				apk(args[1]);
			} else if (param.equals("h") || param.equals("help")) {
				System.out.println(helpMessage);
			} else {
				System.out.println(helpMessage);
			}
		}

	}

	private static void analyseApk(String path) {
		File file = new File(path);
		// run analysis
		try {
			AnalysisScope scope = Util.makeAndroidScope(file);
			ClassHierarchy cha = ClassHierarchy.make(scope);
			StringAnalysis analysis = new StringAnalysis(cha);
			long startTime = System.currentTimeMillis();
			analysis.runAnalysis();
			long finishTime = System.currentTimeMillis();
			analysis.solveHotspots(null, null);
			System.out.println(analysis.getSolvedIntents().toString());
			log.info("-----------------------\n\n");
			long runtime = finishTime - startTime;
			int min = (int) (runtime / 1000) / 60;
			int sec = (int) (runtime / 1000) % 60;
			log.info("Finished in " + min + ":" + sec + "min (" + runtime + "ms)");
		} catch (IOException e) {
			log.error("could not create scope");
			log.error(e.toString());
			e.printStackTrace();
		} catch (CancelException e) {
			log.error("error occured while running analysis");
			log.error(e.toString());
		} catch (ClassHierarchyException e) {
			log.error("could not create classhierarchy");
			log.error(e.toString());
		} catch (Throwable e) {
			log.error("some excepton occured\n\n" + e);
			e.printStackTrace();
		}

	}

	/**
	 * runs analysis 
	 * @param path folder containing apk files or single apk file
	 * @throws IOException
	 */
	private static void apk(String path) throws IOException {
		File file = new File(path);
		if (!file.exists()) {
			throw new IllegalArgumentException("file or directory does not exist");
		}
		if (file.isDirectory()) {
			File[] apks = file.listFiles(new FilenameFilter() {

				@Override
				public boolean accept(File dir, String name) {
					return name.toLowerCase().endsWith(".apk");
				}
			});
			
			
			for (File apk : apks) {
				analyseApk(apk.getAbsolutePath());
			}
		} else {
			if (!path.toLowerCase().endsWith(".apk")) {
				throw new IllegalArgumentException("This is not an apk file");
			}
			analyseApk(path);
		}
	}


}
