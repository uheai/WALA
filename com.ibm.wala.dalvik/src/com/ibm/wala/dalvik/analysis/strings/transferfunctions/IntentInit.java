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
package com.ibm.wala.dalvik.analysis.strings.transferfunctions;

import com.ibm.wala.dalvik.analysis.strings.util.Type;
import com.ibm.wala.dalvik.analysis.strings.variables.FieldOrLocal;
import com.ibm.wala.dalvik.analysis.strings.variables.IntentVar;
import com.ibm.wala.dalvik.analysis.strings.variables.Local;
import com.ibm.wala.dalvik.analysis.strings.variables.NodeVariable;
import com.ibm.wala.dalvik.util.AndroidTypes;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Pair;



/**
 * constructor call of an intent
 * @author maik
 *
 */
public class IntentInit extends Invoke {

	private int vn;

	/**
	 * method where intent is created
	 */
	private MethodReference ref;

	private DefUse defUse;
	
	public IntentInit(SSAInvokeInstruction inst, IR ir, AnalysisCache cache) {
		super(inst, ir, cache);
		vn = inst.getUse(0);
		ref = ir.getMethod().getReference();
		this.defUse = cache.getDefUse(ir);
	}

	@Override
	public byte evaluate(NodeVariable lhs, NodeVariable rhs) {
		byte state = NOT_CHANGED_AND_FIXED;
		if (params == null) {
			lhs.getOrCreateIntent(inst, "empty", vn);
		} else if (params.length == 1
				&& inst.getDeclaredTarget().getParameterType(0).getName().equals(AndroidTypes.IntentName)) {
			// this is a copy of another intent
			state = intentCopy(lhs);
		} else if (params.length < 3 && inst.getDeclaredTarget().getParameterType(0).getName()
				.equals(TypeReference.JavaLangString.getName())) {
			// handles Intent(String action) and Intent(String action, Uri uri).
			// We interpret uri's as strings
			state = intentString(lhs);
		} else if (params.length == 2) {
			// Intent(Context packageContext, Class<?> cls)
			state = intentClass(lhs);
		} else {
			// Intent(String action, Uri uri, Context packageContext, Class<?>
			// cls)
			FieldOrLocal action = handleString(lhs, params[0], types[0]);
			FieldOrLocal data = handleString(lhs, params[1], types[1]);
			IntentVar intent = lhs.getOrCreateIntent(inst, "all", vn);
			boolean b3 = handleClassVar(params[3], types[3], intent.getClassVar());
			boolean b1 = lhs.addVar(intent.getAction(), action);
			boolean b2 = lhs.addVar(intent.getData(), data);
			if (b1 || b2 || b3) {
				state = CHANGED_AND_FIXED;
			}
		}

		return state;
	}

	/**
	 * i = new Intent(Intent other)
	 * @param lhs node variable
	 * @return state
	 */
	private byte intentCopy(NodeVariable lhs) {
		IntentVar newCopy = lhs.getOrCreateIntent(inst, "copy", vn);
		IntentVar param = null;
		switch (types[0]) {
		case METHOD:
			SSAAbstractInvokeInstruction inst = (SSAAbstractInvokeInstruction) params[0];
			// the invoked method returns an intent
			FieldOrLocal retVar = lhs.getReturnValue(inst.getDeclaredTarget(), inst.getDef());
			newCopy.setInit(retVar);
			return CHANGED_AND_FIXED;
		case PARAM:
			Pair<MethodReference, Integer> pair = (Pair<MethodReference, Integer>) params[0];
			Local parVar = lhs.getParameterVar(pair.fst, pair.snd);
			newCopy.setInit(parVar);
			return CHANGED_AND_FIXED;
		case OTHER:
				param = lhs.getOrCreateIntent((Integer) params[0], super.inst);
				// we cannot call newCopy.setInit(param) because param is not
				// a placeholder like in METHOD and PARAM cases.
				// we have to add the variables manually
				lhs.addVar(newCopy.getAction(), param.getAction());
				lhs.addVar(newCopy.getData(), param.getData());
				lhs.addVar(newCopy.getExtra(), param.getExtra());
				lhs.addVar(newCopy.getClassVar(), param.getClassVar());
			
			return CHANGED_AND_FIXED;
		case ANY:
		case CONST:
		case FIELD:
		default:
			break;
		}
		return NOT_CHANGED_AND_FIXED;
	}

	/**
	 * i = new Intent(Strin action)
	 * @param lhs node variable 
	 * @return state
	 */
	private byte intentString(NodeVariable lhs) {
		IntentVar intent = lhs.getOrCreateIntent(inst, "string", vn);
		boolean changed = false;
		FieldOrLocal action = handleString(lhs, params[0], types[0]);
		changed = lhs.addVar(intent.getAction(), action);
		if (params.length == 2) {
			// if there is a second parameter, it is a URI, we handle it
			// like a string
			FieldOrLocal data = handleString(lhs, params[1], types[1]);
			changed |= lhs.addVar(intent.getData(), data);
		}
		if (changed) {
			return CHANGED_AND_FIXED;
		} else {
			return NOT_CHANGED_AND_FIXED;
		}
	}

	/**
	 * i = new Intent(Context, class), ignoring context
	 * @param lhs node variable
	 * @return state
	 */
	private byte intentClass(NodeVariable lhs) {
		IntentVar intent = lhs.getOrCreateIntent(inst, "class", vn);
		// ignore first parameter (context)
		// we can only take care of class files specified as constants,
		// like in Intent i = new Intent(..., SomeClass.class).
		// This is only the case if type is OTHER (not CONST, this is only for
		// String constants).
		if (types[1] == Type.OTHER) {
			Local cls = getClassLocal((Integer) params[1]);
			intent.addClassVar(cls);
			return CHANGED_AND_FIXED;
		} else {
			// we have to set class component to anystring
			intent.getClassVar().setAnyString();
			return CHANGED_AND_FIXED;
		}
	}

	/**
	 * Adds specified class file (see comment in
	 * {@link #intentClass(NodeVariable)} to class variable or sets it to
	 * anystring.
	 * 
	 * @param obj
	 *            contains class file
	 * @param type
	 *            type of object
	 * @param classVar
	 *            class component of intent
	 * @return true if class file was successfully added, false if class
	 *         component is set to anystring.
	 */
	private boolean handleClassVar(Object obj, Type type, FieldOrLocal classVar) {
		if (type == Type.OTHER) {
			FieldOrLocal cls = getClassLocal((Integer) obj);
			return classVar.addUnresolvedVar(cls);
		} else {
			return classVar.setAnyString();
		}
	}

	/**
	 * handle string parameter
	 * @param lhs node variable
	 * @param obj object containing variable
	 * @param type type of parameter
	 * @return state
	 */
	private FieldOrLocal handleString(NodeVariable lhs, Object obj, Type type) {
		FieldOrLocal param = null;
		switch (type) {
		case CONST:
			param = (Local) obj;
			break;
		case METHOD:
			SSAAbstractInvokeInstruction inst = (SSAAbstractInvokeInstruction) obj;
			param = lhs.getReturnValue(inst.getDeclaredTarget(), inst.getDef());
			break;
		case PARAM:
			Pair<MethodReference, Integer> pair = (Pair<MethodReference, Integer>) obj;
			param = lhs.getParameterVar(pair.fst, pair.snd);
			break;
		case OTHER:
			param = lhs.getOrCreateVar((Integer) obj);
			break;
		case ANY:
		case FIELD:
		default:
			break;
		}
		return param;
	}

	/**
	 * If vn defines a {@link SSALoadMetadataInstruction} this method returns a
	 * local variable containing the class file as a string constant. Otherwise
	 * a local variable containing ANYSTRING will be returned.
	 * 
	 * @param vn
	 *            def of an {@link SSALoadMetadataInstruction}
	 * @return local variable (const) containing class file as string or
	 *         anystring
	 */
	private Local getClassLocal(int vn) {
		Local local = new Local("const: class");
		SSAInstruction instruction = defUse.getDef(vn);
		if (instruction instanceof SSALoadMetadataInstruction) {
			SSALoadMetadataInstruction inst = (SSALoadMetadataInstruction) instruction;
			TypeName cls = ((TypeReference) inst.getToken()).getName();
			
			//for debug purposes
			assert (cls != null);
	
			String className = cls.toString();
			local.addValue(className);
		} else {
			local.setAnyString();
		}
		return local;
	}

	@Override
	public int hashCode() {
		return 1337 * vn;
	}

	@Override
	public boolean equals(Object o) {
		return this == o;
	}

	@Override
	public String toString() {
		return "Intent-Init: " + vn;
	}

}
