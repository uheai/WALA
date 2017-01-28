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

import com.ibm.wala.dalvik.util.AndroidTypes;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.strings.Atom;

/**
 * contains collection of method references and other values
 * @author maik
 *
 */
public abstract class MethodReferences {
	
	/**
	 * TypeReferences
	 */
	public static TypeReference Uri = TypeReference.findOrCreate(ClassLoaderReference.Primordial, "Landroid/net/Uri");

	/**
	 * Parameters
	 */
	public static TypeName[] StringPar = { TypeReference.JavaLangString.getName() };
	public static TypeName[] valueOfObject = { TypeReference.JavaLangObject.getName() };
	public static TypeName[] IntentPutExtraPar = { TypeReference.JavaLangString.getName(), AndroidTypes.BundleName };
	public static TypeName[] substringPar = { TypeReference.Int.getName() };
	public static TypeName[] IntentSetDataPar = { Uri.getName() };
	public static TypeName[] IntentSetData2Par = { Uri.getName(), TypeReference.JavaLangString.getName() };
	public static TypeName[] IntentSetClassNamePar = { TypeReference.JavaLangString.getName(), TypeReference.JavaLangString.getName() };
	public static TypeName[] IntentSetClassNamePar2 = { AndroidTypes.Context.getName(), TypeReference.JavaLangString.getName() };
	
	/**
	 * Method names
	 */
	public static Atom StrValueOf = Atom.findOrCreateAsciiAtom("valueOf");
	public static Atom IntentPutExtraAtom = Atom.findOrCreateAsciiAtom("putExtra");
	public static Atom ContextStartActivity = Atom.findOrCreateAsciiAtom("startActivity");
	public static Atom Uri_parseAtom = Atom.findOrCreateAsciiAtom("parse");
	public static Atom Str_substring = Atom.findOrCreateAsciiAtom("substring");
	public static Atom IntentSetActionAtom = Atom.findOrCreateAsciiAtom("setAction");
	public static Atom IntentSetDataAtom = Atom.findOrCreateAsciiAtom("setData");
	public static Atom IntentSetDataAndNormalizeAtom = Atom.findOrCreateAsciiAtom("setDataAndNormalize");
	public static Atom IntentSetDataAndTypeAtom = Atom.findOrCreateAsciiAtom("setDataAndType");
	public static Atom IntentSetDataAndTypeAndNormalizeAtom = Atom.findOrCreateAsciiAtom("setDataAndTypeAndNormalize");
	public static Atom IntentSetClassNameAtom = Atom.findOrCreateAsciiAtom("setClassName");

	/**
	 * Descriptors
	 */
	public static Descriptor appendDesc = Descriptor.findOrCreate(StringPar,
			TypeReference.JavaLangStringBuilder.getName());
	public static Descriptor toStringDesc = Descriptor.findOrCreate(new TypeName[0],
			TypeReference.JavaLangString.getName());
	public static Descriptor IntentPutExtraDesc = Descriptor.findOrCreate(IntentPutExtraPar, AndroidTypes.IntentName);
	public static Descriptor Uri_parseDesc = Descriptor.findOrCreate(StringPar, Uri.getName());
	public static Descriptor Str_substringDesc = Descriptor.findOrCreate(substringPar, TypeReference.JavaLangString.getName());
	public static Descriptor IntentSetActionDesc = Descriptor.findOrCreate(StringPar, AndroidTypes.IntentName);
	public static Descriptor IntentSetDataDesc = Descriptor.findOrCreate(IntentSetDataPar, AndroidTypes.IntentName);
	public static Descriptor IntentSetData2Desc = Descriptor.findOrCreate(IntentSetData2Par, AndroidTypes.IntentName);
	public static Descriptor IntentSetClassNameDesc = Descriptor.findOrCreate(IntentSetClassNamePar, AndroidTypes.IntentName);
	public static Descriptor IntentSetClassName2Desc = Descriptor.findOrCreate(IntentSetClassNamePar2, AndroidTypes.IntentName);

	/**
	 * MethodReferences
	 */
	public static MethodReference append = MethodReference.findOrCreate(TypeReference.JavaLangStringBuilder,
			Atom.findOrCreateAsciiAtom("append"), appendDesc);
	public static MethodReference StringBuilderToString = MethodReference
			.findOrCreate(TypeReference.JavaLangStringBuilder, Atom.findOrCreateAsciiAtom("toString"), toStringDesc);
	public static MethodReference StringBufferToString = MethodReference
			.findOrCreate(TypeReference.JavaLangStringBuffer, Atom.findOrCreateAsciiAtom("toString"), toStringDesc);
	public static MethodReference IntentPutExtra = MethodReference.findOrCreate(AndroidTypes.Intent, IntentPutExtraAtom, IntentPutExtraDesc);
	public static MethodReference Uri_parse = MethodReference.findOrCreate(Uri, Uri_parseAtom, Uri_parseDesc);
	public static MethodReference substring = MethodReference.findOrCreate(TypeReference.JavaLangString, Str_substring, Str_substringDesc);
	public static MethodReference IntentSetAction = MethodReference.findOrCreate(AndroidTypes.Intent, IntentSetActionAtom, IntentSetActionDesc);
	public static MethodReference IntentSetData = MethodReference.findOrCreate(AndroidTypes.Intent, IntentSetDataAtom, IntentSetDataDesc);
	public static MethodReference IntentSetDataAndNormalize = MethodReference.findOrCreate(AndroidTypes.Intent, IntentSetDataAndNormalizeAtom, IntentSetDataDesc);
	public static MethodReference IntentSetDataAndType = MethodReference.findOrCreate(AndroidTypes.Intent, IntentSetDataAndTypeAtom, IntentSetData2Desc);
	public static MethodReference IntentSetDataAndTypeAndNormalize = MethodReference.findOrCreate(AndroidTypes.Intent, IntentSetDataAndTypeAndNormalizeAtom, IntentSetData2Desc);
	public static MethodReference IntentSetClassName = MethodReference.findOrCreate(AndroidTypes.Intent, IntentSetClassNameAtom, IntentSetClassNameDesc);
	public static MethodReference IntentSetClassNameContextString = MethodReference.findOrCreate(AndroidTypes.Intent, IntentSetClassNameAtom, IntentSetClassName2Desc);
	
	/**
	 * returns method reference for toString method of given type
	 * @param ref type to get toString method of
	 * @return method reference of toString method
	 */
	public static MethodReference makeToStringRef(TypeReference ref) {
		return MethodReference.findOrCreate(ref, Atom.findOrCreateAsciiAtom("toString"), toStringDesc);
	}
}

