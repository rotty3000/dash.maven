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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.util.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * This plugin walks through a p2 repository and repairs the artifacts.xml.
 * 
 * @goal fixCheckSums
 * @phase package
 * @description updates the md5 checksum and file size
 */
public class ChecksumMojo extends AbstractEclipseSigningMojo
{
    /**
     * zip file to get checksum'ed
     * 
     * @parameter default-value="${project.build.directory}/packed/site_assembly.zip"
     * @required
     */
    protected String inputFile;

    /**
     * zip file to get checksum'ed
     * 
     * @parameter default-value="${project.build.directory}/fixed/site_assembly.zip"
     * @required
     */
    protected String outputFile;

    /**
     * zip file to get checksum'ed
     * 
     * @parameter default-value="${project.build.directory}/checksumFix"
     * @required
     */
    protected String unzipDir;

    /**
     * unpack path for artifact
     * 
     * @parameter default-value="/"
     * @required
     */
    protected String unpackPath;

    /**
     * checksum file
     * 
     * @parameter default-value="${project.build.directory}/checksumFix/artifacts.xml
     * @required
     */
    protected String artifactsXml;

    public void execute() throws MojoExecutionException, MojoFailureException
    {
        try
        {

            if (!FileUtils.fileExists(unzipDir))
            {
                info("creating directory to hold unzipped artifact");
                FileUtils.mkdir(unzipDir);
            }

            if (!FileUtils.fileExists(FileUtils.dirname(outputFile)))
            {
                info("creating directory to hold output");
                FileUtils.mkdir(FileUtils.dirname(outputFile));
            }

            ZipUnArchiver unzipper = new ZipUnArchiver(new File(inputFile));
            unzipper.extract("",new File(unzipDir));

            ZipUnArchiver unzipper2 = new ZipUnArchiver(new File(unzipDir + "/artifacts.jar"));
            unzipper2.extract("",new File(unzipDir));

            FileUtils.fileDelete(unzipDir + "/artifacts.jar.pack.gz");
            FileUtils.fileDelete(unzipDir + "/content.jar.pack.gz");

            List files = FileUtils.getFileNames(new File(unzipDir),"**/*.jar","",true);

            for (Iterator i = files.iterator(); i.hasNext();)
            {
                String filename = (String)i.next();
                info("Processing: " + filename);
                if (filename.endsWith("artifacts.jar"))
                {
                    continue;
                }
                else if (filename.endsWith("content.jar"))
                {
                    continue;
                }
                else
                {
                    update(filename);
                }
            }

            FileUtils.fileDelete(unzipDir + "/artifacts.jar");

            ZipArchiver zipper = new ZipArchiver();
            zipper.addFile(new File(unzipDir + "/artifacts.xml"),"artifacts.xml");
            // zipper.addDirectory(new File(unzipDir));
            zipper.setDestFile(new File(unzipDir + "/artifacts.jar"));
            zipper.createArchive();

            FileUtils.fileDelete(unzipDir + "/artifacts.xml");
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }

    private void update(String plugin)
    {
        try
        {
            /*
             * some people have features the same as plugins features so we need to tweak the xpath
             */
            boolean isPlugin = plugin.contains("/plugins/");
            
            String pluginFile = FileUtils.basename(plugin,".jar");

            int lastUnderscore = pluginFile.lastIndexOf('_');
            
            String[] artifactBits = new String[2];

            artifactBits[0] = pluginFile.substring(0,lastUnderscore);
            artifactBits[1] = pluginFile.substring(lastUnderscore + 1, pluginFile.length());
            
            getLog().info(" Artifact: " + artifactBits[0] + " / Version: " + artifactBits[1] + " / isPlugin: " + isPlugin );

            File inFile = new File(plugin);
            String inFileSize = Long.toString(inFile.length());

            int count = 0;
            byte[] buffer = new byte[1024 * 16];
            InputStream in = new FileInputStream(inFile);
            MessageDigest md = MessageDigest.getInstance("MD5");

            while ((count = in.read(buffer)) > 0)
            {
                md.update(buffer,0,count);
            }
            in.close();

            String inFileMD5 = convert(md.digest());// (new BigInteger(1, md.digest())).toString(16);

            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = builder.parse(new File(artifactsXml));

            String expr;
            XPath xpath = XPathFactory.newInstance().newXPath();

            if ( isPlugin )
            {
                expr = "/repository/artifacts//artifact[@id='" + artifactBits[0] + "' and @version='" + artifactBits[1] + "' and @classifier='osgi.bundle']/properties//property[@name='artifact.size']/@value";
            }
            else
            {
                expr = "/repository/artifacts//artifact[@id='" + artifactBits[0] + "' and @version='" + artifactBits[1] + "' and @classifier='org.eclipse.update.feature']/properties//property[@name='artifact.size']/@value";
            }
            Node artifactSize = (Node)xpath.evaluate(expr,document,XPathConstants.NODE);
            if (artifactSize != null)
            {
                artifactSize.setNodeValue(inFileSize);
            }

            if (isPlugin)
            {
                expr = "/repository/artifacts//artifact[@id='" + artifactBits[0] + "' and @version='" + artifactBits[1] + "' and @classifier='osgi.bundle']/properties//property[@name='download.size']/@value";
            }
            else
            {
                expr = "/repository/artifacts//artifact[@id='" + artifactBits[0] + "' and @version='" + artifactBits[1] + "' and @classifier='org.eclipse.update.feature']/properties//property[@name='download.size']/@value";   
            }
            
            Node downloadSize = (Node)xpath.evaluate(expr,document,XPathConstants.NODE);
            if (downloadSize != null)
            {
                downloadSize.setNodeValue(inFileSize);
            }

            if ( isPlugin )
            {
                expr = "/repository/artifacts//artifact[@id='" + artifactBits[0] + "' and @version='" + artifactBits[1] + "' and @classifier='osgi.bundle']/properties//property[@name='download.md5']/@value";
            }
            else
            {
                expr = "/repository/artifacts//artifact[@id='" + artifactBits[0] + "' and @version='" + artifactBits[1] + "' and @classifier='org.eclipse.update.feature']/properties//property[@name='download.md5']/@value";
            }
            Node downloadMD5 = (Node)xpath.evaluate(expr,document,XPathConstants.NODE);
            if (downloadMD5 != null)
            {
                downloadMD5.setNodeValue(inFileMD5);
            }

            if (FileUtils.fileExists(plugin + ".pack.gz"))
            {
                File packFile = new File(plugin + ".pack.gz");
                String packFileSize = Long.toString(packFile.length());

                expr = "/repository/artifacts";// //artifact[@id='" + artifactBits[0] + "' and @version='" + artifactBits[1] + "']";
                Node normal = (Node)xpath.evaluate(expr,document,XPathConstants.NODE);
                Element packed = document.createElement("artifact");

                packed.setAttribute("classifier","osgi.bundle");
                packed.setAttribute("id",artifactBits[0]);
                packed.setAttribute("version",artifactBits[1]);

                Element processing = document.createElement("processing");
                processing.setAttribute("size","1");
                packed.appendChild(processing);

                Element step = document.createElement("step");
                step.setAttribute("id","org.eclipse.equinox.p2.processing.Pack200Unpacker");
                step.setAttribute("required","true");
                processing.appendChild(step);

                Element properties = document.createElement("properties");
                properties.setAttribute("size","3");
                packed.appendChild(properties);

                Element prop1 = document.createElement("property");
                prop1.setAttribute("name","artifact.size");
                prop1.setAttribute("value",inFileSize);
                properties.appendChild(prop1);

                Element prop2 = document.createElement("property");
                prop2.setAttribute("name","download.size");
                prop2.setAttribute("value",packFileSize);
                properties.appendChild(prop2);

                Element prop3 = document.createElement("property");
                prop3.setAttribute("name","format");
                prop3.setAttribute("value","packed");
                properties.appendChild(prop3);

                normal.appendChild(packed);

            }

            Transformer txformer = TransformerFactory.newInstance().newTransformer();
            DOMSource src = new DOMSource(document);
            StreamResult res = new StreamResult(artifactsXml);
            txformer.transform(src,res);
        }
        catch (Exception e)
        {
            getLog().error(e);
        }
    }

    private String convert(byte[] bytes)
    {
        StringBuffer sb = new StringBuffer();

        for (int i = 0; i < bytes.length; ++i)
        {
            sb.append(Character.forDigit((bytes[i] >> 4) & 0xf,16));
            sb.append(Character.forDigit(bytes[i] & 0xf,16));
        }
        return sb.toString();
    }

    private void info(String log)
    {
        getLog().info("[FIX] " + log);
    }

}
