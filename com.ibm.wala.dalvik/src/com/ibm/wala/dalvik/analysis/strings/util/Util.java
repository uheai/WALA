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
package com.ibm.wala.dalvik.analysis.strings.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.JarStreamModule;
import com.ibm.wala.dalvik.classLoader.DexFileModule;
import com.ibm.wala.dalvik.classLoader.DexIRFactory;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.config.FileOfClasses;

/**
 * utility class containing some methods
 * @author maik
 *
 */
public abstract class Util {

	private static DexIRFactory factory = new DexIRFactory();

	private static SSAOptions options = new SSAOptions();

	private final static String rt_Path = Util.class.getClassLoader().getResource("rt.jar").getPath();

	//private final static String rt_Path = "/opt/Oracle_Java/jdk1.8.0_77/jre/lib/rt.jar";

	//private final static InputStream exclusions = Util.class.getClassLoader().getResourceAsStream("exclusions.txt");

	private Util() {
		// avoid instantiation
	}

	/**
	 * creates analysis scope
	 * @param apk apk file
	 * @return created scope
	 * @throws IOException 
	 */
	public static AnalysisScope makeAndroidScope(File apk) throws IOException {
		AnalysisScope scope = AnalysisScope.createJavaAnalysisScope();
		scope.setLoaderImpl(ClassLoaderReference.Primordial, "com.ibm.wala.dalvik.classLoader.WDexClassLoaderImpl");
		scope.setLoaderImpl(ClassLoaderReference.Application, "com.ibm.wala.dalvik.classLoader.WDexClassLoaderImpl");
		scope.addToScope(ClassLoaderReference.Primordial, loadJDK());
		scope.addToScope(ClassLoaderReference.Primordial, new JarStreamModule(loadAndroidLib()));
		scope.addToScope(ClassLoaderReference.Application, DexFileModule.make(apk));

		// setting exclusions
		scope.setExclusions(new FileOfClasses(Util.class.getClassLoader().getResourceAsStream("exclusions.txt")));

		return scope;
	}

	private static JarFile loadJDK() throws IOException {
		return new JarFile(rt_Path);
	}

	private static JarInputStream loadAndroidLib() throws IOException {
		return new JarInputStream(Util.class.getClassLoader().getResourceAsStream("android.jar"));
	}

	public static IR makeDexIR(IMethod m) {
		return factory.makeIR(m, Everywhere.EVERYWHERE, new SSAOptions());
	}

	public static boolean hasRelevantType(IField field) {
		return hasRelevantType(field.getFieldTypeReference().getName());

	}

	/**
	 * is this is a StringBuilder, StringBuffer, String or an int
	 * @param name type
	 */
	public static boolean hasRelevantType(TypeName name) {
		if (name.equals(TypeReference.JavaLangString.getName())
				|| name.equals(TypeReference.JavaLangStringBuffer.getName())
				|| name.equals(TypeReference.JavaLangStringBuilder.getName()) || name.equals(TypeReference.IntName)) {
			return true;
		}
		return false;
	}

	/**
	 * is this a string, StringBuilder or StringBuffer
	 * @param name
	 */
	public static boolean isStringLikeType(TypeName name) {
		if (name.equals(TypeReference.JavaLangString.getName())
				|| name.equals(TypeReference.JavaLangStringBuffer.getName())
				|| name.equals(TypeReference.JavaLangStringBuilder.getName())) {
			return true;
		}
		return false;
	}

	/**
	 * returns string representation of a set, i.e. def = { v_1, v_2, ..., v_n }
	 * where v_i is String returned by v_i.toString()
	 * 
	 * @param def
	 *            name of set
	 * @param values
	 *            elements of set
	 * @return string representation of set
	 */
	public static String set2String(String def, Collection<?> values) {
		StringBuilder sb = new StringBuilder(def);
		sb.append(" = { ");
		if (values.isEmpty()) {
			return sb.append("}").toString();
		}
		Iterator<?> it = values.iterator();
		while (it.hasNext()) {
			String s = it.next().toString();
			sb.append(s + ", ");
		}
		sb.deleteCharAt(sb.length() - 2); // remove last ','
		sb.append("}");
		return sb.toString();
	}
	
	public static String set2String(Collection<?> values) {
		StringBuilder sb = new StringBuilder("{");
		Iterator<?> it = values.iterator();
		while (it.hasNext()) {
			String s = it.next().toString();
			sb.append(s + ",");
		}
		if (!values.isEmpty()) {
		sb.deleteCharAt(sb.length() - 1); // remove last ','
		}
		sb.append("}");
		return sb.toString();
	}

}
