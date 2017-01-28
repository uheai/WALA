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
package com.ibm.wala.dalvik.analysis.strings.variables;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.wala.dalvik.analysis.strings.util.Util;
import com.ibm.wala.util.collections.Pair;

/**
 * Represents a variable
 * 
 * @author maik
 *
 */
public abstract class FieldOrLocal {

	private Logger log = LoggerFactory.getLogger(FieldOrLocal.class);

	protected static final String ANYSTRING = "ANYSTRING";

	protected Collection<String> values;

	private Collection<FieldOrLocal> unresolvedVars;

	/**
	 * used for equations like this 'superset of' var1 . var2
	 */
	private Collection<Pair<FieldOrLocal, FieldOrLocal>> concats;

	/**
	 * contains variables which can be added arbitrarily often to the strings in
	 * values. If this set is non-empty the result ends up in
	 * 
	 * { v \in values } { l \in loop }*
	 */
	private Collection<Pair<FieldOrLocal, FieldOrLocal>> loops;

	private String name;

	protected boolean isAny;

	protected FieldOrLocal(String name) {
		unresolvedVars = new HashSet<FieldOrLocal>();
		values = new HashSet<String>();
		concats = new HashSet<Pair<FieldOrLocal, FieldOrLocal>>();
		this.name = name;
		isAny = false;
	}

	/**
	 * main algorithm to solve equations by traversing equation graph, i.e
	 * visiting children
	 * 
	 * @param seen
	 *            variables marked as seen
	 * @return true iff something changed
	 */
	private boolean resolveUnresolved(Set<FieldOrLocal> seen) {
		log.debug(toString());
		boolean globalChanged = false;
		boolean localChanged = false;
		// mark this variable as seen
		seen.add(this);
		if (isAny) {
			return false; // nothing to do
		}
		do {
			localChanged = false;
			for (FieldOrLocal var : unresolvedVars) {
				if (!seen.contains(var)) {
					// if var is marked as seen, we don't have to
					// resolve it again. With this we can avoid cycles
					var.resolveUnresolved(seen);
					// mark this variable as unseen again
					seen.remove(var);
				}
				if (var.isAny) {
					if (setAnyString()) {
						globalChanged = true;
						localChanged = false; // no more iterations needed
												// this variable is anystring
					}
					break; // finished
				}
				if (values.addAll(var.values)) {
					globalChanged = true;
					localChanged = true;
				}
			}
		} while (localChanged);
		return globalChanged;
	}

	/**
	 * solve equations for this variable
	 * 
	 * @see #resolveUnresolved(Set)
	 * @return true iff something changed, i.e. values were added.
	 */
	public boolean resolveUnresolved() {
		return resolveUnresolved(new HashSet<FieldOrLocal>());
	}

	/**
	 * solve equations this 'superset of' prefix . suffix
	 */
	public void resolveConcats() {
		// iterate throug every pair
		for (Pair<FieldOrLocal, FieldOrLocal> pair : concats) {
			FieldOrLocal prefix = pair.fst;
			FieldOrLocal suffix = pair.snd;
			if (prefix.isAny && suffix.isAny) {
				setAnyString();
				// continue;
			}
			// concat every prefix with every suffix
			/*
			 * for (String p : prefix.values) { for (String s : suffix.values) {
			 * if (values.add(p.concat(s))) ret = true; } }
			 */
			prefix.resolveUnresolved();
			prefix.resolveConcats();
			suffix.resolveUnresolved();
			suffix.resolveConcats();
			if (prefix.values.isEmpty()) {
				values.addAll(suffix.values);
			} else if (suffix.values.isEmpty()) {
				values.addAll(prefix.values);
			} else {
				StringBuilder regex = new StringBuilder();
				// prefix
				if (prefix.values.size() > 1) {
					regex.append(Util.set2String(prefix.values));
				} else {
					for (String p : prefix.values) {
						regex.append(p); // there is only one value
					}
				}
				// suffix
				if (suffix.values.size() > 1) {
					regex.append(Util.set2String(suffix.values));
				} else {
					for (String s : suffix.values) {
						regex.append(s); // there is only one value
					}
				}
				values.clear();
				values.add(regex.toString());
			}
		}
	}

	/**
	 * resolves loops, like { v \in values } { l \in loop }* Does nothing if
	 * {@link FieldOrLocal#loops} is null
	 * 
	 */
	public void resolveLoops() {
		if (loops == null || isAny) {
			return;
		}
		for (Pair<FieldOrLocal, FieldOrLocal> pair : loops) {
			FieldOrLocal prefix = pair.fst;
			FieldOrLocal suffix = pair.snd;
			prefix.resolveUnresolved();
			suffix.resolveUnresolved();
			StringBuilder regex = new StringBuilder();
			if (!prefix.values.isEmpty()) {
				regex.append(Util.set2String(prefix.values));
			}
			regex.append(Util.set2String(suffix.values));
			regex.append("*");
			values.clear();
			values.add(regex.toString());
		}
	}

	/**
	 * add equation this 'superset of' prefix. suffix*
	 * 
	 * @param prefix
	 *            prefix of regular expression
	 * @param suffix
	 *            suffix, which can be appended arbitrarily often
	 * @return False iff equation was already added
	 */
	public boolean addLoop(FieldOrLocal prefix, FieldOrLocal suffix) {
		if (loops == null) {
			loops = new HashSet<Pair<FieldOrLocal, FieldOrLocal>>();
		}
		return loops.add(Pair.make(prefix, suffix));
	}

	/**
	 * add (calculated) value, i.e. a result
	 * 
	 * @param value
	 *            result value
	 * @return true iff value set changed.
	 */
	public boolean addValue(String value) {
		if (!isAny) {
			return values.add(value);
		} else {
			return false;
		}
	}

	/**
	 * add collection of values
	 * 
	 * @see #addValue(String)
	 * @param values
	 *            valus to be added to resutl set
	 * @return true iff value set changed
	 */
	public boolean addValues(Collection<String> values) {
		// if variable is anystring there is nothing to do
		boolean ret = false;
		if (!isAny) {
			ret = this.values.addAll(values);
		}
		return ret;
	}

	/**
	 * for every variable of values add its string values to this variable
	 * 
	 * @param values
	 *            set of variables
	 * @return true iff result set changed.
	 */
	public boolean addVariables(Collection<FieldOrLocal> values) {
		boolean ret = false;
		if (!isAny) {
			for (FieldOrLocal var : values) {
				this.values.addAll(var.getValues());
			}
		}
		return ret;
	}

	/**
	 * add values of other to this variable
	 * 
	 * @param other
	 *            other variable
	 * @return true iff result set changed.
	 */
	public boolean addValues(FieldOrLocal other) {
		return this.addValues(other.values);
	}

	/**
	 * add equation this 'superset of' prefix . suffix
	 * 
	 * @param prefix
	 *            variable
	 * @param suffix
	 *            variable
	 * @return False iff equation was already added
	 */
	public boolean addConcat(FieldOrLocal prefix, FieldOrLocal suffix) {
		return concats.add(Pair.make(prefix, suffix));
	}

	/**
	 * add var to list of unresolved variables, i.e there is an statement this =
	 * var.
	 * 
	 * @param var
	 *            fieldvariable or local variable to add
	 * @return true iff something changed
	 */
	public boolean addUnresolvedVar(FieldOrLocal var) {
		assert (var != null) : "adding null variable";
		if (isAny) {
			return false;
		}
		if (unresolvedVars.contains(var)) {
			return false;
		} else {
			unresolvedVars.add(var);
			return true;
		}

	}

	/**
	 * add equation this 'superset of' ret, where ret is the return value of var
	 * 
	 * @param var
	 *            method
	 * @return False iff this equation was already added.
	 */
	public boolean addUnresolvedMethod(MethodVariable var) {
		// if method is unknown set this variable to anystring as we don't
		// what this method might return
		if (var.isUnknown()) {
			return setAnyString();
		}
		if (isAny) {
			return false;
		}
		/*
		 * if (unresolvedMethods.contains(var)) { return false; } else {
		 * unresolvedMethods.add(var); return true; }
		 */
		return addUnresolvedVar(var.getReturnValue());
	}

	/**
	 * set this variable anystring
	 * 
	 * @return False iff this variable was already anystring
	 */
	public boolean setAnyString() {
		if (isAny) {
			return false;
		}
		isAny = true;
		values.clear();
		values.add(ANYSTRING);
		return true;
	}

	/**
	 * @return Is this variable a field
	 */
	abstract public boolean isField();

	/**
	 * 
	 * @return Is this variable a local
	 */
	public boolean isLocal() {
		return !isField();
	}

	/**
	 * 
	 * @return Is this variable anystring
	 */
	public boolean isAnyString() {
		return isAny;
	}

	/**
	 * get all variables v for which there is an equation this 'superset of' v
	 * 
	 * @return set of variables
	 */
	public Collection<FieldOrLocal> getUnresolved() {
		return unresolvedVars;
	}

	/**
	 * @return calculated values
	 */
	public Collection<String> getValues() {
		return values;
	}

	/**
	 * 
	 * @return name of this variable
	 */
	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return Util.set2String(name, values);
	}

	/**
	 * All properties of this variable will be added to copy. The properties
	 * will be added as they are at the moment this method is called. So any
	 * changes of this variable after the call, will not affect copy.
	 * 
	 * @param copy
	 *            all properties of this variable will be added to copy
	 * @return deep copy of this variable
	 */
	public void applyCopy(FieldOrLocal copy) {
		if (isAny) {
			copy.setAnyString();
			return;// this variable is ANYSTRING, we do not have to copy every
					// equation
		}
		// copy values
		for (String s : values) {
			copy.addValue(s);
		}
		// copy unresolved variables
		for (FieldOrLocal var : unresolvedVars) {
			copy.addUnresolvedVar(var);
		}
		// copy concats
		for (Pair<FieldOrLocal, FieldOrLocal> p : concats) {
			copy.addConcat(p.fst, p.snd);
		}
		// copy loops
		if (loops != null) {
			for (Pair<FieldOrLocal, FieldOrLocal> p : loops) {
				copy.addLoop(p.fst, p.snd);
			}
		}
	}

	/**
	 * @return number of equations this variable has
	 */
	public int getNumberOfEquations() {
		if (isAny) {
			return 1;
		} else {
			return getNumberOfEquations(new HashSet<FieldOrLocal>(unresolvedVars.size()));
		}
	}

	/**
	 * returns number of equations this variable has with respect to equations
	 * already counted.
	 * 
	 * @param seen
	 *            variables we can ignore
	 * @return number of equations this variable has
	 */
	private int getNumberOfEquations(Set<FieldOrLocal> seen) {
		// mark this variable as seen
		seen.add(this);
		if (isAny) {
			return 1; // this = ANYSTRING is the only equation
		}
		int result = unresolvedVars.size(); // add number of equations
											// this variable has
		for (FieldOrLocal var : unresolvedVars) {
			if (!seen.contains(var)) {
				// if var is marked as seen, we ran into a loop
				// we do not have to count this equations again.
				result += var.getNumberOfEquations(seen);
			}
		}
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof FieldOrLocal) {
			FieldOrLocal var = (FieldOrLocal) obj;
			return (this.name.equals(var.name) && values.equals(var.values) && unresolvedVars.equals(var.unresolvedVars) && concats.equals(var.concats));
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		int factor = values.size() + unresolvedVars.size() + concats.size();
		if (factor == 0) {
			factor = 1;
		}
		return factor * 1337;
	}
}
