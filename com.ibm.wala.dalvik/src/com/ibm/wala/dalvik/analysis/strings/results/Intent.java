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
package com.ibm.wala.dalvik.analysis.strings.results;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.dalvik.analysis.strings.util.IntentType;
import com.ibm.wala.dalvik.analysis.strings.variables.IntentVar;
import com.ibm.wala.dalvik.util.AndroidTypes;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.strings.Atom;


//Facade for IntentVar, makes sure user can not call any public method

/**
 * Solved Intent variable
 * 
 * @author maik
 *
 */
public class Intent {

	private IntentVar var;

	private Variable action;
	private Variable data;
	private Variable clazz;
	private Variable extra;

	private IntentType type;

	/**
	 * creates facade for intent variable
	 * 
	 * @param var
	 *            used variable
	 */
	public Intent(IntentVar var) {
		this.var = var;
	}

	/**
	 * get action component of this intent
	 * 
	 * @return action
	 */
	public Variable getAction() {
		if (action == null) {
			action = new Variable(var.getAction());
		}
		return action;
	}

	/**
	 * get data component of this intent
	 * 
	 * @return data
	 */
	public Variable getData() {
		if (data == null) {
			data = new Variable(var.getData());
		}
		return data;
	}

	/**
	 * get extra component of this intent
	 * 
	 * @return
	 */
	public Variable getExtra() {
		if (extra == null) {
			extra = new Variable(var.getExtra());
		}
		return extra;
	}

	/**
	 * get class component of this intent
	 * 
	 * @return
	 */
	public Variable getClassVar() {
		if (clazz == null) {
			clazz = new Variable(var.getClassVar());
		}
		return clazz;
	}

	/**
	 * Checks whether this intent is used in given instruction at position pos
	 * 
	 * @param inst
	 *            instruction using intent
	 * @param pos
	 *            number of use
	 * @return true if this intent is used by inst
	 */
	public boolean isUsed(SSAInstruction inst, int pos) {
		SSAInstruction def = var.getDefiningInstruction();
		if (!def.hasDef()) {
			return false;
			//TODO: defining instruction of intent was set wrong, take care of it some day
		}
		return (inst.getUse(pos) == def.getDef());
	}

	/**
	 * Determines whether startActivity(intent) was called on this activity
	 * 
	 * @return true iff startActivity(intent) was called
	 */
	public boolean startActivityCalled() {
		return var.startActivityCalled();
	}

	// --------------
	// Methods used by ContextInterpreter
	// --------------

	public IntentType getType(IClassHierarchy cha) {
		if (type != null) {
			return type;
		}
		isInternal(cha);
		if (type == null) {
			isExternal(cha);
		}
		if (type == null) {
			isStandardAction(cha);
		}
		if (type == null) {
			isSystemService();
		}
		if (type == null) {
			type = IntentType.UNKNOWN_TARGET;
		}

		return type;
	}

	private boolean isSystemService() {
		boolean ret = false;

		if (getAction().isAny()) {
			return false;
		}

		for (String value : getAction().getValues()) {
			Atom atom = Atom.findOrCreateAsciiAtom(value);
			// TODO: check whether this is right
			ret |= (atom.getVal(0) != 'L') && (atom.rIndex((byte) '/') < 0) && (atom.rIndex((byte) '.') < 0);
			if (!ret) {
				break;
			}
		}

		if (ret) {
			type = IntentType.SYSTEM_SERVICE;
		}

		return ret;
	}

	private boolean isInternal(IClassHierarchy cha) {
		boolean ret = false;
		Variable classVar = getClassVar();
		// if there is no class specified, the target could be anywhere
		if (classVar.getValues().isEmpty() || classVar.isAny()) {
			// type = IntentType.UNKNOWN_TARGET;
			return false;
		}

		for (String value : classVar.getValues()) {
			TypeReference ref = TypeReference.findOrCreate(ClassLoaderReference.Application, value);
			IClass cls = cha.lookupClass(ref);
			ret |= (cls != null); // every possible class has to exist in
									// class-hierarchy in order to say that
									// the target is internal

			if (!ret) {
				break;
			}

		}

		if (ret) {
			type = IntentType.INTERNAL_TARGET;
		}

		return ret;
	}

	private boolean isExternal(IClassHierarchy cha) {
		boolean ret = false;
		Variable classVar = getClassVar();
		// if there is no class specified, the target could be anywhere
		if (classVar.getValues().isEmpty() || classVar.isAny()) {
			// type = IntentType.UNKNOWN_TARGET;
			return false;
		}
		for (String value : classVar.getValues()) {
			TypeReference ref = TypeReference.find(ClassLoaderReference.Application, value);
			IClass cls = cha.lookupClass(ref);
			ret |= (cls == null); // every possible class has to be not in
									// class-hierarchy in order to say that
									// the target is external

			if (!ret) {
				break;
			}
		}

		if (ret) {
			type = IntentType.EXTERNAL_TARGET;
		}

		return ret;
	}

	private boolean isStandardAction(IClassHierarchy cha) {
		IClass intentClass = cha.lookupClass(AndroidTypes.Intent);
		// System.out.println(intentClass);
		for (String ac : var.getAction().getValues()) {
			// TODO: handle Kleene
			// we need to format the string
			String stdAc = ac; // All standard actions are like
								// ACTION_DO_SOMETHING
			if (ac.startsWith("android.intent")) {
				stdAc = ac.replaceFirst("android\\.intent\\.", "").toUpperCase().replaceAll("\\.", "_");
			}
			IField field = intentClass.getField(Atom.findOrCreateAsciiAtom(stdAc));
			// System.out.println(field + " ac: " + stdAc);
			if (field != null) {
				type = IntentType.STANDARD_ACTION;
				return true;
			}
		}
		return false;
	}

	// --------------
	// String methods
	// --------------

	/**
	 * 
	 * @return string representation of action component
	 */
	public String getActionString() {
		return var.getAction().toString();
	}

	/**
	 * 
	 * @return string representation of data component
	 */
	public String getDataString() {
		return var.getData().toString();
	}

	public String getExtraString() {
		return var.getExtra().toString();
	}

	/**
	 * @return string representation of class component
	 */
	public String getClassString() {
		return var.getClassVar().toString();
	}

	@Override
	public String toString() {
		return var.toString();
	}

	// ------------------
	// euqals / hashCode
	// ------------------

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!this.getClass().equals(obj.getClass())) {
			return false;
		}
		Intent other = (Intent) obj;
		return this.var.equals(other.var);
	}
	
	@Override
	public int hashCode() {
		return var.hashCode() * 1337;
	}

}
