/*******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.viz;

import com.ibm.wala.util.WalaException;

/**
 * @param <T> the node type
 */
public interface NodeDecorator<T> {
  
  /**
   * @param n
   * @return the String label for node n
   */
  String getLabel(T n) throws WalaException;
  
  /**
   * Add additional parameters to change node. Line in dot-file
   * will look like 
   * <blockquote>
   * nameOfNode [label="...", this]
   * </blockquote>
   * Make sure it is still a proper dot line
   * @param n 
   * @return string containing parameters or null
   */
  String getAdditionalParameters(T n) throws WalaException;
  
}
