package org.eclipse.dash.m4e.eclipse.signing;

//========================================================================
//Copyright (c) 2010 Intalio, Inc.
//------------------------------------------------------------------------
//All rights reserved. This program and the accompanying materials
//are made available under the terms of the Eclipse Public License v1.0
//and Apache License v2.0 which accompanies this distribution.
//The Eclipse Public License is available at 
//http://www.eclipse.org/legal/epl-v10.html
//The Apache License v2.0 is available at
//http://www.opensource.org/licenses/apache2.0.php
//You may elect to redistribute this code under either of these licenses. 
//========================================================================


/**
 * 
 * @goal repack
 * @phase package
 * @description runs the eclipse packing process
 */
public class RepackMojo extends PackMojo
{
    /**
     * zip file to be packed
     * 
     * @parameter default-value= "${project.build.directory}/signed/site_assembly.zip"
     * @required
     */
    protected String inputFile;
    
}
